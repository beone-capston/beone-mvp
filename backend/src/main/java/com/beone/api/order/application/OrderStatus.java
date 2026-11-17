package com.beone.api.order.application;

/**
 * 주문 상태. 이름이 곧 응답 {@code status} 값이다.
 *
 * <p>전이는 {@code CREATED → PAID → CANCELLED} 한 방향뿐이다. 모의 승인(payment)이 {@code PAID}로, 전액 취소가 {@code CANCELLED}로
 * 바꾼다. 실패한 모의 승인({@code FAILED})과 부분취소는 상태를 바꾸지 않는다(docs/api/orders.md, D-23-4).
 */
public enum OrderStatus {

	/** 만들어졌고 아직 모의 승인 전. */
	CREATED,

	/** 모의 승인됐다. 부분취소 뒤에도 이 상태다. */
	PAID,

	/** 모의 승인을 전액 취소했다. 다시 승인하지 않는다(취소의 취소 없음, D-17). */
	CANCELLED

}
