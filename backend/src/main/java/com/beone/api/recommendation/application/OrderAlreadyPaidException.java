package com.beone.api.recommendation.application;

/**
 * 이미 모의 승인된 주문으로 추천을 계산하려 할 때 던진다. 웹 계층이 409 {@code ORDER_ALREADY_PAID}로 바꾼다.
 *
 * <p>주문 상태에 아직 {@code CREATED}만 있어 지금은 생기지 않는다. 모의 결제(B3)가 상태를 더하면 동작한다(결정 27 B2-1).
 */
public class OrderAlreadyPaidException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public OrderAlreadyPaidException() {
		super(null, null, false, false);
	}

}
