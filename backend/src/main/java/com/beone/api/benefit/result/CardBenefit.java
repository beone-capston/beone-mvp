package com.beone.api.benefit.result;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.card.LimitBucketId;
import com.beone.api.benefit.card.ServiceId;
import com.beone.api.benefit.money.BenefitAmount;
import com.beone.api.benefit.rule.RuleVersionRef;

/**
 * 후보 카드 한 장의 결과.
 *
 * @param appliedRule 사용한 규칙 버전. 쓸 수 있는 규칙이 없을 때만 비어 있다
 * @param status 카드 단위 적용 상태
 * @param currentOrderBenefit 현재 주문의 혜택
 * @param monthlyTotal 현재 주문을 이 카드로 결제할 때의 월간 총예상 혜택
 * @param services 현재 주문의 서비스별 혜택
 * @param remainingLimits 현재 주문 뒤 한도 묶음별 잔여 한도
 */
public record CardBenefit(CardId cardId, Optional<RuleVersionRef> appliedRule, ApplicationStatus status,
		BenefitAmount currentOrderBenefit, BenefitAmount monthlyTotal, List<ServiceBenefit> services,
		Map<LimitBucketId, BenefitAmount> remainingLimits) {

	public CardBenefit {
		Objects.requireNonNull(cardId, "cardId");
		Objects.requireNonNull(appliedRule, "appliedRule");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(currentOrderBenefit, "currentOrderBenefit");
		Objects.requireNonNull(monthlyTotal, "monthlyTotal");
		services = List.copyOf(services);
		remainingLimits = Map.copyOf(remainingLimits);
		if (!status.isConsistentWith(currentOrderBenefit)) {
			throw new IllegalArgumentException("card " + cardId + " status " + status + " does not match amount "
					+ currentOrderBenefit);
		}
		boolean ruleUnavailable = status == ApplicationStatus.RULE_EXPIRED || status == ApplicationStatus.NO_VERIFIED_RULE;
		if (ruleUnavailable && (appliedRule.isPresent() || !services.isEmpty() || !remainingLimits.isEmpty())) {
			throw new IllegalArgumentException(
					"card " + cardId + " has no usable rule but reports a rule, services or remaining limits");
		}
		if (ruleUnavailable && monthlyTotal.isConfirmed()) {
			throw new IllegalArgumentException("card " + cardId + " has no usable rule, so its monthly total is unconfirmed");
		}
		if (!ruleUnavailable && appliedRule.isEmpty()) {
			throw new IllegalArgumentException("card " + cardId + " result must name the rule version it used");
		}
		appliedRule.ifPresent(rule -> {
			if (!rule.cardId().equals(cardId)) {
				throw new IllegalArgumentException("card " + cardId + " result names a rule of card " + rule.cardId());
			}
		});
		Set<ServiceId> seen = new HashSet<>();
		for (ServiceBenefit service : services) {
			if (!seen.add(service.serviceId())) {
				throw new IllegalArgumentException("card " + cardId + " reports service " + service.serviceId() + " twice");
			}
		}
	}

}
