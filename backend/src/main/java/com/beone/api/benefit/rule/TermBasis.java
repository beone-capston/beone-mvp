package com.beone.api.benefit.rule;

import java.util.Objects;

/**
 * 규칙 값이 문서 대조가 아닌 다른 근거로 정해졌음을 표시한다. 결과에 "약관 근거 없음"을 표시할 때 쓴다(D-03).
 *
 * @param kind 근거의 종류
 * @param decision 근거 결정 번호(예: D-03)
 */
public record TermBasis(Kind kind, String decision) {

	public TermBasis {
		Objects.requireNonNull(kind, "kind");
		if (decision == null || decision.isBlank()) {
			throw new IllegalArgumentException("term basis needs a decision id");
		}
	}

	/** 근거의 종류. */
	public enum Kind {

		/** 약관 근거 없이 사용자가 결정했다. */
		USER_DECISION

	}

}
