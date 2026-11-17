package com.beone.api.card.web;

import com.beone.api.common.web.ApiErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 보유 카드 기능의 업무 오류 코드(docs/api.md 업무 오류 코드). 표에 있는 코드만 쓴다. {@code USER_CARD_MAPPING_REQUIRED}는 이
 * 작업의 API가 쓰지 않는다.
 */
enum UserCardErrorCode implements ApiErrorCode {

	USER_CARD_NOT_FOUND("USER_CARD_NOT_FOUND", HttpStatus.NOT_FOUND, "보유 카드를 찾을 수 없습니다."),

	USER_CARD_DUPLICATED("USER_CARD_DUPLICATED", HttpStatus.CONFLICT, "같은 상품의 카드가 이미 등록되어 있습니다."),

	PORTFOLIO_READ_ONLY("PORTFOLIO_READ_ONLY", HttpStatus.CONFLICT, "가상 포트폴리오의 보유 카드는 바꿀 수 없습니다."),

	REPRESENTATIVE_CARD_IN_USE("REPRESENTATIVE_CARD_IN_USE", HttpStatus.CONFLICT, "결제 전 설정의 대표 카드입니다. 먼저 설정을 바꿔 주세요.");

	private final String code;

	private final HttpStatus status;

	private final String message;

	UserCardErrorCode(String code, HttpStatus status, String message) {
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
