package com.beone.api.order.application;

/**
 * 주문이 없거나 다른 사용자의 주문일 때 던진다. 존재 여부를 구분하지 않고 입력값을 메시지에 담지 않는다.
 */
public class OrderNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public OrderNotFoundException() {
		super(null, null, false, false);
	}

}
