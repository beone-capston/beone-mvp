package com.beone.api.auth.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * {@code POST /api/v1/auth/email-verifications} 요청 본문(docs/api/auth.md). 정의하지 않은 필드는 400이다.
 *
 * @param token 필수. 43자 Base64URL 이메일 인증 토큰
 */
public record EmailVerificationRequest(@NotNull(message = "필수 값입니다.") @Pattern(regexp = "[A-Za-z0-9_-]{43}",
		message = "43자 Base64URL 문자열이어야 합니다.") String token) implements UnknownFieldRejecting {

	@Override
	public String toString() {
		return "EmailVerificationRequest[token=<hidden>]";
	}

}
