package com.beone.api.benefit.engine;

/**
 * 대표 카드 고정일 때 월간 최적 카드와의 비교(PRD §4.1).
 *
 * @param representativeUserCardId 대표 카드
 * @param optimalUserCardId 최적 카드. 비교 카드가 없으면 null
 * @param differenceWon 최적 카드와의 차이. 대표 카드 금액이 미확정이면 null
 */
public record FixedComparison(String representativeUserCardId, String optimalUserCardId, Long differenceWon) {

	public FixedComparison {
		EngineChecks.requireToken(representativeUserCardId, "representativeUserCardId");
		EngineChecks.requireOptionalToken(optimalUserCardId, "optimalUserCardId");
		EngineChecks.requireOptionalNonNegative(differenceWon, "differenceWon");
		if (differenceWon != null && optimalUserCardId == null) {
			throw new IllegalArgumentException("a difference needs an optimal card");
		}
	}

}
