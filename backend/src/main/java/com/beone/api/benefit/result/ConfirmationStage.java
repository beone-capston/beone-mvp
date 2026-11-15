package com.beone.api.benefit.result;

/**
 * 서비스 후보 취합(#33)에서 서비스를 {@code 확인 필요}로 둔 단계.
 */
public enum ConfirmationStage {

	/** 규칙 선택 단계에서 미확정 조건으로 계산하지 않기로 한 서비스({@code RuleSelection.Selected}). */
	RULE_SELECTION,

	/** 전월 실적이 입력되지 않았거나 {@code UNKNOWN}이라 후보를 판정하지 않은 서비스. */
	PRIOR_PERFORMANCE,

	/** 후보 판정기가 확인 필요 금액을 돌려준 서비스. */
	CANDIDATE_EVALUATION

}
