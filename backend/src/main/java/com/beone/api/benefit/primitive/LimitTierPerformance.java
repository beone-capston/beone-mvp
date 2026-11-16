package com.beone.api.benefit.primitive;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

import com.beone.api.benefit.input.CardMonthlyState;
import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.rule.BenefitService;
import com.beone.api.benefit.rule.CardRuleVersion;
import com.beone.api.benefit.state.StateValue;

/**
 * 서비스 체인의 한도 구간을 고를 실적. 주문 뒤 남은 한도 표시(D-22)에 쓴다.
 *
 * <p>
 * 취합기와 같은 신규 유예 판정을 따른다. 유예가 없으면 입력 실적, 유예 중이면 간주 실적이다. 실적을 모르거나 음수이거나, 유예 정의가
 * 미확정이거나 유예 적용을 정할 수 없으면 구간을 단정하지 않고 비운다. 유예 정의 미확정에서 취합기가 최상위 구간으로 금액을 확정한 경우에도 여기서는
 * 비우므로, 남은 한도는 실제보다 덜 알려질 수는 있어도 틀린 값이 되지 않는다.
 */
public final class LimitTierPerformance {

	private LimitTierPerformance() {
	}

	/**
	 * @param rule 서비스가 속한 규칙
	 * @param service 한도 체인을 볼 서비스
	 * @param state 같은 카드의 월간 상태. 기간 판단을 마친 값이다
	 * @param registeredAt 카드 사용 등록일
	 * @param orderDate 주문일
	 * @return 한도 구간을 고를 실적. 단정할 수 없으면 비어 있다
	 */
	public static Optional<Won> of(CardRuleVersion rule, BenefitService service, CardMonthlyState state,
			StateValue<LocalDate> registeredAt, LocalDate orderDate) {
		Objects.requireNonNull(rule, "rule");
		Objects.requireNonNull(service, "service");
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(registeredAt, "registeredAt");
		Objects.requireNonNull(orderDate, "orderDate");
		Optional<Won> performance = state.priorPerformance().filter(StateValue::isKnown).map(StateValue::knownValue);
		if (performance.isEmpty() || performance.get().isNegative()) {
			return Optional.empty();
		}
		return switch (NewCardGraceJudge.judge(rule, service, registeredAt, orderDate, performance.get())) {
			case NewCardGraceJudge.Judgement.Absent absent -> performance;
			case NewCardGraceJudge.Judgement.Deemed deemed -> Optional.of(deemed.performance());
			case NewCardGraceJudge.Judgement.DefinitionUnresolved unresolved -> Optional.empty();
			case NewCardGraceJudge.Judgement.Pending pending -> Optional.empty();
		};
	}

}
