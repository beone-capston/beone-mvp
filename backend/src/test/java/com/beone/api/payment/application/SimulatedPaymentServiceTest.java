package com.beone.api.payment.application;

import static com.beone.api.payment.application.PaymentFixtures.auto;
import static com.beone.api.payment.application.PaymentFixtures.compared;
import static com.beone.api.payment.application.PaymentFixtures.selected;
import static com.beone.api.payment.application.PaymentFixtures.service;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.beone.api.benefit.rule.loading.RuleCatalogLoader;
import com.beone.api.card.application.ConfirmationMethod;
import com.beone.api.card.application.HoldingSource;
import com.beone.api.card.application.MappingStatus;
import com.beone.api.card.application.RecommendationEligibility;
import com.beone.api.card.application.UserCardMapping;
import com.beone.api.card.application.UserCardService;
import com.beone.api.card.application.UserCardView;
import com.beone.api.ledger.application.AttributionStatus;
import com.beone.api.ledger.application.LedgerEntryType;
import com.beone.api.ledger.application.LedgerUsageEffect;
import com.beone.api.ledger.application.LedgerWriter;
import com.beone.api.ledger.application.NewLedgerEntry;
import com.beone.api.monthlystate.application.MonthlyStateService;
import com.beone.api.monthlystate.application.MonthlyStateView;
import com.beone.api.monthlystate.application.ServiceSelectionStatus;
import com.beone.api.monthlystate.application.StateValue;
import com.beone.api.order.application.OrderDetail;
import com.beone.api.order.application.OrderService;
import com.beone.api.order.application.OrderStatus;
import com.beone.api.order.application.PaymentMethod;
import com.beone.api.payment.persistence.PaymentCancellationEntity;
import com.beone.api.payment.persistence.PaymentCancellationRepository;
import com.beone.api.payment.persistence.SimulatedPaymentEntity;
import com.beone.api.payment.persistence.SimulatedPaymentRepository;
import com.beone.api.recommendation.application.RecommendationCalculation;
import com.beone.api.recommendation.application.RecommendationDocument;
import com.beone.api.recommendation.application.RecommendationService;
import com.beone.api.user.application.CurrentUser;
import com.beone.api.user.application.DemoUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

/**
 * 모의 승인·취소 흐름(docs/api/mock-payments.md, D-17·D-23-4). 저장소·다른 패키지 부품은 대역이고 규칙은 실제 4종 파일을 읽는다.
 *
 * <p>기대값은 계약 예시에서 썼다: 주문 {@code ord-5001} 스타벅스 10,000원, 승인 {@code pay-7001}·원장 {@code lt-9001}, 4,000원 부분취소
 * {@code cn-8001}·{@code lt-9002} 뒤 남은 6,000원, 09174(귀속 미확정)는 실적·혜택·한도 모두 확인 필요. 09271은 귀속 규칙이
 * {@code RECEIPT_MONTH}다(규칙 파일 E13).
 */
class SimulatedPaymentServiceTest {

	private static final CurrentUser ME = new CurrentUser(1L, DemoUser.MY_CARDS);

	private static final LocalDateTime CREATED = LocalDateTime.of(2026, 11, 3, 10, 0, 0);

	// 2026-11-03T10:42:00+09:00
	private static final Instant APPROVED = Instant.parse("2026-11-03T01:42:00Z");

	private static final OrderDetail ORDER = new OrderDetail("ord-5001", 10000, LocalDate.of(2026, 11, 3),
			LocalTime.of(10, 40), "스타벅스 송도점", "STARBUCKS_STORE", PaymentMethod.DIRECT, OrderStatus.CREATED, CREATED);

	private final SimulatedPaymentRepository payments = mock(SimulatedPaymentRepository.class);

	private final PaymentCancellationRepository cancellations = mock(PaymentCancellationRepository.class);

	private final IdempotencyStore idempotency = mock(IdempotencyStore.class);

	private final OrderService orders = mock(OrderService.class);

	private final RecommendationService recommendations = mock(RecommendationService.class);

	private final UserCardService cards = mock(UserCardService.class);

	private final MonthlyStateService states = mock(MonthlyStateService.class);

	private final LedgerWriter ledger = mock(LedgerWriter.class);

	private final AtomicReference<Instant> now = new AtomicReference<>(APPROVED);

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	private SimulatedPaymentService service;

	private SimulatedPaymentEntity saved;

	@BeforeEach
	void setUp() {
		Clock clock = new MovableClock(ZoneOffset.UTC);
		this.service = new SimulatedPaymentService(this.payments, this.cancellations, this.idempotency, this.orders,
				this.recommendations, this.cards, this.states, this.ledger, RuleCatalogLoader.load(), this.jsonMapper,
				clock);
		given(this.orders.lockForPayment(ME, "ord-5001")).willReturn(ORDER);
		given(this.orders.get(ME, "ord-5001")).willReturn(ORDER);
		given(this.idempotency.replay(eq(1L), anyString(), anyString(), any())).willReturn(Optional.empty());
		given(this.payments.saveAndFlush(any())).willAnswer(invocation -> {
			SimulatedPaymentEntity entity = invocation.getArgument(0);
			ReflectionTestUtils.setField(entity, "simulatedPaymentId", 7001L);
			this.saved = entity;
			return entity;
		});
		given(this.payments.findForUpdate(7001L, 1L)).willAnswer(invocation -> Optional.ofNullable(this.saved));
		given(this.ledger.recordApproval(any(), any())).willReturn(9001L);
		given(this.ledger.recordCancellation(any())).willReturn(9002L);
		given(this.cancellations.save(any())).willAnswer(invocation -> {
			PaymentCancellationEntity entity = invocation.getArgument(0);
			ReflectionTestUtils.setField(entity, "cancellationId", 8001L);
			return entity;
		});
		given(this.cards.list(ME)).willReturn(List.of(card("uc-1001", "09174"), card("uc-1003", "01664")));
	}

	@Test
	void approvesTheRecalculatedSelectionAndRecordsTheLedgerOnce() {
		recalculatesTo("09174", 2100L, List.of(new RecommendationCalculation.BucketDeduction("uc-1001", "GREAT", 2100L),
				new RecommendationCalculation.BucketDeduction("uc-1003", "COMMON", 1200L)));

		ApprovalDocument document = this.service.approve(ME, "ord-5001", new ApprovalCommand(null, false), "key-1");

		assertThat(document).isEqualTo(new ApprovalDocument("pay-7001", "ord-5001", "APPROVED",
				"2026-11-03T10:42:00+09:00", 10000, "AUTO_RECOMMEND", "uc-1001", "09174", true,
				new ApprovalDocument.Amount(2100L, "CONFIRMED"),
				new ApprovalDocument.AppliedRule("09174-v1", "VERIFIED",
						List.of(new ApprovalDocument.OfficialSource(PaymentFixtures.SOURCE_URI, "p.1"))),
				new ApprovalDocument.SelectionBasis("MONTHLY_TOTAL", false, List.of()),
				"사용자 확인 매핑, 공식 SKU·약관 버전 미검증",
				new ApprovalDocument.Recalculation("rec-3002", null, null, List.of()), "lt-9001", 10000));
		ArgumentCaptor<NewLedgerEntry> entry = ArgumentCaptor.forClass(NewLedgerEntry.class);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<LedgerUsageEffect>> effects = ArgumentCaptor.forClass(List.class);
		verify(this.ledger).recordApproval(entry.capture(), effects.capture());
		assertThat(entry.getValue()).isEqualTo(new NewLedgerEntry(1L, 1001L, LedgerEntryType.APPROVAL, 10000,
				LocalDate.of(2026, 11, 3), LocalTime.of(10, 40), "STARBUCKS_STORE", "DIRECT", "스타벅스 송도점", null, 7001L,
				YearMonth.of(2026, 11), AttributionStatus.CONFIRMED, LocalDateTime.of(2026, 11, 3, 10, 42, 0)));
		// 승인 카드의 차감액만 남긴다
		assertThat(effects.getValue()).containsExactly(new LedgerUsageEffect("GREAT", 2100L));
		verify(this.orders).markPaid(ME, "ord-5001", "pay-7001");
		verify(this.idempotency).remember(eq(1L), eq("key-1"), anyString(), eq(this.saved.getDocumentJson()), any());
		assertThat(this.jsonMapper.readValue(this.saved.getDocumentJson(), ApprovalDocument.class)).isEqualTo(document);
	}

	@Test
	void approvalLocksTheOrderThenAllHeldCardsBeforeAnyPlainReadOrRecalculation() {
		// #122: 같은 카드의 다른 주문 동시 승인 경합을 막는 잠금 순서. 주문 행 → 보유 카드 행 → 멱등 키 조회(첫 일반 읽기) → 재계산
		recalculatesTo("09174", 2100L, List.of(new RecommendationCalculation.BucketDeduction("uc-1001", "GREAT", 2100L)));

		this.service.approve(ME, "ord-5001", new ApprovalCommand(null, false), "key-1");

		InOrder order = inOrder(this.orders, this.cards, this.idempotency, this.recommendations);
		order.verify(this.orders).lockForPayment(ME, "ord-5001");
		order.verify(this.cards).lockHeldCardsForPayment(ME);
		order.verify(this.idempotency).replay(eq(1L), eq("key-1"), anyString(), any());
		order.verify(this.recommendations).calculateForApproval(ME, "ord-5001");
	}

	@Test
	void cancellationDoesNotLockHeldCards() {
		approveWith("09174", 2100L, "GREAT");

		this.service.cancel(ME, "pay-7001", 4000, "key-c");

		// 승인 한 번만 잠근다. 취소는 다른 결제의 원장을 읽어 계산하지 않는다
		verify(this.cards, times(1)).lockHeldCardsForPayment(ME);
	}

	@Test
	void sameKeyAndFingerprintReturnsTheStoredResultWithoutRecalculating() throws Exception {
		ApprovalDocument stored = new ApprovalDocument("pay-7001", "ord-5001", "APPROVED", "2026-11-03T10:42:00+09:00",
				10000, "AUTO_RECOMMEND", "uc-1001", "09174", false, new ApprovalDocument.Amount(2100L, "CONFIRMED"), null,
				null, null, new ApprovalDocument.Recalculation("rec-3002", null, null, List.of()), "lt-9001", 10000);
		given(this.idempotency.replay(eq(1L), eq("key-1"), anyString(), any()))
			.willReturn(Optional.of(this.jsonMapper.writeValueAsString(stored)));

		assertThat(this.service.approve(ME, "ord-5001", new ApprovalCommand(null, false), "key-1")).isEqualTo(stored);
		verifyNoInteractions(this.recommendations, this.ledger);
		verify(this.orders, never()).markPaid(any(), anyString(), anyString());
	}

	@Test
	void fingerprintCoversThePathAndTheBody() {
		ArgumentCaptor<String> fingerprints = ArgumentCaptor.forClass(String.class);
		given(this.orders.lockForPayment(ME, "ord-5001"))
			.willReturn(new OrderDetail("ord-5001", 10000, LocalDate.of(2026, 11, 3), null, null, "STARBUCKS_STORE",
					PaymentMethod.DIRECT, OrderStatus.PAID, CREATED, "pay-1"));

		for (ApprovalCommand command : List.of(new ApprovalCommand(null, false), new ApprovalCommand(null, false),
				new ApprovalCommand("rec-1", false), new ApprovalCommand(null, true))) {
			assertThatThrownBy(() -> this.service.approve(ME, "ord-5001", command, "k"))
				.isInstanceOf(OrderNotPayableException.class);
		}
		verify(this.idempotency, org.mockito.Mockito.times(4)).replay(eq(1L), eq("k"), fingerprints.capture(), any());
		List<String> values = fingerprints.getAllValues();
		assertThat(values.get(0)).isEqualTo(values.get(1));
		assertThat(values.subList(1, 4)).doesNotHaveDuplicates();
	}

	@Test
	void alreadyPaidOrderWithAnotherKeyIsRejectedBeforeRecalculation() {
		given(this.orders.lockForPayment(ME, "ord-5001"))
			.willReturn(new OrderDetail("ord-5001", 10000, LocalDate.of(2026, 11, 3), null, null, "STARBUCKS_STORE",
					PaymentMethod.DIRECT, OrderStatus.PAID, CREATED, "pay-1"));

		assertThatThrownBy(() -> this.service.approve(ME, "ord-5001", new ApprovalCommand(null, false), "key-2"))
			.isInstanceOf(OrderNotPayableException.class);
		verifyNoInteractions(this.recommendations, this.ledger);
	}

	@Test
	void simulatedFailureStoresTheAttemptWithoutLedgerOrPaidOrder() {
		recalculatesTo("09174", 2100L, List.of(new RecommendationCalculation.BucketDeduction("uc-1001", "GREAT", 2100L)));

		ApprovalDocument document = this.service.approve(ME, "ord-5001", new ApprovalCommand(null, true), "key-1");

		// D-23-4: 201·FAILED, 원장 행 없음, 주문 PAID 아님, 혜택 null, 취소 가능 0
		assertThat(document.status()).isEqualTo("FAILED");
		assertThat(document.ledgerTransactionId()).isNull();
		assertThat(document.currentOrderBenefit()).isEqualTo(new ApprovalDocument.Amount(null, "NEEDS_CONFIRMATION"));
		assertThat(document.cancellableAmountWon()).isZero();
		assertThat(this.saved.getStatus()).isEqualTo(PaymentStatus.FAILED);
		verifyNoInteractions(this.ledger);
		verify(this.orders, never()).markPaid(any(), anyString(), anyString());
	}

	@Test
	void partialCancellationOfAnUnresolvedAttributionCardMatchesTheContractExample() {
		approveWith("09174", 2100L, "GREAT");
		this.now.set(Instant.parse("2026-11-03T02:00:00Z"));
		given(this.ledger.usageEffectsOf(9001L)).willReturn(List.of(new LedgerUsageEffect("GREAT", 2100L)));

		CancellationDocument document = this.service.cancel(ME, "pay-7001", 4000, "key-c1");

		assertThat(document.cancellationId()).isEqualTo("cn-8001");
		assertThat(document.simulatedPaymentId()).isEqualTo("pay-7001");
		assertThat(document.amountWon()).isEqualTo(4000);
		assertThat(document.cancelledAt()).isEqualTo("2026-11-03T11:00:00+09:00");
		assertThat(document.ledgerTransactionId()).isEqualTo("lt-9002");
		assertThat(document.paymentStatus()).isEqualTo("PARTIALLY_CANCELLED");
		assertThat(document.cancellableAmountWon()).isEqualTo(6000);
		assertThat(document.impact().confirmed()).isEmpty();
		assertThat(document.impact().needsConfirmation()).extracting(CancellationDocument.ImpactItem::target)
			.containsExactly("PERFORMANCE", "BENEFIT", "LIMIT_USAGE");
		ArgumentCaptor<NewLedgerEntry> entry = ArgumentCaptor.forClass(NewLedgerEntry.class);
		verify(this.ledger).recordCancellation(entry.capture());
		assertThat(entry.getValue()).isEqualTo(new NewLedgerEntry(1L, 1001L, LedgerEntryType.CANCELLATION, -4000,
				LocalDate.of(2026, 11, 3), null, "STARBUCKS_STORE", "DIRECT", "스타벅스 송도점", 9001L, 7001L,
				YearMonth.of(2026, 11), AttributionStatus.CONFIRMED, LocalDateTime.of(2026, 11, 3, 11, 0, 0)));
		verify(this.orders, never()).markCancelled(any(), anyString());
		assertThat(this.saved.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_CANCELLED);
		assertThat(this.saved.cancellableWon()).isEqualTo(6000);
		ArgumentCaptor<PaymentCancellationEntity> line = ArgumentCaptor.forClass(PaymentCancellationEntity.class);
		verify(this.cancellations).save(line.capture());
		assertThat(line.getValue().getSimulatedPaymentId()).isEqualTo(7001L);
		assertThat(line.getValue().getAmountWon()).isEqualTo(4000L);
		assertThat(line.getValue().getLedgerId()).isEqualTo(9002L);
	}

	@Test
	void fullCancellationOfAReceiptMonthCardConfirmsPerformanceAndCancelsTheOrder() {
		approveWith("09271", 0L, null);
		this.now.set(Instant.parse("2026-11-03T02:00:00Z"));
		given(this.states.get(ME, "uc-1001", YearMonth.of(2026, 11))).willReturn(state(null));

		CancellationDocument document = this.service.cancel(ME, "pay-7001", 10000, "key-c1");

		assertThat(document.paymentStatus()).isEqualTo("CANCELLED");
		assertThat(document.cancellableAmountWon()).isZero();
		assertThat(document.impact().confirmed()).containsExactly(new CancellationDocument.ImpactItem("PERFORMANCE",
				"uc-1001", "2026-11", null, null, -10000L, "RECEIPT_MONTH", null));
		assertThat(document.impact().needsConfirmation()).isEmpty();
		verify(this.orders).markCancelled(ME, "ord-5001");
		assertThat(this.saved.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
		assertThat(this.saved.cancellableWon()).isZero();
	}

	@Test
	void receiptMonthApprovalBeforeTheLatestSnapshotIsNotSubtracted() {
		approveWith("09271", 0L, null);
		this.now.set(Instant.parse("2026-11-03T02:00:00Z"));
		// 승인(10:42) 뒤 10:50에 월간 상태를 다시 입력함 → 스냅샷에 승인이 들어 있는지 모름(D-17)
		given(this.states.get(ME, "uc-1001", YearMonth.of(2026, 11)))
			.willReturn(state(LocalDateTime.of(2026, 11, 3, 10, 50, 0)));

		CancellationDocument document = this.service.cancel(ME, "pay-7001", 1000, "key-c1");

		assertThat(document.impact().confirmed()).isEmpty();
		assertThat(document.impact().needsConfirmation()).containsExactly(new CancellationDocument.ImpactItem(
				"PERFORMANCE", "uc-1001", "2026-11", null, null, null, null, CancellationImpacts.BEFORE_SNAPSHOT));
	}

	@Test
	void cancellationFailures() {
		approveWith("09174", 2100L, "GREAT");

		assertThatThrownBy(() -> this.service.cancel(ME, "pay-7001", 10001, "k"))
			.isInstanceOf(CancellationAmountExceededException.class);
		assertThatThrownBy(() -> this.service.cancel(ME, "pay-x", 1, "k"))
			.isInstanceOf(SimulatedPaymentNotFoundException.class);
		assertThatThrownBy(() -> this.service.cancel(ME, "pay-7002", 1, "k"))
			.isInstanceOf(SimulatedPaymentNotFoundException.class);
		this.saved.cancel(10000);
		assertThatThrownBy(() -> this.service.cancel(ME, "pay-7001", 1, "k"))
			.isInstanceOf(PaymentAlreadyCancelledException.class);
	}

	@Test
	void failedPaymentHasNothingToCancel() {
		recalculatesTo("09174", 2100L, List.of());
		this.service.approve(ME, "ord-5001", new ApprovalCommand(null, true), "key-1");

		assertThatThrownBy(() -> this.service.cancel(ME, "pay-7001", 1, "k"))
			.isInstanceOf(CancellationAmountExceededException.class);
		verify(this.ledger, never()).recordCancellation(any());
	}

	@Test
	void getCombinesTheApprovalWithCurrentStatusAndCancellations() {
		approveWith("09174", 2100L, "GREAT");
		given(this.payments.findBySimulatedPaymentIdAndUserId(7001L, 1L)).willReturn(Optional.of(this.saved));
		this.saved.cancel(4000);
		given(this.cancellations.findBySimulatedPaymentIdOrderByCancellationIdAsc(7001L)).willReturn(List.of(
				withId(new PaymentCancellationEntity(7001L, 4000, LocalDateTime.of(2026, 11, 3, 11, 0, 0), 9002L), 8001L)));

		PaymentDetail detail = this.service.get(ME, "pay-7001");

		assertThat(detail.status()).isEqualTo("PARTIALLY_CANCELLED");
		assertThat(detail.cancellableAmountWon()).isEqualTo(6000);
		assertThat(detail.ledgerTransactionId()).isEqualTo("lt-9001");
		assertThat(detail.cancellations()).containsExactly(
				new PaymentDetail.CancellationLine("cn-8001", 4000, "2026-11-03T11:00:00+09:00", "lt-9002"));
		assertThatThrownBy(() -> this.service.get(ME, "pay-0")).isInstanceOf(SimulatedPaymentNotFoundException.class);
	}

	private void approveWith(String productId, Long benefitWon, String bucket) {
		recalculatesTo(productId, benefitWon, (bucket == null) ? List.of()
				: List.of(new RecommendationCalculation.BucketDeduction("uc-1001", bucket, benefitWon)));
		this.service.approve(ME, "ord-5001", new ApprovalCommand(null, false), "key-a");
		given(this.payments.findForUpdate(anyLong(), eq(1L))).willReturn(Optional.empty());
		given(this.payments.findForUpdate(7001L, 1L)).willReturn(Optional.of(this.saved));
	}

	private void recalculatesTo(String productId, Long benefitWon,
			List<RecommendationCalculation.BucketDeduction> deductions) {
		RecommendationDocument document = auto("rec-3002", selected("uc-1001", productId, "MONTHLY_TOTAL"),
				List.of(compared("uc-1001", productId, benefitWon, List.of(service("SVC-GREAT", benefitWon))),
						compared("uc-1003", "01664", 1200L, List.of())),
				List.of(PaymentFixtures.excluded("uc-1002", null, "NO_SUPPORTED_RULE", List.of())), true);
		given(this.recommendations.calculateForApproval(ME, "ord-5001"))
			.willReturn(new RecommendationCalculation(document, deductions));
	}

	private static UserCardView card(String id, String productId) {
		return new UserCardView(id, "카드", HoldingSource.MANUAL, null,
				new UserCardMapping(MappingStatus.USER_CONFIRMED, productId, ConfirmationMethod.MANUAL_SELECTION, CREATED),
				false, RecommendationEligibility.of(HoldingSource.MANUAL, MappingStatus.USER_CONFIRMED, false), CREATED);
	}

	private static MonthlyStateView state(LocalDateTime snapshotAt) {
		return new MonthlyStateView("uc-1001", YearMonth.of(2026, 11), snapshotAt, StateValue.unknown(), List.of(),
				StateValue.unknown(), List.of(), ServiceSelectionStatus.UNKNOWN, null, StateValue.unknown(), null);
	}

	// 테스트가 옮기는 시각을 따르는 시계. withZone 뒤에도 같은 시각 원천을 본다.
	private final class MovableClock extends Clock {

		private final java.time.ZoneId zone;

		MovableClock(java.time.ZoneId zone) {
			this.zone = zone;
		}

		@Override
		public java.time.ZoneId getZone() {
			return this.zone;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return new MovableClock(zone);
		}

		@Override
		public Instant instant() {
			return SimulatedPaymentServiceTest.this.now.get();
		}

	}

	private static PaymentCancellationEntity withId(PaymentCancellationEntity entity, long id) {
		ReflectionTestUtils.setField(entity, "cancellationId", id);
		return entity;
	}

}
