package com.beone.api.codef.application;

/**
 * 동기화 조회 대상. 부분 실패 항목의 {@code target} 값과 같다(docs/api/codef.md, 샌드박스 확인 전 제안값).
 */
public enum CodefSyncTarget {

	/** 보유카드. */
	HELD_CARDS,

	/** 승인내역. */
	APPROVALS,

	/** 실적조회. */
	PERFORMANCES

}
