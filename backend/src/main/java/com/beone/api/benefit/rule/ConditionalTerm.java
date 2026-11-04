package com.beone.api.benefit.rule;

import java.util.Objects;

/**
 * 영향 상황이 생길 때만 결과를 바꾸는 미확정 규칙 조건.
 *
 * <p>
 * 계약: 근거가 {@link TriggerEvidence#CONFIRMED_ABSENT}일 때만 무시할 수 있다.
 * {@code PRESENT}나 {@code UNDETERMINED}이면 해당 혜택은 {@code 확인 필요}로 남는다(D-03, D-05).
 * 근거가 없으면 {@code UNDETERMINED}로 보며, 영향이 없다고 보지 않는다.
 *
 * @param name 조건 이름. 서비스 조건은 서비스 ID를 앞에 붙인다(예: {@code BASE.fractionalWon})
 * @param reason 미확정 사유
 * @param trigger 조건이 결과에 영향을 주는 상황
 */
public record ConditionalTerm(String name, String reason, TermTrigger trigger) {

	public ConditionalTerm {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("term name is required");
		}
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("reason is required");
		}
		Objects.requireNonNull(trigger, "trigger");
	}

	public boolean mayBeDisregarded(TriggerEvidence evidence) {
		Objects.requireNonNull(evidence, "evidence");
		return evidence == TriggerEvidence.CONFIRMED_ABSENT;
	}

}
