package com.beone.api.ledger.web;

import com.beone.api.common.web.ApiErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 원장 조회 API가 쓰는 업무 오류 코드(docs/api.md 업무 오류 코드 표의 보유 카드 행). 표에 있는 코드만 쓴다.
 */
enum LedgerErrorCode implements ApiErrorCode {

	USER_CARD_NOT_FOUND("USER_CARD_NOT_FOUND", HttpStatus.NOT_FOUND, "보유 카드를 찾을 수 없습니다.");

	private final String code;

	private final HttpStatus status;

	private final String message;

	LedgerErrorCode(String code, HttpStatus status, String message) {
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
