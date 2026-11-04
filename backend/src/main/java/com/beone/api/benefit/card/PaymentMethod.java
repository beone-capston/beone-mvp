package com.beone.api.benefit.card;

/**
 * 주문·예정 소비·원장 거래의 확인된 결제 방식.
 */
public enum PaymentMethod {

	/** 카드로 직접 결제. */
	DIRECT,

	/** 카드 약관이 대상으로 정한 간편결제(09174 Check). */
	ELIGIBLE_SIMPLE_PAY,

	/**
	 * 이 카드로 등록한 자동이체.
	 * 평가 fixture는 D-10의 자동이체 등록(사용자 확인 상태)을 이 값으로 단순화했다.
	 */
	AUTOPAY

}
