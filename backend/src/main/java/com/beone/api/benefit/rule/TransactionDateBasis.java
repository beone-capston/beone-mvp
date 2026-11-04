package com.beone.api.benefit.rule;

/**
 * 규칙이 거래를 셀 때 쓰는 날짜. 같은 달이라는 이유로 서로 다른 기준일을 합치지 않는다(D-03).
 */
public enum TransactionDateBasis {

	/** 승인일. */
	APPROVAL,

	/** 이용일(예: 실제 탑승일). */
	USAGE,

	/** 카드 이용내역서 기재일. */
	STATEMENT_ENTRY,

	/** 매입일. */
	PURCHASE

}
