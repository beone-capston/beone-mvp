package com.beone.api.benefit.primitive;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.beone.api.benefit.input.Order;
import com.beone.api.benefit.money.BenefitAmount;
import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.result.ApplicationStatus;
import com.beone.api.benefit.result.LimitReason;
import com.beone.api.benefit.result.ServiceCandidate;
import com.beone.api.benefit.rule.BenefitCalculation;
import com.beone.api.benefit.rule.BenefitService;
import com.beone.api.benefit.rule.ConditionalTerm;
import com.beone.api.benefit.rule.ServiceTier;
import com.beone.api.benefit.rule.TermTrigger;
import com.beone.api.benefit.rule.TriggerEvidence;

/**
 * 주문 한 건에서 서비스 하나만 고를 때의 후보({@link ServiceCandidate})를 판정한다(#31, D-13, D-18 K1).
 *
 * <p>
 * 지원 범위는 선택형이 아닌 서비스이고, 한도가 있으면 체인과 입력 사용액을 함께 받는다. 선택형 서비스는 범위 밖이므로 거부한다.
 * 범위 안에서 근거가 부족한 경우는 거부하지 않고 확인 필요 금액으로 돌려준다.
 *
 * <p>
 * 호출자 전제 조건. 판정기는 아래를 검증하거나 산출하지 않는다.
 * <ul>
 * <li>규칙 버전 선택은 끝난 상태다(예: {@code RuleCatalog}가 고른 규칙의 계산 가능 서비스).</li>
 * <li>빈 {@code limitBuckets}는 약관상 한도 없음이 확인된 서비스에만 쓴다. 한도 기간은 호출자가 확인했다.</li>
 * <li>전월 실적은 이 카드의 실적 기간에 맞는 값이다. 신규 유예 중이면 호출자가 정한 간주 실적이다.</li>
 * <li>신규 유예 근거는 이 서비스에 대해 호출자가 정한 {@link TermTrigger#WITHIN_NEW_CARD_GRACE} 발생 여부다.
 * 카드가 유예 기간이 아니거나 유예가 이 서비스에 적용되지 않음을 확인한 경우에만 {@code CONFIRMED_ABSENT}다.</li>
 * </ul>
 *
 * <p>
 * 확인된 미충족이 하나라도 있으면 다른 근거(사용액 포함)가 미확정이어도 확정 0원이다. 미충족 사유는 순서 없는 집합이며 우선순위를 정하지 않는다.
 * 후보 금액은 한도 적용 전 값이고, 정률은 남은 한도를 {@link ServiceCandidate#remainingLimit()}으로 넘겨 기여액에서 줄인다.
 * 남은 한도 0은 {@code LIMIT_EXHAUSTED} 확정 0원이고, 정액이 남은 한도보다 크면 일부 지급 근거가 없어 확인 필요다.
 * 중복 적용·서비스 간 채택은 판정하지 않는다.
 */
public final class ServiceCandidateEvaluator {

	private ServiceCandidateEvaluator() {
	}

	/**
	 * @param service 비선택형 서비스
	 * @param order 현재 주문
	 * @param previousMonthPerformance 호출자가 기간을 보증한 전월 실적. 음수는 처리 근거가 없어 거부한다
	 * @param newCardGrace 이 서비스에 대한 신규 유예 발생 근거
	 * @param limits 이 서비스의 한도 체인과 입력 사용액
	 * @throws IllegalArgumentException 지원 범위 밖 서비스이거나 입력 계약을 어긴 경우
	 */
	public static ServiceCandidate evaluate(BenefitService service, Order order, Won previousMonthPerformance,
			TriggerEvidence newCardGrace, ServiceLimits limits) {
		Objects.requireNonNull(service, "service");
		Objects.requireNonNull(order, "order");
		Objects.requireNonNull(previousMonthPerformance, "previousMonthPerformance");
		Objects.requireNonNull(newCardGrace, "newCardGrace");
		Objects.requireNonNull(limits, "limits");
		requireSupported(service, limits);
		// 음수 실적은 다른 조건이 미충족이어도 거부하도록 먼저 구간을 고른다.
		Optional<ServiceTier> tier = PerformanceTiers.serviceTier(service, previousMonthPerformance);

		Set<ApplicationStatus> unmet = performanceIndependentUnmet(service, order);
		List<String> pending = new ArrayList<>();
		if (service.details().inclusionUnconfirmedFor(order.merchantClass())) {
			pending.add("service " + service.id() + " inclusion of " + order.merchantClass() + " is not confirmed");
		}
		boolean graceDisregarded = newCardGrace == TriggerEvidence.CONFIRMED_ABSENT;
		boolean belowEveryTier = tier.isEmpty() || limits.belowEveryTierOfSomeBucket(previousMonthPerformance);
		if (belowEveryTier) {
			// 유예가 적용되면 실적을 간주 실적으로 올려 보므로, 유예 부재가 확인될 때만 실적 미충족을 확정한다(D-03).
			if (graceDisregarded) {
				unmet.add(ApplicationStatus.NOT_APPLICABLE_PERFORMANCE);
			}
			else {
				pending.add("service " + service.id() + " performance is below every tier and new card grace is "
						+ newCardGrace);
			}
		}
		else if (!graceDisregarded && (!isHighestTier(service, tier.get())
				|| limits.belowHighestTierOfSomeBucket(previousMonthPerformance))) {
			// 간주 실적은 실적을 올리기만 하므로 최상위 구간이 아니면 유예로 더 높은 구간이 될 수 있다.
			pending.add("service " + service.id() + " may reach a higher tier under new card grace " + newCardGrace);
		}
		if (!unmet.isEmpty()) {
			return new ServiceCandidate(service.id(), BenefitAmount.confirmed(0), unmet);
		}

		// 계산식이 하나라도 미확정이면 이 서비스는 계산하지 않는다(BenefitService.unresolvedCalculationReasons).
		pending.addAll(service.unresolvedCalculationReasons());
		// K1의 확인 필요는 나머지 조건을 모두 충족한 서비스에만 적용한다.
		Optional<ServiceLimits.Remaining> remaining = limits.chain().isEmpty() || belowEveryTier ? Optional.empty()
				: Optional.of(limits.remaining(previousMonthPerformance));
		Optional<LimitReason> limitReason = remaining.flatMap(ServiceLimits.Remaining::limitReason);
		remaining.flatMap(ServiceLimits.Remaining::pendingReason).ifPresent(pending::add);
		if (!pending.isEmpty()) {
			return needsConfirmation(service, pending, limitReason);
		}
		Optional<Won> remainingWon = remaining.flatMap(ServiceLimits.Remaining::won);
		if (remainingWon.isPresent() && remainingWon.get().isZero()) {
			return new ServiceCandidate(service.id(), BenefitAmount.confirmed(0), Set.of(ApplicationStatus.LIMIT_EXHAUSTED));
		}

		BenefitCalculation calculation = tier.orElseThrow().calculation().resolvedValue();
		Won target = BenefitArithmetic.capTargetAmount(order.amount(), service.maximumTargetAmount());
		UnroundedWon raw = BenefitArithmetic.apply(calculation, target);
		if (calculation instanceof BenefitCalculation.FixedAmount) {
			if (raw.wholeWon().compareTo(order.amount()) > 0) {
				// 정액을 결제 금액으로 줄일지 정하지 않았으므로 줄이거나 초과 금액으로 확정하지 않는다(#31 범위 결정).
				pending.add("service " + service.id() + " fixed benefit " + raw.wholeWon().value()
						+ " exceeds order amount " + order.amount().value());
			}
			if (remainingWon.isPresent() && raw.wholeWon().compareTo(remainingWon.get()) > 0) {
				// 정액을 일부만 지급한다는 근거가 없다(D-18 K1).
				pending.add("service " + service.id() + " fixed benefit " + raw.wholeWon().value()
						+ " exceeds the remaining limit");
			}
			if (!pending.isEmpty()) {
				return needsConfirmation(service, pending, Optional.empty());
			}
		}
		// 원 미만이 실제로 생겼을 때만 원 미만 처리 조건이 필요하다(ConditionalTerm 계약).
		TriggerEvidence fractional = raw.hasFractionalWon() ? TriggerEvidence.PRESENT : TriggerEvidence.CONFIRMED_ABSENT;
		for (ConditionalTerm term : service.unresolvedConditionalTerms()) {
			if (term.trigger() == TermTrigger.FRACTIONAL_WON_RESULT && !term.mayBeDisregarded(fractional)) {
				pending.add(term.name() + ": " + term.reason());
			}
		}
		if (!pending.isEmpty()) {
			return needsConfirmation(service, pending, Optional.empty());
		}
		Won amount = raw.hasFractionalWon() ? raw.round(service.fractionalWon().resolvedValue()) : raw.wholeWon();
		return new ServiceCandidate(service.id(), BenefitAmount.confirmed(amount.value()), Set.of(),
				amount.isPositive() ? remainingWon : Optional.empty(), Optional.empty(), raw.hasFractionalWon());
	}

	/**
	 * 실적·사용액과 무관하게 주문만으로 확인되는 미충족. 포함 관계가 확인되지 않은 업종은 대상 불일치로 보지 않는다(D-18 K1).
	 *
	 * @param service 규칙의 서비스
	 * @param order 현재 주문
	 * @return 변경 가능한 미충족 집합
	 */
	static Set<ApplicationStatus> performanceIndependentUnmet(BenefitService service, Order order) {
		Set<ApplicationStatus> unmet = EnumSet.noneOf(ApplicationStatus.class);
		if (ServiceConditions.isExcludedClass(service, order.merchantClass())) {
			unmet.add(ApplicationStatus.NOT_APPLICABLE_EXCLUDED);
		}
		if (isConfirmedOutsideTarget(service, order)) {
			unmet.add(ApplicationStatus.NOT_APPLICABLE_CATEGORY);
		}
		if (!ServiceConditions.acceptsPaymentMethod(service, order.paymentMethod())) {
			unmet.add(ApplicationStatus.NOT_APPLICABLE_PAYMENT_METHOD);
		}
		if (!ServiceConditions.meetsMinimumAmount(service, order.amount())) {
			unmet.add(ApplicationStatus.NOT_APPLICABLE_MIN_AMOUNT);
		}
		return unmet;
	}

	/**
	 * 주문 업종이 대상이 아님이 확인되면 참. 포함 관계가 확인되지 않은 코드는 불일치로 보지 않는다.
	 */
	static boolean isConfirmedOutsideTarget(BenefitService service, Order order) {
		return !ServiceConditions.isTargetClass(service, order.merchantClass())
				&& !service.details().inclusionUnconfirmedFor(order.merchantClass());
	}

	private static void requireSupported(BenefitService service, ServiceLimits limits) {
		if (service.requiresSelection()) {
			throw new IllegalArgumentException(
					"service " + service.id() + " requires selection, which the candidate evaluator does not support");
		}
		if (service.limitBuckets().isEmpty() != limits.chain().isEmpty()) {
			throw new IllegalArgumentException("service " + service.id() + " limit buckets do not match the given limit chain");
		}
	}

	private static boolean isHighestTier(BenefitService service, ServiceTier tier) {
		return service.tiers().get(service.tiers().size() - 1).equals(tier);
	}

	private static ServiceCandidate needsConfirmation(BenefitService service, List<String> reasons,
			Optional<LimitReason> limitReason) {
		return new ServiceCandidate(service.id(), BenefitAmount.needsConfirmation(String.join("; ", reasons)), Set.of(),
				Optional.empty(), limitReason, false);
	}

}
