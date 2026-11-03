package com.beone.api.benefit.rule;

/**
 * 거래가 월 한도를 차감하는 순서(D-03). 매입순서를 모를 때 승인순서로 대신하지 않는다.
 */
public enum LimitDeductionOrder {

	/** 승인 순서. */
	APPROVAL_ORDER,

	/** 전표 매입 순서(01664, 01914). */
	PURCHASE_ORDER

}
