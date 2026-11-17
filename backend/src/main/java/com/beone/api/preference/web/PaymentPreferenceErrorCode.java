package com.beone.api.preference.web;

import com.beone.api.common.web.ApiErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 결제 전 설정 API의 업무 오류 코드(docs/api.md 업무 오류 코드). {@code USER_CARD_NOT_FOUND}는 보유 카드 기능과 같은 코드·문구다.
 * {@code REPRESENTATIVE_CARD_IN_USE}는 보유 카드 API가 응답하므로 card 패키지에 있다.
 */
enum PaymentPreferenceErrorCode implements ApiErrorCode {

	USER_CARD_NOT_FOUND("USER_CARD_NOT_FOUND", HttpStatus.NOT_FOUND, "보유 카드를 찾을 수 없습니다."),

	REPRESENTATIVE_CARD_NOT_ELIGIBLE("REPRESENTATIVE_CARD_NOT_ELIGIBLE", HttpStatus.CONFLICT,
			"추천 비교 대상인 카드만 대표 카드로 고를 수 있습니다.");

	private final String code;

	private final HttpStatus status;

	private final String message;

	PaymentPreferenceErrorCode(String code, HttpStatus status, String message) {
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
