package com.beone.api.benefit.rule;

/**
 * 전월 실적을 산정하는 기간.
 * 후보 카드 4종이 쓰는 기간만 지원하고, 그 밖의 기간은 미확정 조건으로 남긴다(PRD FR-02, D-03).
 */
public enum PerformanceWindow {

	/** 직전 달 1일부터 말일까지. */
	PREVIOUS_CALENDAR_MONTH

}
