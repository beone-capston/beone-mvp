package com.beone.api.benefit.rule;

import java.util.Objects;

import com.beone.api.benefit.card.CardId;

/**
 * 카드의 규칙 버전 하나를 가리킨다. 결과와 산출 상태값에서 사용한다.
 */
public record RuleVersionRef(CardId cardId, String ruleVersion) {

	public RuleVersionRef {
		Objects.requireNonNull(cardId, "cardId");
		if (ruleVersion == null || ruleVersion.isBlank()) {
			throw new IllegalArgumentException("rule version is required");
		}
	}

}
