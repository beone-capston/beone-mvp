package com.beone.api.auth.web;

/**
 * 인증 POST 요청의 CSRF 토큰이 없거나 세션의 토큰과 다르다. 403 {@code CSRF_INVALID}로 응답한다. 토큰 값은 담지 않는다.
 */
class CsrfInvalidException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	CsrfInvalidException() {
		super("CSRF token is missing or does not match the session");
	}

}
