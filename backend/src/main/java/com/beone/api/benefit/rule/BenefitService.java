package com.beone.api.benefit.rule;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.beone.api.benefit.card.LimitBucketId;
import com.beone.api.benefit.card.MerchantClass;
import com.beone.api.benefit.card.PaymentMethod;
import com.beone.api.benefit.card.ServiceId;
import com.beone.api.benefit.money.Won;

/**
 * 카드 규칙의 혜택 서비스 하나(예: 09271 기본 적립, 09174 Great 할인).
 *
 * @param id 규칙 안의 서비스 식별자
 * @param kind 포인트 적립 또는 청구 할인(D-11)
 * @param target 대상 가맹점
 * @param excludedClasses 혜택에서 제외하는 업종
 * @param paymentMethods 대상 결제 방식. 명시적으로 나열한다
 * @param minimumAmount 건당 최소 금액. 약관에 없으면 0원
 * @param maximumTargetAmount 건당 할인 대상 금액 상한. 초과분은 할인하지 않는다
 * @param tiers 전월 실적 구간별 계산 방식. 최소 실적 오름차순
 * @param limitBuckets 혜택이 차감하는 한도. 약관에 한도가 없으면 비어 있다
 * @param requiresSelection 사용자가 선택해야 적용되는 선택형 서비스면 참(09271)
 * @param stackableWith 같은 거래에서 함께 받을 수 있는 서비스. 빈 집합이면 가장 큰 단일 혜택만 적용한다(PRD FR-05)
 * @param fractionalWon 원 미만 금액 처리 방식
 */
public record BenefitService(ServiceId id, BenefitKind kind, MerchantTarget target, Set<MerchantClass> excludedClasses,
		Set<PaymentMethod> paymentMethods, Won minimumAmount, Optional<Won> maximumTargetAmount,
		List<ServiceTier> tiers, List<LimitBucketId> limitBuckets, boolean requiresSelection,
		RuleTerm<Set<ServiceId>> stackableWith, RuleTerm<FractionalWonPolicy> fractionalWon, ServiceDetails details) {

	public BenefitService {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(target, "target");
		excludedClasses = Set.copyOf(excludedClasses);
		paymentMethods = Set.copyOf(paymentMethods);
		if (paymentMethods.isEmpty()) {
			throw new IllegalArgumentException("service " + id + " must list at least one payment method");
		}
		Objects.requireNonNull(minimumAmount, "minimumAmount");
		if (minimumAmount.isNegative()) {
			throw new IllegalArgumentException("service " + id + " minimum amount must not be negative");
		}
		Objects.requireNonNull(maximumTargetAmount, "maximumTargetAmount");
		if (maximumTargetAmount.isPresent() && maximumTargetAmount.get().compareTo(minimumAmount) < 0) {
			throw new IllegalArgumentException("service " + id + " maximum target amount is below its minimum");
		}
		if (maximumTargetAmount.isPresent() && !maximumTargetAmount.get().isPositive()) {
			throw new IllegalArgumentException("service " + id + " maximum target amount must be positive");
		}
		tiers = Tiers.requireStrictlyAscending(tiers, ServiceTier::minimumPerformance, "service " + id);
		limitBuckets = List.copyOf(limitBuckets);
		if (Set.copyOf(limitBuckets).size() != limitBuckets.size()) {
			throw new IllegalArgumentException("service " + id + " lists a limit bucket twice");
		}
		Objects.requireNonNull(stackableWith, "stackableWith");
		if (stackableWith.isResolved() && stackableWith.resolvedValue().contains(id)) {
			throw new IllegalArgumentException("service " + id + " cannot stack with itself");
		}
		Objects.requireNonNull(fractionalWon, "fractionalWon");
		Objects.requireNonNull(details, "details");
		if (details.fractionalWonBasis().isPresent() && !fractionalWon.isResolved()) {
			throw new IllegalArgumentException("service " + id + " has a basis for an unresolved fractional won term");
		}
		for (InclusionUnconfirmed pair : details.inclusionUnconfirmed()) {
			requireConsistent(id, pair, target, excludedClasses);
		}
	}

	/**
	 * 추가 값 없이 계산 조건만 있는 서비스.
	 */
	public BenefitService(ServiceId id, BenefitKind kind, MerchantTarget target, Set<MerchantClass> excludedClasses,
			Set<PaymentMethod> paymentMethods, Won minimumAmount, Optional<Won> maximumTargetAmount,
			List<ServiceTier> tiers, List<LimitBucketId> limitBuckets, boolean requiresSelection,
			RuleTerm<Set<ServiceId>> stackableWith, RuleTerm<FractionalWonPolicy> fractionalWon) {
		this(id, kind, target, excludedClasses, paymentMethods, minimumAmount, maximumTargetAmount, tiers, limitBuckets,
				requiresSelection, stackableWith, fractionalWon, ServiceDetails.none());
	}

	// 쌍의 관련 코드는 이 서비스의 대상이나 제외 코드여야 하고, 주문 코드는 그 목록에 이미 있으면 안 된다.
	private static void requireConsistent(ServiceId id, InclusionUnconfirmed pair, MerchantTarget target,
			Set<MerchantClass> excludedClasses) {
		boolean relatedToTarget = target instanceof MerchantTarget.SpecificClasses specific
				&& specific.classes().contains(pair.relatedClass());
		boolean relatedToExclusion = excludedClasses.contains(pair.relatedClass());
		if (!relatedToTarget && !relatedToExclusion) {
			throw new IllegalArgumentException("service " + id + " inclusion pair refers to unrelated class "
					+ pair.relatedClass());
		}
		boolean alreadyListed = excludedClasses.contains(pair.merchantClass())
				|| (target instanceof MerchantTarget.SpecificClasses specific
						&& specific.classes().contains(pair.merchantClass()));
		if (alreadyListed) {
			throw new IllegalArgumentException("service " + id + " inclusion pair lists a known class "
					+ pair.merchantClass());
		}
	}

	/**
	 * 계산이 전월 실적에 따라 달라지면 참. 구간이 둘 이상이거나 단일 구간의 최소 실적이 0원보다 크면 해당한다.
	 */
	public boolean hasPerformanceRequirement() {
		return tiers.size() > 1 || tiers.get(0).minimumPerformance().isPositive();
	}

	/**
	 * 계산식 자체가 미확정인 사유. 하나라도 있으면 이 서비스는 계산하지 않는다.
	 */
	public List<String> unresolvedCalculationReasons() {
		return tiers.stream()
			.map(ServiceTier::calculation)
			.filter(term -> !term.isResolved())
			.map(term -> ((RuleTerm.Unresolved<?>) term).reason())
			.toList();
	}

	/**
	 * 입력에 따라서만 필요한 미확정 조건(중복 허용, 원 미만 처리).
	 */
	public List<ConditionalTerm> unresolvedConditionalTerms() {
		List<ConditionalTerm> unresolved = new ArrayList<>();
		RulePolicy.addIfUnresolved(unresolved, id + ".stackableWith", stackableWith,
				TermTrigger.MULTIPLE_SERVICES_QUALIFY);
		RulePolicy.addIfUnresolved(unresolved, id + ".fractionalWon", fractionalWon, TermTrigger.FRACTIONAL_WON_RESULT);
		return List.copyOf(unresolved);
	}

}
