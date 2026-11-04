package com.beone.api.benefit.rule;

/**
 * 월 혜택 한도가 적용되는 기간. 다른 기간은 미확정 조건으로 남긴다(PRD FR-02, D-03).
 */
public enum LimitPeriod {

	/** 매월 1일부터 말일까지. 이월하지 않는다. */
	CALENDAR_MONTH

}
