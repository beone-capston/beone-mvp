package com.beone.api.benefit.input;

/**
 * 결제 전에 정하는 카드 선택 방식(PRD §4.1).
 */
public enum SelectionMode {

	/** 엔진이 월간 총혜택이 가장 큰 카드를 고른다. */
	AUTO_RECOMMEND,

	/** 대표 카드를 항상 사용한다. 엔진은 최적 카드와의 차이를 함께 보여 준다. */
	FIXED_REPRESENTATIVE

}
