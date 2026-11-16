package com.beone.api.order.application;

/**
 * 주문 상태. 이름이 곧 응답 {@code status} 값이다.
 *
 * <p>계약의 {@code PAID}·{@code CANCELLED}는 모의 결제 작업이 상태 전이와 함께 추가한다. 이 작업은 생성 상태만 만든다.
 */
public enum OrderStatus {

	/** 만들어졌고 아직 모의 승인 전. */
	CREATED

}
