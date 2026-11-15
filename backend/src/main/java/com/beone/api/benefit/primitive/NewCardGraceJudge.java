package com.beone.api.benefit.primitive;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.rule.BenefitService;
import com.beone.api.benefit.rule.CardRuleVersion;
import com.beone.api.benefit.rule.GracePartialPerformance;
import com.beone.api.benefit.rule.LimitBucket;
import com.beone.api.benefit.rule.LimitTier;
import com.beone.api.benefit.rule.NewCardGrace;
import com.beone.api.benefit.rule.RuleTerm;
import com.beone.api.benefit.rule.ServiceTier;
import com.beone.api.benefit.state.StateValue;

/**
 * 등록일과 주문일로 신규 유예를 판정하고, 유예 중 입력 실적 P에서 구간을 고를 실적을 정한다(D-20). 카드 ID로 나누지 않고 규칙 데이터만 쓴다.
 *
 * <ul>
 * <li>P가 0원이거나 간주 실적 이상 다음 구간 기준 미만이면 간주 구간이다.</li>
 * <li>0원 초과 간주 실적 미만은 {@link NewCardGrace#partialPerformance()}를 따른다.</li>
 * <li>다음 구간 기준 이상이면 유예 혜택과 일반 구간 중 무엇이 적용되는지 근거가 없어 확인 필요다.</li>
 * </ul>
 * P가 없거나 {@code UNKNOWN}인 경우는 호출자가 먼저 확인 필요로 둔다.
 */
final class NewCardGraceJudge {

	private NewCardGraceJudge() {
	}

	/**
	 * 판정 결과.
	 */
	sealed interface Judgement {

		/** 유예가 적용되지 않음이 확인됐다. 입력 실적으로 판정한다. */
		record Absent() implements Judgement {
		}

		/** 규칙의 유예 정의가 미확정이다. 입력 실적으로 올라갈 수 있는 구간만 확인 필요가 된다. */
		record DefinitionUnresolved() implements Judgement {
		}

		/** 유예 중이다. 이 실적으로 구간을 고른다. */
		record Deemed(Won performance) implements Judgement {
		}

		/** 유예 적용 여부나 구간을 정할 수 없다. */
		record Pending(String reason) implements Judgement {
		}

	}

	static Judgement judge(CardRuleVersion rule, BenefitService service, StateValue<LocalDate> registeredAt,
			LocalDate orderDate, Won performance) {
		RuleTerm<Optional<NewCardGrace>> term = rule.policy().newCardGrace();
		if (!term.isResolved()) {
			return new Judgement.DefinitionUnresolved();
		}
		Optional<NewCardGrace> definition = term.resolvedValue();
		if (definition.isEmpty() || !definition.get().services().contains(service.id())) {
			return new Judgement.Absent();
		}
		NewCardGrace grace = definition.get();
		if (!registeredAt.isKnown()) {
			return new Judgement.Pending("service " + service.id() + " has new card grace but the registration date is UNKNOWN");
		}
		if (registeredAt.knownValue().isAfter(orderDate)) {
			return new Judgement.Pending("service " + service.id() + " has new card grace but the card is registered after the order");
		}
		if (!grace.covers(registeredAt.knownValue(), orderDate)) {
			return new Judgement.Absent();
		}
		return withinGrace(rule, service, grace, performance);
	}

	private static Judgement withinGrace(CardRuleVersion rule, BenefitService service, NewCardGrace grace, Won performance) {
		Won deemed = grace.deemedPerformance();
		if (performance.isPositive() && performance.compareTo(deemed) < 0
				&& grace.partialPerformance() == GracePartialPerformance.NEEDS_CONFIRMATION) {
			return new Judgement.Pending("service " + service.id()
					+ " is within new card grace with partial performance below the deemed tier");
		}
		Optional<Won> nextTier = nextThreshold(rule.limitChain(service), service, deemed);
		if (nextTier.isPresent() && performance.compareTo(nextTier.get()) >= 0) {
			return new Judgement.Pending("service " + service.id()
					+ " is within new card grace with performance at or above the next tier");
		}
		return new Judgement.Deemed(deemed);
	}

	// 계산 구간과 한도 구간 기준을 함께 보아 간주 실적 바로 위 기준을 찾는다.
	private static Optional<Won> nextThreshold(List<LimitBucket> chain, BenefitService service, Won deemed) {
		Stream<Won> serviceThresholds = service.tiers().stream().map(ServiceTier::minimumPerformance);
		Stream<Won> limitThresholds = chain.stream()
			.flatMap(bucket -> bucket.tiers().stream())
			.map(LimitTier::minimumPerformance);
		return Stream.concat(serviceThresholds, limitThresholds)
			.filter(threshold -> threshold.compareTo(deemed) > 0)
			.min(Won::compareTo);
	}

}
