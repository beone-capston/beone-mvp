package com.beone.api.auth.web;

/**
 * 탈퇴 비밀번호 재확인 실패. 세션은 유효하므로 401이 아니라 400 {@code INVALID_REQUEST}와 {@code password} 필드 오류로 답한다.
 */
class PasswordConfirmationFailedException extends RuntimeException {

	PasswordConfirmationFailedException() {
		super("password confirmation failed");
	}

}
