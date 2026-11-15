package com.beone.api.benefit.engine;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 비교에서 빠진 카드와 이유(D-15, D-18 K3, D-19). 금액은 항상 미확정이며 0원으로 바꾸지 않는다.
 *
 * @param userCardId 보유 카드 ID
 * @param cardProductId 규칙 카드 ID. 규칙 카드를 모르면 null
 * @param reason 제외 사유
 * @param detail 설명
 * @param candidateCardProductId {@code MAPPING_UNCONFIRMED}일 때 확인할 수 있는 후보
 * @param monthlyTotalBenefit 항상 미확정
 * @param applicationStatus 계산한 카드의 상태. 계산하지 않았으면 null
 * @param appliedRule 규칙으로 계산했으나 금액이 미확정인 카드의 규칙. 그 밖에는 null
 * @param pendingReasons 금액을 확정하지 못한 사유
 */
public record ExcludedCard(String userCardId, String cardProductId, String reason, String detail,
		String candidateCardProductId, BenefitAmountOutput monthlyTotalBenefit, String applicationStatus,
		AppliedRuleOutput appliedRule, List<PendingReasonOutput> pendingReasons) {

	private static final Set<String> REASONS = Set.of("RULE_UNAVAILABLE", "MAPPING_UNCONFIRMED", "NEEDS_CONFIRMATION",
			"UNSUPPORTED", "NO_SUPPORTED_RULE", "USER_EXCLUDED");

	public ExcludedCard {
		EngineChecks.requireToken(userCardId, "userCardId");
		EngineChecks.requireOptionalToken(cardProductId, "cardProductId");
		EngineChecks.requireOneOf(REASONS, reason, "reason");
		EngineChecks.requireText(detail, "detail");
		EngineChecks.requireOptionalToken(candidateCardProductId, "candidateCardProductId");
		if (candidateCardProductId != null && !"MAPPING_UNCONFIRMED".equals(reason)) {
			throw new IllegalArgumentException("candidateCardProductId is only for MAPPING_UNCONFIRMED");
		}
		Objects.requireNonNull(monthlyTotalBenefit, "monthlyTotalBenefit");
		if (monthlyTotalBenefit.isConfirmed()) {
			throw new IllegalArgumentException("an excluded card has no confirmed benefit");
		}
		pendingReasons = EngineChecks.copy(pendingReasons, "pendingReasons");
		switch (reason) {
			case "RULE_UNAVAILABLE" -> {
				EngineChecks.requireOneOf(Set.of("RULE_EXPIRED", "NO_VERIFIED_RULE"), applicationStatus, "applicationStatus");
				if (cardProductId == null || appliedRule != null) {
					throw new IllegalArgumentException("a card without a rule names its product and no rule");
				}
			}
			case "NEEDS_CONFIRMATION", "UNSUPPORTED" -> {
				EngineChecks.requireOneOf(Set.of("NEEDS_CONFIRMATION"), applicationStatus, "applicationStatus");
				if (cardProductId == null || appliedRule == null) {
					throw new IllegalArgumentException("a calculated card names its product and rule");
				}
			}
			default -> {
				if (applicationStatus != null || appliedRule != null) {
					throw new IllegalArgumentException("a card that was not calculated has no status or rule");
				}
			}
		}
	}

}
