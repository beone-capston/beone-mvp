package com.beone.api.card.web;

import com.beone.api.common.web.ApiErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 카드 상품 API의 기능별 오류 코드(docs/api.md 업무 오류 코드 표의 상품 행).
 */
enum CardProductErrorCode implements ApiErrorCode {

	CARD_PRODUCT_NOT_FOUND("CARD_PRODUCT_NOT_FOUND", HttpStatus.NOT_FOUND, "카드 상품을 찾을 수 없습니다.");

	private final String code;

	private final HttpStatus status;

	private final String message;

	CardProductErrorCode(String code, HttpStatus status, String message) {
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
