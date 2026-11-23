package com.beone.api.recommendation.web;

import com.beone.api.common.web.ApiErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 추천 API가 쓰는 업무 오류 코드(docs/api.md 업무 오류 코드 표의 주문·추천 행). 표에 있는 코드만 쓴다.
 */
enum RecommendationErrorCode implements ApiErrorCode {

	ORDER_NOT_FOUND("ORDER_NOT_FOUND", HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),

	ORDER_ALREADY_PAID("ORDER_ALREADY_PAID", HttpStatus.CONFLICT, "이미 모의 승인된 주문입니다."),

	RECOMMENDATION_NOT_FOUND("RECOMMENDATION_NOT_FOUND", HttpStatus.NOT_FOUND, "추천 결과를 찾을 수 없습니다.");

	private final String code;

	private final HttpStatus status;

	private final String message;

	RecommendationErrorCode(String code, HttpStatus status, String message) {
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
