package com.beone.api.benefit.rule;

/**
 * 신규 카드 실적 유예 기간의 종류(D-03).
 */
public enum GraceSpan {

	/** 등록일부터 다음 달 말일까지(09271, 09174, 01914). */
	UNTIL_END_OF_NEXT_MONTH,

	/** 최초 등록일부터 정해진 일수(01664: 60일). */
	DAYS_FROM_REGISTRATION

}
