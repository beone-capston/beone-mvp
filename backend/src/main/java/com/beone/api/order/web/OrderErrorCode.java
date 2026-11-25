package com.beone.api.order.web;

import com.beone.api.common.web.ApiErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 주문 기능의 업무 오류 코드(docs/api.md 업무 오류 코드). 이 작업은 {@code ORDER_NOT_FOUND}만 쓴다.
 */
enum OrderErrorCode implements ApiErrorCode {

	ORDER_NOT_FOUND("ORDER_NOT_FOUND", HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다.");

	private final String code;

	private final HttpStatus status;

	private final String message;

	OrderErrorCode(String code, HttpStatus status, String message) {
		this.code = code;
		this.status = status;
		this.message = message;
	}

	@Override
	public String code() {
		return this.code;
	}

	@Override
	public HttpStatus status() {
		return this.status;
	}

	@Override
	public String message() {
		return this.message;
	}

}
