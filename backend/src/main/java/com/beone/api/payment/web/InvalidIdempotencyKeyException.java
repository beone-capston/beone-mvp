package com.beone.api.payment.web;

/**
 * Idempotency-Key가 없거나 비었거나 255자를 넘음(400 INVALID_REQUEST).
 */
class InvalidIdempotencyKeyException extends RuntimeException {

	InvalidIdempotencyKeyException() {
		super(null, null, false, false);
	}

}
