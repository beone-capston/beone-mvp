package com.beone.api.benefit.input;

/**
 * 원장 거래의 승인·취소 구분.
 */
public enum LedgerEntryType {

	/** 승인. 금액은 양수다. */
	APPROVAL,

	/** 취소. 날짜는 취소 접수일이고 금액은 음수다. */
	CANCELLATION

}
