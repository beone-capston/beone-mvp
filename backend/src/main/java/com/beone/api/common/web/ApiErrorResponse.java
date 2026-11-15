package com.beone.api.common.web;

import java.util.List;

/**
 * 실패 응답 본문. {@code errors}는 항상 배열이며 상세가 없으면 빈 배열이다.
 */
public record ApiErrorResponse(String code, String message, List<FieldViolation> errors) {

	public ApiErrorResponse {
		errors = (errors == null) ? List.of() : List.copyOf(errors);
	}

	public static ApiErrorResponse of(ApiErrorCode errorCode) {
		return of(errorCode, List.of());
	}

	public static ApiErrorResponse of(ApiErrorCode errorCode, List<FieldViolation> errors) {
		return new ApiErrorResponse(errorCode.code(), errorCode.message(), errors);
	}

}
