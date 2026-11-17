package com.beone.api.payment.application;

/**
 * 주문이 이미 모의 승인됐거나 전액 취소됨(409 ORDER_ALREADY_PAID). 다른 키로 같은 주문을 다시 승인한 경우다.
 */
public class OrderNotPayableException extends RuntimeException {

	public OrderNotPayableException() {
		super(null, null, false, false);
	}

}
