package com.beone.api.payment.application;

/**
 * 같은 Idempotency-Key를 다른 지문의 요청에 다시 씀(409 IDEMPOTENCY_KEY_REUSED).
 */
public class IdempotencyKeyReusedException extends RuntimeException {

	public IdempotencyKeyReusedException() {
		super(null, null, false, false);
	}

}
