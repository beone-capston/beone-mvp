package com.beone.api.monthlystate.web;

import com.beone.api.common.web.ApiErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 월간 상태 API가 쓰는 업무 오류 코드(docs/api.md 업무 오류 코드 표의 보유 카드·월간 상태(2차) 행). 표에 있는 코드만 쓴다.
 *
 * <p>{@code SOURCE_CONFLICT_CHANGED}는 충돌이 생기는 경로가 없어 아직 쓰지 않는다(관리자 판단 P10).
 */
enum MonthlyStateErrorCode implements ApiErrorCode {

	USER_CARD_NOT_FOUND("USER_CARD_NOT_FOUND", HttpStatus.NOT_FOUND, "보유 카드를 찾을 수 없습니다."),

	USER_CARD_MAPPING_REQUIRED("USER_CARD_MAPPING_REQUIRED", HttpStatus.CONFLICT, "카드 상품 매핑을 먼저 확인해 주세요."),

	SOURCE_CONFLICT_NOT_FOUND("SOURCE_CONFLICT_NOT_FOUND", HttpStatus.CONFLICT, "출처 충돌이 없는 항목입니다.");

	private final String code;

	private final HttpStatus status;

	private final String message;

	MonthlyStateErrorCode(String code, HttpStatus status, String message) {
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
