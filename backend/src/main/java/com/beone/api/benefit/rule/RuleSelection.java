package com.beone.api.benefit.rule;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.card.ServiceId;
import com.beone.api.benefit.money.BenefitAmount;
import com.beone.api.benefit.result.ApplicationStatus;

/**
 * 카드 한 장의 판정일 기준 규칙 선택 결과.
 */
public sealed interface RuleSelection {

	CardId cardId();

	/**
	 * 판정일에 유효한 {@code VERIFIED} 규칙을 찾은 경우.
	 * 서비스는 계산할 수 있는 것과 미확정 조건 때문에 {@code 확인 필요}로 남는 것으로 나눈다.
	 *
	 * @param rule 선택된 규칙
	 * @param calculableServices 계산을 막는 미확정 조건이 없는 서비스
	 * @param servicesNeedingConfirmation 서비스별 계산 불가 사유. 이 서비스는 계산하지 않는다
	 * @param unresolvedConditionalTerms 영향 없음이 확인되기 전까지 해당 혜택을 {@code 확인 필요}로 두는 조건({@link ConditionalTerm})
	 */
	record Selected(CardRuleVersion rule, List<BenefitService> calculableServices,
			Map<ServiceId, List<String>> servicesNeedingConfirmation,
			List<ConditionalTerm> unresolvedConditionalTerms) implements RuleSelection {

		public Selected {
			Objects.requireNonNull(rule, "rule");
			calculableServices = List.copyOf(calculableServices);
			servicesNeedingConfirmation = Map.copyOf(servicesNeedingConfirmation);
			unresolvedConditionalTerms = List.copyOf(unresolvedConditionalTerms);
		}

		@Override
		public CardId cardId() {
			return rule.cardId();
		}

	}

	/**
	 * 쓸 수 있는 규칙이 없는 경우. 혜택을 계산하지 않으며 금액은 0원이 아닌 미확정이다.
	 */
	record Unavailable(CardId cardId, ApplicationStatus status, String reason) implements RuleSelection {

		public Unavailable {
			Objects.requireNonNull(cardId, "cardId");
			if (status != ApplicationStatus.RULE_EXPIRED && status != ApplicationStatus.NO_VERIFIED_RULE) {
				throw new IllegalArgumentException("unavailable rule status must be RULE_EXPIRED or NO_VERIFIED_RULE");
			}
			if (reason == null || reason.isBlank()) {
				throw new IllegalArgumentException("reason is required");
			}
		}

		public BenefitAmount amount() {
			return BenefitAmount.needsConfirmation(reason);
		}

	}

}
