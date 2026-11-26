package com.beone.api.payment.web;

/**
 * {@code local}·{@code demo} 밖에서 {@code simulateFailure=true}를 보냄(400 INVALID_REQUEST, docs/api/mock-payments.md).
 */
class SimulateFailureNotAllowedException extends RuntimeException {

	SimulateFailureNotAllowedException() {
		super(null, null, false, false);
	}

}
