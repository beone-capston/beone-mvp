package com.beone.api.auth.web;

/**
 * 계정이 없거나 비밀번호가 틀렸다(둘을 구분하지 않음). 401 {@code INVALID_CREDENTIALS}로 답한다.
 */
class InvalidCredentialsException extends RuntimeException {

	InvalidCredentialsException() {
		super("invalid credentials");
	}

}
