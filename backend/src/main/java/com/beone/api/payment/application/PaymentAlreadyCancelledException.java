package com.beone.api.payment.application;

/**
 * 이미 전액 취소된 결제(409 PAYMENT_ALREADY_CANCELLED).
 */
public class PaymentAlreadyCancelledException extends RuntimeException {

	public PaymentAlreadyCancelledException() {
		super(null, null, false, false);
	}

}
