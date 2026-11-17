package com.beone.api.payment.application;

/**
 * 없거나, 형식이 틀렸거나, 다른 사용자의 모의 결제(404 SIMULATED_PAYMENT_NOT_FOUND).
 */
public class SimulatedPaymentNotFoundException extends RuntimeException {

	public SimulatedPaymentNotFoundException() {
		super(null, null, false, false);
	}

}
