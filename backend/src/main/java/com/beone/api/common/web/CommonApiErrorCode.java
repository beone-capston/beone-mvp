package com.beone.api.common.web;

import org.springframework.http.HttpStatus;

/**
 * 공통 예외 처리기가 사용하는 오류 코드.
 *
 * <p>INVALID_REQUEST·INTERNAL_SERVER_ERROR는 따로 열거하지 않은 프레임워크 4xx·5xx에도 쓰이므로
 * 실제 응답 상태가 {@link #status()}와 다를 수 있다(docs/api.md).
 */
public enum CommonApiErrorCode implements ApiErrorCode {

	INVALID_REQUEST("INVALID_REQUEST", HttpStatus.BAD_REQUEST, "요청 값을 확인해 주세요."),
	NOT_FOUND("NOT_FOUND", HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
	METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
	UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 콘텐츠 형식입니다."),
	INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "요청 처리 중 오류가 발생했습니다.");

	private final String code;

	private final HttpStatus status;

	private final String message;

	CommonApiErrorCode(String code, HttpStatus status, String message) {
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
