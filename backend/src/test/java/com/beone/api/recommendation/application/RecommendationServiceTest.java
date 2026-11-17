package com.beone.api.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.beone.api.benefit.engine.DefaultRecommendationEngine;
import com.beone.api.benefit.rule.loading.RuleCatalogLoader;
import com.beone.api.card.application.MappingStatus;
import com.beone.api.card.application.UserCardService;
import com.beone.api.card.application.UserCardView;
import com.beone.api.monthlystate.application.DatePeriod;
import com.beone.api.monthlystate.application.MonthlyStateService;
import com.beone.api.monthlystate.application.MonthlyStateView;
import com.beone.api.monthlystate.application.ServiceSelectionStatus;
import com.beone.api.monthlystate.application.StateValue;
import com.beone.api.order.application.OrderDetail;
import com.beone.api.order.application.OrderNotFoundException;
import com.beone.api.order.application.OrderService;
import com.beone.api.order.application.OrderStatus;
import com.beone.api.order.application.PaymentMethod;
import com.beone.api.preference.application.PaymentPreferenceService;
import com.beone.api.preference.application.PaymentPreferenceView;
import com.beone.api.recommendation.persistence.RecommendationCardBasisEntity;
import com.beone.api.recommendation.persistence.RecommendationCardBasisRepository;
import com.beone.api.recommendation.persistence.RecommendationEntity;
import com.beone.api.recommendation.persistence.RecommendationRepository;
import com.beone.api.user.application.CurrentUser;
import com.beone.api.user.application.DemoUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

/**
 * 추천 서비스의 입력 조립·저장·무효화 판정을 확인한다. 엔진은 실제 규칙 파일(카드 4종)을 읽는 실제 구현이다.
 *
 * <p>계산 기대값은 평가 자료 C01(09271, 2026-10-15 일반 국내 100,000원 직접 결제, 전월 실적 없음 → 1,000원 APPLIED)에서 옮겼다.
 * 09271은 미확정 실적 정책 조건이 없어 {@code policyEvidence=UNDETERMINED}의 영향을 받지 않는다. 무효화 우선순위는 결정 27 B2-4다.
 */
class RecommendationServiceTest {

	private static final CurrentUser ME = new CurrentUser(1L, DemoUser.MY_CARDS);

	private static final LocalDateTime SAVED_AT = LocalDateTime.of(2026, 10, 15, 9, 0, 0);

	private static final OrderDetail C01_ORDER = new OrderDetail("ord-5001", 100000, LocalDate.of(2026, 10, 15),
			LocalTime.of(10, 0), null, "ORDINARY_DOMESTIC", PaymentMethod.DIRECT, OrderStatus.CREATED, SAVED_AT);

	private final RecommendationRepository recommendations = mock(RecommendationRepository.class);

	private final RecommendationCardBasisRepository bases = mock(RecommendationCardBasisRepository.class);

	private final OrderService orders = mock(OrderService.class);

	private final UserCardService cards = mock(UserCardService.class);

	private final MonthlyStateService states = mock(MonthlyStateService.class);

	private final PaymentPreferenceService preferences = mock(PaymentPreferenceService.class);

	private final List<RecommendationCardBasisEntity> savedBases = new ArrayList<>();

	private RecommendationEntity saved;

	private RecommendationService service;

	@BeforeEach
	void setUp() {
		this.service = new RecommendationService(this.recommendations, this.bases, this.orders, this.cards, this.states,
				this.preferences, new DefaultRecommendationEngine(RuleCatalogLoader.load()), JsonMapper.builder().build(),
				Clock.fixed(Instant.parse("2026-10-15T01:41:00Z"), ZoneOffset.UTC));
		given(this.orders.get(ME, "ord-5001")).willReturn(C01_ORDER);
		given(this.preferences.get(ME)).willReturn(PaymentPreferenceView.NOT_SET);
		given(this.recommendations.saveAndFlush(any())).willAnswer(invocation -> {
			RecommendationEntity entity = invocation.getArgument(0);
			ReflectionTestUtils.setField(entity, "recommendationId", 3001L);
			this.saved = entity;
			return entity;
		});
		given(this.bases.saveAll(any())).willAnswer(invocation -> {
			Iterable<RecommendationCardBasisEntity> rows = invocation.getArgument(0);
			rows.forEach(this.savedBases::add);
			return this.savedBases;
		});
		given(this.recommendations.findByRecommendationIdAndUserId(3001L, 1L))
			.willAnswer(invocation -> Optional.ofNullable(this.saved));
		given(this.bases.findByRecommendationIdOrderByCardBasisIdAsc(3001L)).willAnswer(invocation -> this.savedBases);
	}

	@Test
	void calculatesC01ThroughTheEngineAndStoresTheDocument() {
		holds(EngineInputAssemblerTest.card("uc-11", MappingStatus.USER_CONFIRMED, "09271", false));
		given(this.states.get(eq(ME), eq("uc-11"), eq(YearMonth.of(2026, 10)))).willReturn(c01State("uc-11"));

		RecommendationDocument document = this.service.calculate(ME, "ord-5001");

		assertThat(document.recommendationId()).isEqualTo("rec-3001");
		assertThat(document.orderId()).isEqualTo("ord-5001");
		assertThat(document.calculatedAt()).isEqualTo("2026-10-15T10:41:00+09:00");
		assertThat(document.decisionDate()).isEqualTo(LocalDate.of(2026, 10, 15));
		assertThat(document.selectionMode()).isEqualTo("AUTO_RECOMMEND");
		assertThat(document.outcome()).isEqualTo("SELECTED");
		assertThat(document.conditional()).isFalse();
		assertThat(document.selected().userCardId()).isEqualTo("uc-11");
		assertThat(document.selected().cardProductId()).isEqualTo("09271");
		// 비교 카드 1장: 근거와 차액을 주장하지 않는다(D-22)
		assertThat(document.selected().basis()).isEqualTo(new RecommendationDocument.Basis(null, false, List.of(), null));
		RecommendationDocument.ComparedCard card = document.comparedCards().get(0);
		assertThat(card.applicationStatus()).isEqualTo("APPLIED");
		assertThat(card.currentOrderBenefit()).isEqualTo(new RecommendationDocument.Amount(1000L, "CONFIRMED"));
		assertThat(card.monthlyTotalBenefit()).isEqualTo(card.currentOrderBenefit());
		assertThat(card.mapping()).isEqualTo(new RecommendationDocument.Mapping("USER_CONFIRMED", "사용자 확인 매핑, 공식 SKU·약관 버전 미검증"));
		assertThat(card.displayName()).isEqualTo("카드");
		assertThat(document.excludedCards()).isEmpty();
		assertThat(document.fixedCardComparison()).isNull();
		assertThat(document.inputSnapshot().order())
			.isEqualTo(new RecommendationDocument.OrderSnapshot(100000, LocalDate.of(2026, 10, 15), "ORDINARY_DOMESTIC",
					"DIRECT"));
		assertThat(document.inputSnapshot().representativeUserCardId()).isNull();
		assertThat(document.inputSnapshot().monthlyStateSnapshotAt())
			.containsExactly(java.util.Map.entry("uc-11", "2026-10-15T09:00:00+09:00"));
		assertThat(document.notes()).containsExactly(RecommendationNotes.PLANNED_SPENDING_EXCLUDED,
				RecommendationNotes.SINGLE_COMPARED_CARD);
		assertThat(this.saved.getOrderId()).isEqualTo(5001L);
		assertThat(this.saved.getOrderMonth()).isEqualTo("2026-10");
		assertThat(this.saved.getDocumentJson()).contains("\"recommendationId\":\"rec-3001\"");
		assertThat(this.savedBases).singleElement().satisfies(basis -> {
			assertThat(basis.getUserCardId()).isEqualTo(11L);
			assertThat(basis.getMappingStatus()).isEqualTo("USER_CONFIRMED");
			assertThat(basis.getRuleCardProductId()).isEqualTo("09271");
			assertThat(basis.getMonthlySnapshotAt()).isEqualTo(SAVED_AT);
		});
	}

	@Test
	void noComparableCardIsNotDeterminedWithNullSnapshot() {
		holds(EngineInputAssemblerTest.card("uc-12", MappingStatus.UNMAPPED, null, false));
		given(this.states.get(eq(ME), eq("uc-12"), any())).willReturn(emptyState("uc-12"));

		RecommendationDocument document = this.service.calculate(ME, "ord-5001");

		assertThat(document.outcome()).isEqualTo("NOT_DETERMINED");
		assertThat(document.conditional()).isFalse();
		assertThat(document.selected()).isNull();
		assertThat(document.excludedCards()).singleElement().satisfies(card -> {
			assertThat(card.reason()).isEqualTo("MAPPING_UNCONFIRMED");
			assertThat(card.monthlyTotalBenefit()).isEqualTo(new RecommendationDocument.Amount(null, "NEEDS_CONFIRMATION"));
			assertThat(card.applicationStatus()).isNull();
		});
		assertThat(document.inputSnapshot()).isNull();
		assertThat(document.notes()).containsExactly(RecommendationNotes.PLANNED_SPENDING_EXCLUDED,
				RecommendationNotes.NO_COMPARABLE_CARD);
	}

	@Test
	void missingOrderPropagatesAndNothingIsSaved() {
		given(this.orders.get(ME, "ord-9")).willThrow(new OrderNotFoundException());

		assertThatThrownBy(() -> this.service.calculate(ME, "ord-9")).isInstanceOf(OrderNotFoundException.class);
		verify(this.recommendations, never()).saveAndFlush(any());
	}

	@Test
	void createdOrderIsNotPaid() {
		// 주문 상태에 아직 CREATED만 있어 결제된 주문을 만들 수 없다. B3가 상태를 더하면 409 경로가 동작한다.
		assertThat(RecommendationService.isPaid(C01_ORDER)).isFalse();
	}

	@Test
	void unchangedInputsAreNotInvalidated() {
		calculateC01();

		RecommendationView view = this.service.get(ME, "rec-3001");

		assertThat(view.invalidated()).isFalse();
		assertThat(view.invalidatedReason()).isNull();
		assertThat(view.document().recommendationId()).isEqualTo("rec-3001");
		assertThat(view.document().comparedCards().get(0).currentOrderBenefit().amountWon()).isEqualTo(1000L);
	}

	@Test
	void mappingChangeOutranksMonthlyStateChange() {
		calculateC01();
		holds(EngineInputAssemblerTest.card("uc-11", MappingStatus.UNMAPPED, null, false));
		given(this.states.get(eq(ME), eq("uc-11"), any())).willReturn(emptyState("uc-11"));

		assertThat(this.service.get(ME, "rec-3001").invalidatedReason()).isEqualTo(InvalidationReason.MAPPING_CHANGED);
	}

	@Test
	void exclusionChangeAndReleaseCountAsMappingChanged() {
		calculateC01();
		holds(EngineInputAssemblerTest.card("uc-11", MappingStatus.USER_CONFIRMED, "09271", true));
		assertThat(this.service.get(ME, "rec-3001").invalidatedReason()).isEqualTo(InvalidationReason.MAPPING_CHANGED);

		holds();
		assertThat(this.service.get(ME, "rec-3001").invalidatedReason()).isEqualTo(InvalidationReason.MAPPING_CHANGED);
	}

	@Test
	void reenteredMonthlyStateIsMonthlyStateChanged() {
		calculateC01();
		MonthlyStateView reentered = c01State("uc-11");
		given(this.states.get(eq(ME), eq("uc-11"), eq(YearMonth.of(2026, 10))))
			.willReturn(new MonthlyStateView("uc-11", YearMonth.of(2026, 10), SAVED_AT.plusMinutes(5),
					reentered.previousMonthPerformanceWon(), List.of(), StateValue.unknown(), List.of(),
					ServiceSelectionStatus.UNKNOWN, null, reentered.registeredOn(), null));

		assertThat(this.service.get(ME, "rec-3001").invalidatedReason())
			.isEqualTo(InvalidationReason.MONTHLY_STATE_CHANGED);
	}

	@Test
	void otherUsersOrMalformedIdIsNotFound() {
		calculateC01();

		assertThatThrownBy(() -> this.service.get(new CurrentUser(2L, DemoUser.VIRTUAL_PORTFOLIO), "rec-3001"))
			.isInstanceOf(RecommendationNotFoundException.class);
		assertThatThrownBy(() -> this.service.get(ME, "rec-03001")).isInstanceOf(RecommendationNotFoundException.class);
		// 다른 사용자는 자기 user_id로만 찾으므로 결과가 비고, 형식이 틀린 ID는 저장소까지 가지 않는다.
		verify(this.recommendations).findByRecommendationIdAndUserId(3001L, 2L);
		verify(this.recommendations, never()).findByRecommendationIdAndUserId(eq(3001L), eq(1L));
	}

	private void calculateC01() {
		holds(EngineInputAssemblerTest.card("uc-11", MappingStatus.USER_CONFIRMED, "09271", false));
		given(this.states.get(eq(ME), eq("uc-11"), eq(YearMonth.of(2026, 10)))).willReturn(c01State("uc-11"));
		this.service.calculate(ME, "ord-5001");
	}

	private void holds(UserCardView... held) {
		given(this.cards.list(ME)).willReturn(List.of(held));
	}

	// C01: 전월(9월) 적격 거래 없음 → 전월 실적 0원 입력. 등록일은 fixture 기본값 2026-01-01.
	private static MonthlyStateView c01State(String userCardId) {
		LocalDate registered = LocalDate.of(2026, 1, 1);
		return new MonthlyStateView(userCardId, YearMonth.of(2026, 10), SAVED_AT,
				StateValue.manual(0L, new DatePeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), SAVED_AT),
				List.of(), StateValue.unknown(), List.of(), ServiceSelectionStatus.UNKNOWN, null,
				StateValue.manual(registered, new DatePeriod(registered, registered), SAVED_AT), null);
	}

	private static MonthlyStateView emptyState(String userCardId) {
		return new MonthlyStateView(userCardId, YearMonth.of(2026, 10), null, StateValue.unknown(), List.of(),
				StateValue.unknown(), List.of(), ServiceSelectionStatus.UNKNOWN, null, StateValue.unknown(), null);
	}

}
