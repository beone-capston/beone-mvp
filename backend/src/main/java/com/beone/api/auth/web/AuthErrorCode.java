package com.beone.api.auth.web;

import com.beone.api.common.web.ApiErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 회원 인증 API의 오류 코드(docs/api.md 업무 오류 코드 「인증」, docs/api/auth.md). 문구는 인증 명세 사본과 같다.
 *
 * <p>CSRF 검사(U1), 가입·이메일 인증(U2), 로그인·세션 조회(U3)가 쓰는 코드다. 문구는 사본과 같다.
 */
enum AuthErrorCode implements ApiErrorCode {

	UNAUTHENTICATED("UNAUTHENTICATED", HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),

	INVALID_CREDENTIALS("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호를 확인해 주세요."),

	EMAIL_NOT_VERIFIED("EMAIL_NOT_VERIFIED", HttpStatus.FORBIDDEN, "이메일 인증을 완료해 주세요."),

	CSRF_INVALID("CSRF_INVALID", HttpStatus.FORBIDDEN, "요청을 확인할 수 없습니다. 화면을 새로고침한 후 다시 시도해 주세요."),

	AUTH_TOKEN_INVALID("AUTH_TOKEN_INVALID", HttpStatus.BAD_REQUEST, "링크가 유효하지 않거나 만료되었습니다. 다시 요청해 주세요."),

	RATE_LIMITED("RATE_LIMITED", HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");

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
