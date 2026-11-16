package com.beone.api.benefit.engine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.beone.api.benefit.card.LimitBucketId;
import com.beone.api.benefit.card.ServiceId;
import com.beone.api.benefit.input.CardMonthlyState;
import com.beone.api.benefit.money.BenefitAmount;
import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.primitive.LimitTierPerformance;
import com.beone.api.benefit.primitive.PerformanceTiers;
import com.beone.api.benefit.result.Adoption;
import com.beone.api.benefit.result.ApplicationStatus;
import com.beone.api.benefit.result.CardBenefit;
import com.beone.api.benefit.result.OrderBenefit;
import com.beone.api.benefit.result.OrderServiceBenefit;
import com.beone.api.benefit.result.PendingReason;
import com.beone.api.benefit.result.ServiceBenefit;
import com.beone.api.benefit.result.ServiceResultNotes;
import com.beone.api.benefit.rule.BenefitService;
import com.beone.api.benefit.rule.CardRuleVersion;
import com.beone.api.benefit.rule.LimitBucket;
import com.beone.api.benefit.rule.LimitTier;
import com.beone.api.benefit.rule.MerchantTarget;
import com.beone.api.benefit.state.StateSource;
import com.beone.api.benefit.state.StateValue;

/**
 * 결정 결과({@link OrderBenefit.Decided})를 카드 평가로 바꾼다(D-22).
 *
 * <ul>
 * <li>서비스 상태: 채택이면 적용 상태, 채택 미정이면 {@code NEEDS_CONFIRMATION}, 미채택이면 미충족 조건 중 제외 > 대상 업종 > 택1 선택 > 실적 >
 * 최소 금액 > 결제 방식 > 한도 소진의 첫 값이고, 미충족 조건이 없으면 {@code NO_CONTRIBUTION}이다. 재확인·확인 필요·지원 범위 밖 서비스는
 * {@code NEEDS_CONFIRMATION}이다.</li>
 * <li>카드 상태: 금액 미확정이면 {@code NEEDS_CONFIRMATION}, 양수면 한도로 줄어든 채택 서비스가 있을 때 {@code APPLIED_CAP_REMAINING}, 0원이면
 * {@link #zeroStatus}의 순서다.</li>
 * <li>남은 한도: 버킷별 {@code 구간 한도 − 입력 사용액 − 이 주문의 채택 기여액 중 체인에 그 버킷이 있는 서비스의 합}. 구간·사용액을 모르거나 사용액이
 * 한도보다 크거나, 그 버킷을 체인에 둔 서비스의 기여액이 확인 필요이면 null이다.</li>
 * </ul>
 * 카드 ID로 분기하지 않고 규칙 데이터만 쓴다.
 */
final class CardEvaluator {

	/** 미채택 서비스 상태를 고르는 순서(D-22). 한도 소진은 다른 미충족과 함께 나오지 않아 마지막에 둔다. */
	private static final List<ApplicationStatus> SERVICE_UNMET_ORDER = List.of(ApplicationStatus.NOT_APPLICABLE_EXCLUDED,
			ApplicationStatus.NOT_APPLICABLE_CATEGORY, ApplicationStatus.NOT_APPLICABLE_SELECTION,
			ApplicationStatus.NOT_APPLICABLE_PERFORMANCE, ApplicationStatus.NOT_APPLICABLE_MIN_AMOUNT,
			ApplicationStatus.NOT_APPLICABLE_PAYMENT_METHOD, ApplicationStatus.LIMIT_EXHAUSTED);

	/** 0원 카드에서 업종이 맞은 서비스의 사유 순서(D-22 카드 상태 4-①·③). */
	private static final List<ApplicationStatus> MATCHED_ORDER = List.of(ApplicationStatus.LIMIT_EXHAUSTED,
			ApplicationStatus.NOT_APPLICABLE_PERFORMANCE, ApplicationStatus.NOT_APPLICABLE_SELECTION,
			ApplicationStatus.NOT_APPLICABLE_MIN_AMOUNT, ApplicationStatus.NOT_APPLICABLE_PAYMENT_METHOD,
			ApplicationStatus.NO_CONTRIBUTION);

	/** 0원 카드에서 업종이 맞은 특정 업종 서비스가 없을 때의 순서(D-22 카드 상태 4-②). */
	private static final List<ApplicationStatus> OUTSIDE_ORDER = List.of(ApplicationStatus.NOT_APPLICABLE_EXCLUDED,
			ApplicationStatus.NOT_APPLICABLE_CATEGORY);

	static final String FRACTIONAL_WON_NOTE = "원 미만 절사: 약관 근거 없음, 사용자 결정";

	private CardEvaluator() {
	}

	/**
	 * @param userCardId 결과에 돌려줄 보유 카드 ID
	 * @param rule 결정에 쓴 규칙
	 * @param decided 결정 결과
	 * @param state 결정에 쓴 월간 상태
	 * @param registeredAt 결정에 쓴 사용 등록일
	 * @param orderDate 주문일
	 * @return 카드 평가
	 */
	static CardEvaluation evaluate(String userCardId, CardRuleVersion rule, OrderBenefit.Decided decided,
			CardMonthlyState state, StateValue<LocalDate> registeredAt, LocalDate orderDate) {
		if (!rule.ref().equals(decided.source().rule())) {
			throw new IllegalArgumentException("decision used rule " + decided.source().rule() + ", not " + rule.ref());
		}
		List<BenefitService> services = rule.services()
			.stream()
			.sorted(Comparator.comparing(BenefitService::id, PendingReason.SERVICE_ORDER))
			.toList();
		Map<ServiceId, OrderServiceBenefit> decidedById = decided.services()
			.stream()
			.collect(Collectors.toMap(service -> service.candidate().serviceId(), Function.identity()));
		Map<ServiceId, ServiceResultNotes> notesById = decided.serviceNotes()
			.stream()
			.collect(Collectors.toMap(ServiceResultNotes::serviceId, Function.identity()));
		BenefitAmount cardAmount = decided.orderBenefit();

		// 1) 서비스별 상태·기여액
		Map<ServiceId, ServiceOutcome> outcomes = new LinkedHashMap<>();
		for (BenefitService service : services) {
			outcomes.put(service.id(), outcome(decidedById.get(service.id()), cardAmount));
		}

		// 2) 버킷별 차감액과 확인 필요 체인
		Set<LimitBucketId> unknownDeduction = new HashSet<>();
		Map<LimitBucketId, Long> deductions = new HashMap<>();
		for (BenefitService service : services) {
			ServiceOutcome outcome = outcomes.get(service.id());
			for (LimitBucket bucket : rule.limitChain(service)) {
				if (!outcome.contribution().isConfirmed()) {
					unknownDeduction.add(bucket.id());
				}
				else if (outcome.adoption() == Adoption.ADOPTED) {
					deductions.merge(bucket.id(), outcome.contribution().confirmedWon().value(), Math::addExact);
				}
			}
		}

		// 3) 서비스 결과와 한도 줄
		List<ServiceResult> results = new ArrayList<>();
		List<ServiceBenefit> benefits = new ArrayList<>();
		Map<LimitBucketId, List<Long>> remainingByBucket = new HashMap<>();
		for (BenefitService service : services) {
			ServiceOutcome outcome = outcomes.get(service.id());
			Optional<Won> performance = LimitTierPerformance.of(rule, service, state, registeredAt, orderDate);
			List<ServiceLimitLine> lines = new ArrayList<>();
			for (LimitBucket bucket : rule.limitChain(service)) {
				ServiceLimitLine line = limitLine(bucket, performance, state.usedBenefits().get(bucket.id()),
						unknownDeduction.contains(bucket.id()), deductions.getOrDefault(bucket.id(), 0L));
				lines.add(line);
				remainingByBucket.computeIfAbsent(bucket.id(), id -> new ArrayList<>()).add(line.remainingAfterWon());
			}
			results.add(serviceResult(service, outcome, lines, notesById.get(service.id())));
			benefits.add(new ServiceBenefit(service.id(), outcome.status(), outcome.contribution()));
		}

		// 4) 카드 상태와 CardBenefit
		ApplicationStatus status = cardStatus(cardAmount, outcomes.values(), decided, rule);
		Map<LimitBucketId, BenefitAmount> remainingLimits = new HashMap<>();
		for (LimitBucket bucket : rule.limitBuckets()) {
			remainingLimits.put(bucket.id(), remainingLimit(bucket.id(), remainingByBucket.get(bucket.id())));
		}
		CardBenefit benefit = new CardBenefit(rule.cardId(), Optional.of(rule.ref()), status, cardAmount, cardAmount,
				benefits, remainingLimits);

		List<BucketUsageEffect> effects = new ArrayList<>();
		for (LimitBucket bucket : rule.limitBuckets()) {
			if (unknownDeduction.contains(bucket.id())) {
				effects.add(new BucketUsageEffect(userCardId, bucket.id().value(), null));
			}
			else if (deductions.getOrDefault(bucket.id(), 0L) > 0) {
				effects.add(new BucketUsageEffect(userCardId, bucket.id().value(), deductions.get(bucket.id())));
			}
		}
		List<String> policyNotes = results.stream().anyMatch(ServiceResult::fractionalWonTruncated)
				? List.of(FRACTIONAL_WON_NOTE) : List.of();
		List<PendingReasonOutput> pending = decided.reasons()
			.stream()
			.map(reason -> new PendingReasonOutput(reason.kind().name(),
					reason.services().stream().map(ServiceId::value).toList(), reason.detail()))
			.toList();
		return new CardEvaluation(benefit, decided, appliedRule(rule), results, policyNotes, pending, effects);
	}

	static AppliedRuleOutput appliedRule(CardRuleVersion rule) {
		return new AppliedRuleOutput(rule.ruleVersion(), rule.status().name(),
				rule.sources()
					.stream()
					.map(source -> new OfficialSourceOutput(source.uri(), source.locator().orElse(null)))
					.toList());
	}

	/**
	 * 서비스 하나의 상태·후보·기여액. 결정 목록에 없는 서비스(재확인·확인 필요·지원 범위 밖)는 카드 금액도 확인 필요이므로 그 값을 쓴다.
	 */
	private static ServiceOutcome outcome(OrderServiceBenefit decided, BenefitAmount cardAmount) {
		if (decided == null) {
			if (cardAmount.isConfirmed()) {
				throw new IllegalStateException("a service outside the decided list needs an unconfirmed card amount");
			}
			return new ServiceOutcome(ApplicationStatus.NEEDS_CONFIRMATION, null, cardAmount, Adoption.UNDETERMINED,
					Set.of());
		}
		Long candidate = decided.candidate().standaloneAmount().isConfirmed()
				? decided.candidate().standaloneAmount().confirmedWon().value() : null;
		Set<ApplicationStatus> unmet = decided.candidate().unmetConditions();
		ApplicationStatus status = switch (decided.adoption()) {
			case ADOPTED -> decided.appliedStatus().orElseThrow();
			case UNDETERMINED -> ApplicationStatus.NEEDS_CONFIRMATION;
			case NOT_ADOPTED -> first(SERVICE_UNMET_ORDER, unmet).orElse(ApplicationStatus.NO_CONTRIBUTION);
		};
		return new ServiceOutcome(status, candidate, decided.contribution(), decided.adoption(), unmet);
	}

	private static ServiceLimitLine limitLine(LimitBucket bucket, Optional<Won> performance, StateValue<Won> used,
			boolean deductionUnknown, long deduction) {
		Long limit = performance.flatMap(won -> PerformanceTiers.limitTier(bucket, won))
			.map(LimitTier::monthlyCap)
			.map(Won::value)
			.orElse(null);
		Long usedBefore = used != null && used.isKnown() ? used.knownValue().value() : null;
		String usedSource = used == null ? StateSource.UNKNOWN.name() : used.source().name();
		Long remaining = null;
		if (limit != null && usedBefore != null && usedBefore <= limit && !deductionUnknown) {
			remaining = limit - usedBefore - deduction;
			if (remaining < 0) {
				// 채택 기여액은 체인의 남은 한도로 줄인 값이라 음수가 될 수 없다.
				throw new IllegalStateException("limit bucket " + bucket.id() + " remaining would be negative: " + remaining);
			}
		}
		return new ServiceLimitLine(bucket.id().value(), bucket.parent().map(LimitBucketId::value).orElse(null), limit,
				usedBefore, usedSource, remaining);
	}

	private static BenefitAmount remainingLimit(LimitBucketId bucket, List<Long> values) {
		if (values == null || values.isEmpty()) {
			return BenefitAmount.needsConfirmation("limit bucket " + bucket + " is not in any service chain");
		}
		Long first = values.get(0);
		boolean agreed = first != null && values.stream().allMatch(first::equals);
		return agreed ? BenefitAmount.confirmed(first)
				: BenefitAmount.needsConfirmation("remaining limit of bucket " + bucket + " is not confirmed");
	}

	private static ServiceResult serviceResult(BenefitService service, ServiceOutcome outcome, List<ServiceLimitLine> lines,
			ServiceResultNotes notes) {
		BenefitAmountOutput contribution = outcome.contribution().isConfirmed()
				? BenefitAmountOutput.confirmed(outcome.contribution().confirmedWon().value())
				: BenefitAmountOutput.needsConfirmation();
		FractionalWonBasisOutput basis = notes.fractionalWonBasis()
			.map(term -> new FractionalWonBasisOutput(term.kind().name(), term.decision()))
			.orElse(null);
		return new ServiceResult(service.id().value(), outcome.status().name(), outcome.candidateWon(), contribution,
				outcome.adoption().name(), lines, notes.limitReason().map(Enum::name).orElse(null),
				notes.usageBasis().map(Enum::name).orElse(null), notes.mayDifferFromActual(),
				notes.usageNote().orElse(null), notes.fractionalWonTruncated(), basis, notes.unevaluatedExclusions());
	}

	private static ApplicationStatus cardStatus(BenefitAmount amount, Iterable<ServiceOutcome> outcomes,
			OrderBenefit.Decided decided, CardRuleVersion rule) {
		if (!amount.isConfirmed()) {
			return ApplicationStatus.NEEDS_CONFIRMATION;
		}
		if (amount.confirmedWon().isPositive()) {
			for (ServiceOutcome outcome : outcomes) {
				if (outcome.status() == ApplicationStatus.APPLIED_CAP_REMAINING) {
					return ApplicationStatus.APPLIED_CAP_REMAINING;
				}
			}
			return ApplicationStatus.APPLIED;
		}
		return zeroStatus(decided, rule);
	}

	/**
	 * 0원 카드의 상태(D-22 카드 상태 4).
	 * <ol>
	 * <li>특정 업종 대상이면서 업종이 맞은(대상 업종·제외 미충족이 없는) 서비스의 사유: 한도 소진 > 실적 > 택1 선택 > 최소 금액 > 결제 방식</li>
	 * <li>없으면 모든 서비스의 제외 > 대상 업종</li>
	 * <li>없으면 전 가맹점 서비스의 나머지 사유를 1과 같은 순서로</li>
	 * </ol>
	 * 미충족 조건이 없는 0원 서비스의 사유는 {@code NO_CONTRIBUTION}이며 각 순서의 마지막이다.
	 */
	private static ApplicationStatus zeroStatus(OrderBenefit.Decided decided, CardRuleVersion rule) {
		Map<ServiceId, BenefitService> servicesById = rule.services()
			.stream()
			.collect(Collectors.toMap(BenefitService::id, Function.identity()));
		Set<ApplicationStatus> matched = EnumSet.noneOf(ApplicationStatus.class);
		Set<ApplicationStatus> all = EnumSet.noneOf(ApplicationStatus.class);
		Set<ApplicationStatus> allMerchants = EnumSet.noneOf(ApplicationStatus.class);
		for (OrderServiceBenefit service : decided.services()) {
			Set<ApplicationStatus> unmet = service.candidate().unmetConditions();
			Set<ApplicationStatus> reasons = unmet.isEmpty() ? Set.of(ApplicationStatus.NO_CONTRIBUTION) : unmet;
			MerchantTarget target = servicesById.get(service.candidate().serviceId()).target();
			all.addAll(reasons);
			if (target instanceof MerchantTarget.AllMerchants) {
				allMerchants.addAll(reasons);
			}
			else if (!unmet.contains(ApplicationStatus.NOT_APPLICABLE_CATEGORY)
					&& !unmet.contains(ApplicationStatus.NOT_APPLICABLE_EXCLUDED)) {
				matched.addAll(reasons);
			}
		}
		return first(MATCHED_ORDER, matched).or(() -> first(OUTSIDE_ORDER, all))
			.or(() -> first(MATCHED_ORDER, allMerchants))
			.orElse(ApplicationStatus.NO_CONTRIBUTION);
	}

	private static Optional<ApplicationStatus> first(List<ApplicationStatus> order, Set<ApplicationStatus> present) {
		return order.stream().filter(present::contains).findFirst();
	}

	private record ServiceOutcome(ApplicationStatus status, Long candidateWon, BenefitAmount contribution,
			Adoption adoption, Set<ApplicationStatus> unmet) {
	}

}
