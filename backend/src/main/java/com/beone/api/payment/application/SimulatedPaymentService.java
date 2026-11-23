package com.beone.api.payment.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.rule.CancellationAttribution;
import com.beone.api.benefit.rule.RuleCatalog;
import com.beone.api.benefit.rule.RuleSelection;
import com.beone.api.benefit.rule.RuleTerm;
import com.beone.api.card.application.UserCardId;
import com.beone.api.card.application.UserCardService;
import com.beone.api.card.application.UserCardView;
import com.beone.api.ledger.application.AttributionStatus;
import com.beone.api.ledger.application.LedgerEntryType;
import com.beone.api.ledger.application.LedgerTransactionId;
import com.beone.api.ledger.application.LedgerUsageEffect;
import com.beone.api.ledger.application.LedgerWriter;
import com.beone.api.ledger.application.NewLedgerEntry;
import com.beone.api.monthlystate.application.MonthlyStateCardNotFoundException;
import com.beone.api.monthlystate.application.MonthlyStateService;
import com.beone.api.order.application.OrderDetail;
import com.beone.api.order.application.OrderId;
import com.beone.api.order.application.OrderService;
import com.beone.api.order.application.OrderStatus;
import com.beone.api.payment.persistence.PaymentCancellationEntity;
import com.beone.api.payment.persistence.PaymentCancellationRepository;
import com.beone.api.payment.persistence.SimulatedPaymentEntity;
import com.beone.api.payment.persistence.SimulatedPaymentRepository;
import com.beone.api.recommendation.application.RecommendationCalculation;
import com.beone.api.recommendation.application.RecommendationService;
import com.beone.api.recommendation.application.RecommendationView;
import com.beone.api.user.application.CurrentUser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 모의 승인·조회·취소(docs/api/mock-payments.md). 실제 카드 결제·외부 호출이 아니다(CLAUDE.md 제품의 경계).
 *
 * <p>승인 직전 추천 재계산은 recommendation 공개 부품({@link RecommendationService#calculateForApproval})으로만 하고 엔진을 직접 부르지
 * 않는다. 원장 행 기록·결제 상태 변경·주문 상태 전이·영향 계산·멱등 키 저장은 한 트랜잭션이다(계약 "한 트랜잭션으로 처리").
 *
 * <p>동시 요청은 행 잠금으로 직렬화한다. 승인은 주문 행, 취소는 결제 행을 {@code SELECT ... FOR UPDATE}로 먼저 잠근 뒤 멱등 키를 본다.
 * 같은 주문을 다른 키로 동시에 승인하면 뒤 요청은 앞 요청이 커밋한 {@code PAID}를 읽어 409 {@code ORDER_ALREADY_PAID}가 된다.
 *
 * <p>승인은 주문이 {@code CREATED}이면 주문 행 다음에 이 사용자의 보유 카드 행 전부를 ID 오름차순으로 잠근다(#122). 잠금 순서는
 * 주문 행 → 보유 카드 행이고, 이 두 잠금은 트랜잭션의 첫 일반 읽기보다 앞선다. 이미 승인된 주문의 재생·거절은 카드를 잠그지 않는다.
 * 전액 취소는 원장 INSERT의 외래 키 검사로 카드 행 공유 잠금을 쥔 뒤 주문 행을 잠그므로, 같은 주문의 재생이 카드를 잠그면 순환
 * 대기가 생기기 때문이다. MySQL 기본 격리 수준(REPEATABLE READ)은 첫 일반 읽기에서 읽기 시점을 정하므로,
 * 카드 잠금을 기다린 뒤 읽는 원장은 앞 승인이 커밋한 행을 포함한다. 그래서 같은 카드의 다른 주문을 동시에 승인해도 뒤 요청은 앞
 * 승인의 한도 차감액을 반영한 잔여 한도로 계산한다. 취소는 다른 결제의 원장을 읽어 계산하지 않으므로 카드 행을 잠그지 않는다.
 */
@Service
public class SimulatedPaymentService {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private static final DateTimeFormatter INSTANT = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssXXX");

	private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
	};

	private final SimulatedPaymentRepository paymentRepository;

	private final PaymentCancellationRepository cancellationRepository;

	private final IdempotencyStore idempotencyStore;

	private final OrderService orderService;

	private final RecommendationService recommendationService;

	private final UserCardService userCardService;

	private final MonthlyStateService monthlyStateService;

	private final LedgerWriter ledgerWriter;

	private final RuleCatalog ruleCatalog;

	private final JsonMapper jsonMapper;

	private final Clock clock;

	/**
	 * {@link Clock} 빈이 하나 있으면 그것을, 없으면 시스템 시계를 쓴다(추천 서비스와 같은 방식).
	 * @param paymentRepository 모의 결제 저장소
	 * @param cancellationRepository 모의 취소 저장소
	 * @param idempotencyStore 멱등 키 저장
	 * @param orderService 주문 잠금·상태 전이
	 * @param recommendationService 승인 직전 재계산·본 추천 조회
	 * @param userCardService 승인 카드의 매핑 표시
	 * @param monthlyStateService 취소 영향의 스냅샷 시각
	 * @param ledgerWriter 원장 쓰기
	 * @param ruleCatalog 취소 귀속 규칙 읽기
	 * @param jsonMapper 응답 본문 저장용 JSON 변환기
	 * @param clock 승인·취소 시각을 정할 시계
	 */
	@Autowired
	public SimulatedPaymentService(SimulatedPaymentRepository paymentRepository,
			PaymentCancellationRepository cancellationRepository, IdempotencyStore idempotencyStore,
			OrderService orderService, RecommendationService recommendationService, UserCardService userCardService,
			MonthlyStateService monthlyStateService, LedgerWriter ledgerWriter, RuleCatalog ruleCatalog,
			JsonMapper jsonMapper, ObjectProvider<Clock> clock) {
		this(paymentRepository, cancellationRepository, idempotencyStore, orderService, recommendationService,
				userCardService, monthlyStateService, ledgerWriter, ruleCatalog, jsonMapper,
				clock.getIfUnique(Clock::systemUTC));
	}

	SimulatedPaymentService(SimulatedPaymentRepository paymentRepository,
			PaymentCancellationRepository cancellationRepository, IdempotencyStore idempotencyStore,
			OrderService orderService, RecommendationService recommendationService, UserCardService userCardService,
			MonthlyStateService monthlyStateService, LedgerWriter ledgerWriter, RuleCatalog ruleCatalog,
			JsonMapper jsonMapper, Clock clock) {
		this.paymentRepository = Objects.requireNonNull(paymentRepository, "paymentRepository");
		this.cancellationRepository = Objects.requireNonNull(cancellationRepository, "cancellationRepository");
		this.idempotencyStore = Objects.requireNonNull(idempotencyStore, "idempotencyStore");
		this.orderService = Objects.requireNonNull(orderService, "orderService");
		this.recommendationService = Objects.requireNonNull(recommendationService, "recommendationService");
		this.userCardService = Objects.requireNonNull(userCardService, "userCardService");
		this.monthlyStateService = Objects.requireNonNull(monthlyStateService, "monthlyStateService");
		this.ledgerWriter = Objects.requireNonNull(ledgerWriter, "ledgerWriter");
		this.ruleCatalog = Objects.requireNonNull(ruleCatalog, "ruleCatalog");
		this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
		this.clock = Objects.requireNonNull(clock, "clock").withZone(SEOUL);
	}

	/**
	 * 주문을 다시 계산해 모의 승인한다. {@code simulateFailure}면 실패 기록만 남긴다(201, D-23-4).
	 * @param user 현재 사용자
	 * @param orderId 외부 주문 ID
	 * @param command 승인 요청 입력
	 * @param idempotencyKey 비어 있지 않은 Idempotency-Key
	 * @return 승인 응답 문서
	 * @throws com.beone.api.order.application.OrderNotFoundException 없거나 다른 사용자의 주문
	 * @throws IdempotencyKeyReusedException 같은 키·다른 지문
	 * @throws OrderNotPayableException 이미 모의 승인된 주문
	 * @throws com.beone.api.recommendation.application.RecommendationNotFoundException 없거나 다른 사용자의 본 추천
	 * @throws RecommendationNotDeterminedException 자동 추천인데 확정 불가
	 * @throws RepresentativeCardNotEligibleException 대표 카드 고정인데 쓸 수 없는 대표 카드
	 */
	@Transactional
	public ApprovalDocument approve(CurrentUser user, String orderId, ApprovalCommand command,
			String idempotencyKey) {
		OrderDetail order = this.orderService.lockForPayment(user, orderId);
		// 첫 일반 읽기(멱등 키 조회) 전에 잠가야 기다린 뒤 앞 승인이 커밋한 원장을 읽는다. 이미 승인된 주문(재생·409)은 재계산하지
		// 않으므로 잠그지 않는다. 그 주문의 전액 취소가 원장 INSERT의 외래 키 검사로 카드 행 공유 잠금을 쥔 채 주문 행을 기다릴 수
		// 있어, 여기서 카드를 잠그면 서로 기다린다
		if (order.status() == OrderStatus.CREATED) {
			this.userCardService.lockHeldCardsForPayment(user);
		}
		LocalDateTime now = now();
		String fingerprint = Fingerprint.of(user.userId(), "POST",
				"/api/v1/me/orders/" + order.orderId() + "/simulated-payments", this.jsonMapper.writeValueAsString(command));
		Optional<String> previous = this.idempotencyStore.replay(user.userId(), idempotencyKey, fingerprint, now);
		if (previous.isPresent()) {
			return this.jsonMapper.readValue(previous.get(), ApprovalDocument.class);
		}
		if (order.status() != OrderStatus.CREATED) {
			throw new OrderNotPayableException();
		}
		RecommendationView seen = (command.seenRecommendationId() == null) ? null
				: this.recommendationService.get(user, command.seenRecommendationId());
		RecommendationCalculation calculation = this.recommendationService.calculateForApproval(user, orderId);
		CardChoice choice = CardChoice.choose(calculation.document());
		String mappingNote = mappingNote(user, choice.userCardId());
		ApprovalDocument.Recalculation recalculation = RecalculationComparison.compare(seen, calculation.document());

		boolean failed = command.simulateFailure();
		SimulatedPaymentEntity payment = this.paymentRepository.saveAndFlush(new SimulatedPaymentEntity(
				user.userId(), OrderId.parse(order.orderId()).orElseThrow(), userCardKey(choice.userCardId()),
				choice.cardProductId(), failed ? PaymentStatus.FAILED : PaymentStatus.APPROVED, order.amountWon(), now,
				this.jsonMapper.writeValueAsString(choice.benefitServiceIds())));
		String paymentId = PaymentIds.SIMULATED_PAYMENT.format(payment.getSimulatedPaymentId());
		String ledgerTransactionId = null;
		if (!failed) {
			long ledgerId = this.ledgerWriter.recordApproval(approvalEntry(user, order, choice, payment, now),
					calculation.deductionsOf(choice.userCardId())
						.stream()
						.map(deduction -> new LedgerUsageEffect(deduction.limitBucketId(), deduction.deductionWon()))
						.toList());
			payment.attachApprovalLedger(ledgerId);
			this.orderService.markPaid(user, orderId, paymentId);
			ledgerTransactionId = LedgerTransactionId.format(ledgerId);
		}
		ApprovalDocument document = new ApprovalDocument(paymentId, order.orderId(), payment.getStatus().name(),
				seoulInstant(now), order.amountWon(), calculation.document().selectionMode(), choice.userCardId(),
				choice.cardProductId(), calculation.document().conditional(),
				failed ? ApprovalDocument.Amount.needsConfirmation() : choice.benefit(), choice.appliedRule(),
				choice.selectionBasis(), mappingNote, recalculation, ledgerTransactionId, payment.cancellableWon());
		String json = this.jsonMapper.writeValueAsString(document);
		payment.attachDocument(json);
		this.idempotencyStore.remember(user.userId(), idempotencyKey, fingerprint, json, now);
		return document;
	}

	/**
	 * 모의 결제 하나의 승인 결과와 취소 내역을 돌려준다.
	 * @param user 현재 사용자
	 * @param simulatedPaymentId 외부 결제 ID
	 * @return 승인 결과와 취소 내역
	 * @throws SimulatedPaymentNotFoundException 없거나 형식이 틀렸거나 다른 사용자의 결제
	 */
	@Transactional(readOnly = true)
	public PaymentDetail get(CurrentUser user, String simulatedPaymentId) {
		SimulatedPaymentEntity payment = PaymentIds.SIMULATED_PAYMENT.parse(simulatedPaymentId)
			.flatMap(id -> this.paymentRepository.findBySimulatedPaymentIdAndUserId(id, user.userId()))
			.orElseThrow(SimulatedPaymentNotFoundException::new);
		List<PaymentDetail.CancellationLine> cancellations = this.cancellationRepository
			.findBySimulatedPaymentIdOrderByCancellationIdAsc(payment.getSimulatedPaymentId())
			.stream()
			.map(cancellation -> new PaymentDetail.CancellationLine(
					PaymentIds.CANCELLATION.format(cancellation.getCancellationId()), cancellation.getAmountWon(),
					seoulInstant(cancellation.getCancelledAt()), LedgerTransactionId.format(cancellation.getLedgerId())))
			.toList();
		return PaymentDetail.of(approval(payment), payment.getStatus(), payment.cancellableWon(), cancellations);
	}

	/**
	 * 모의 승인을 전액 또는 부분 취소한다. 원장에는 항상 취소 행을 남기고, 영향은 확정 규칙이 있는 값만 확정한다(D-17).
	 * @param user 현재 사용자
	 * @param simulatedPaymentId 외부 결제 ID
	 * @param amountWon 1 이상 취소 금액
	 * @param idempotencyKey 비어 있지 않은 Idempotency-Key
	 * @return 취소 응답 문서
	 * @throws SimulatedPaymentNotFoundException 없거나 다른 사용자의 결제
	 * @throws IdempotencyKeyReusedException 같은 키·다른 지문
	 * @throws PaymentAlreadyCancelledException 이미 전액 취소됨
	 * @throws CancellationAmountExceededException 남은 취소 가능 금액 초과(실패한 결제 포함)
	 */
	@Transactional
	public CancellationDocument cancel(CurrentUser user, String simulatedPaymentId, long amountWon,
			String idempotencyKey) {
		SimulatedPaymentEntity payment = PaymentIds.SIMULATED_PAYMENT.parse(simulatedPaymentId)
			.flatMap(id -> this.paymentRepository.findForUpdate(id, user.userId()))
			.orElseThrow(SimulatedPaymentNotFoundException::new);
		LocalDateTime now = now();
		String paymentId = PaymentIds.SIMULATED_PAYMENT.format(payment.getSimulatedPaymentId());
		String fingerprint = Fingerprint.of(user.userId(), "POST",
				"/api/v1/me/simulated-payments/" + paymentId + "/cancellations", "{\"amountWon\":" + amountWon + "}");
		Optional<String> previous = this.idempotencyStore.replay(user.userId(), idempotencyKey, fingerprint, now);
		if (previous.isPresent()) {
			return this.jsonMapper.readValue(previous.get(), CancellationDocument.class);
		}
		if (payment.getStatus() == PaymentStatus.CANCELLED) {
			throw new PaymentAlreadyCancelledException();
		}
		if (amountWon > payment.cancellableWon()) {
			throw new CancellationAmountExceededException();
		}
		ApprovalDocument approval = approval(payment);
		OrderDetail order = this.orderService.get(user, approval.orderId());
		long ledgerId = this.ledgerWriter.recordCancellation(new NewLedgerEntry(user.userId(),
				payment.getUserCardId(), LedgerEntryType.CANCELLATION, -amountWon, now.toLocalDate(), null,
				order.merchantClass(), order.paymentMethod().name(), order.merchantName(),
				payment.getApprovalLedgerId(), payment.getSimulatedPaymentId(), YearMonth.from(now),
				AttributionStatus.CONFIRMED, now));
		PaymentCancellationEntity cancellation = this.cancellationRepository
			.save(new PaymentCancellationEntity(payment.getSimulatedPaymentId(), amountWon, now, ledgerId));
		payment.cancel(amountWon);
		if (payment.getStatus() == PaymentStatus.CANCELLED) {
			this.orderService.markCancelled(user, order.orderId());
		}
		CancellationDocument.Impact impact = CancellationImpacts.of(approval.userCardId(),
				YearMonth.from(order.orderDate()), YearMonth.from(now), amountWon,
				performanceBasis(user, payment, order), approval.currentOrderBenefit(),
				this.jsonMapper.readValue(payment.getBenefitServiceIds(), STRING_LIST),
				this.ledgerWriter.usageEffectsOf(payment.getApprovalLedgerId()));
		CancellationDocument document = new CancellationDocument(
				PaymentIds.CANCELLATION.format(cancellation.getCancellationId()), paymentId, amountWon,
				seoulInstant(now), LedgerTransactionId.format(ledgerId), payment.getStatus().name(),
				payment.cancellableWon(), impact);
		this.idempotencyStore.remember(user.userId(), idempotencyKey, fingerprint,
				this.jsonMapper.writeValueAsString(document), now);
		return document;
	}

	// 판정일(주문일)의 규칙으로 취소 귀속을 보고, 귀속이 확정이면 원 승인과 그 달 월간 상태 스냅샷의 앞뒤를 본다(D-16·D-17).
	private CancellationImpacts.PerformanceBasis performanceBasis(CurrentUser user, SimulatedPaymentEntity payment,
			OrderDetail order) {
		if (payment.getCardProductId() == null) {
			return CancellationImpacts.PerformanceBasis.noAttribution(CancellationImpacts.NO_RULE);
		}
		RuleSelection selection = this.ruleCatalog.select(CardId.of(payment.getCardProductId()), order.orderDate());
		if (!(selection instanceof RuleSelection.Selected selected)) {
			return CancellationImpacts.PerformanceBasis.noAttribution(CancellationImpacts.NO_RULE);
		}
		RuleTerm<CancellationAttribution> attribution = selected.rule().policy().cancellationAttribution();
		if (!attribution.isResolved() || attribution.resolvedValue() != CancellationAttribution.RECEIPT_MONTH) {
			return CancellationImpacts.PerformanceBasis.noAttribution(CancellationImpacts.ATTRIBUTION_UNRESOLVED);
		}
		LocalDateTime snapshotAt;
		try {
			snapshotAt = this.monthlyStateService
				.get(user, UserCardId.format(payment.getUserCardId()), YearMonth.from(order.orderDate()))
				.snapshotAt();
		}
		catch (MonthlyStateCardNotFoundException released) {
			return CancellationImpacts.PerformanceBasis.snapshotUnknown(CancellationImpacts.STATE_UNAVAILABLE);
		}
		if (snapshotAt != null && !payment.getApprovedAt().isAfter(snapshotAt)) {
			return CancellationImpacts.PerformanceBasis.snapshotUnknown(CancellationImpacts.BEFORE_SNAPSHOT);
		}
		return CancellationImpacts.PerformanceBasis.receiptMonth();
	}

	// 원장 승인 행: 거래일·시각은 주문일·주문 시각(판정일과 같음, D-23-1), 귀속월은 거래일 달로 확정(모의 원장은 날짜를 BeONE이 정함).
	private static NewLedgerEntry approvalEntry(CurrentUser user, OrderDetail order, CardChoice choice,
			SimulatedPaymentEntity payment, LocalDateTime now) {
		return new NewLedgerEntry(user.userId(), userCardKey(choice.userCardId()), LedgerEntryType.APPROVAL,
				order.amountWon(), order.orderDate(), order.orderTime(), order.merchantClass(),
				order.paymentMethod().name(), order.merchantName(), null, payment.getSimulatedPaymentId(),
				YearMonth.from(order.orderDate()), AttributionStatus.CONFIRMED, now);
	}

	private String mappingNote(CurrentUser user, String userCardId) {
		return this.userCardService.list(user)
			.stream()
			.filter(card -> card.userCardId().equals(userCardId))
			.findFirst()
			.map(UserCardView::mapping)
			.map(mapping -> mapping.note())
			.orElse(null);
	}

	private ApprovalDocument approval(SimulatedPaymentEntity payment) {
		return this.jsonMapper.readValue(payment.getDocumentJson(), ApprovalDocument.class);
	}

	private static long userCardKey(String userCardId) {
		return UserCardId.parse(userCardId).orElseThrow();
	}

	private LocalDateTime now() {
		return LocalDateTime.now(this.clock).truncatedTo(ChronoUnit.SECONDS);
	}

	private static String seoulInstant(LocalDateTime seoulTime) {
		return INSTANT.format(seoulTime.atZone(SEOUL));
	}

}
