package com.beone.api.payment.application;

/**
 * 취소 금액이 남은 취소 가능 금액보다 큼(409 CANCELLATION_AMOUNT_EXCEEDED). 실패한 결제는 취소 가능 금액이 0이다.
 */
public class CancellationAmountExceededException extends RuntimeException {

	public CancellationAmountExceededException() {
		super(null, null, false, false);
	}

}
