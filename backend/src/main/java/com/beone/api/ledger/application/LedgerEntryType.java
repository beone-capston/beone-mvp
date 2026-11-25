package com.beone.api.ledger.application;

/**
 * 원장 행 종류(docs/api/ledger.md {@code entryType}). 금액 부호가 아니라 이 값으로 승인·취소를 구분한다(#45).
 */
public enum LedgerEntryType {

	/** 승인. 금액은 양수다. */
	APPROVAL,

	/** 취소. 원 승인 행에 연결되고 금액은 음수다. */
	CANCELLATION

}
