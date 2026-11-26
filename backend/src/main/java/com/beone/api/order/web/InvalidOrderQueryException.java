package com.beone.api.order.web;

/**
 * 주문 목록 쿼리 값의 형식·범위 오류. 오류 응답에 쓸 필드 이름만 갖는다(입력 원문은 남기지 않음).
 */
class InvalidOrderQueryException extends RuntimeException {

	private final String field;

	InvalidOrderQueryException(String field) {
		super(null, null, false, false);
		this.field = field;
	}

	String field() {
		return this.field;
	}

}
