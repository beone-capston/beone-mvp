package com.beone.api.auth.web;

import com.beone.api.common.web.ApiErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 회원 인증 API의 오류 코드(docs/api.md 업무 오류 코드 「인증」, docs/api/auth.md). 문구는 인증 명세 사본과 같다.
 *
 * <p>U1에서는 CSRF 검사가 쓰는 코드만 둔다. 나머지 인증 코드는 그 코드를 쓰는 API와 함께 추가한다.
 */
enum AuthErrorCode implements ApiErrorCode {

	CSRF_INVALID("CSRF_INVALID", HttpStatus.FORBIDDEN, "요청을 확인할 수 없습니다. 화면을 새로고침한 후 다시 시도해 주세요.");

	private final String code;

	private final HttpStatus status;

	private final String message;

	AuthErrorCode(String code, HttpStatus status, String message) {
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
