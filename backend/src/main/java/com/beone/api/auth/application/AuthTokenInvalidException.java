package com.beone.api.auth.application;

/**
 * 토큰이 없거나 만료·사용됨·목적 불일치다. 400 {@code AUTH_TOKEN_INVALID}로 답하며 이유를 구분하지 않는다.
 */
public class AuthTokenInvalidException extends RuntimeException {

	public AuthTokenInvalidException() {
		super("auth token invalid");
	}

}
