package com.beone.api.benefit.rule;

/**
 * 계산된 혜택의 원 미만 금액 처리 방식.
 * 약관에 근거가 있는 카드는 아직 없다. 4종의 절사는 D-03 사용자 결정이며, 이 사실은 {@link TermBasis}로 표시한다.
 */
public enum FractionalWonPolicy {

	/** 원 미만 절사. */
	TRUNCATE,

	/** 원 미만 반올림. */
	ROUND_HALF_UP

}
