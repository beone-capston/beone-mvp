package com.beone.api.auth.web;

/**
 * 비밀번호는 맞지만 이메일 인증을 마치지 않았다. 403 {@code EMAIL_NOT_VERIFIED}로 답한다.
 */
class EmailNotVerifiedException extends RuntimeException {

	EmailNotVerifiedException() {
		super("email not verified");
	}

}
