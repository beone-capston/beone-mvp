package com.beone.api.auth.web;

import jakarta.validation.constraints.NotNull;

/**
 * {@code POST /api/v1/auth/password-reset-requests} 요청 본문(docs/api/auth.md). 정의하지 않은 필드는 400이다.
 *
 * @param email 필수. 이메일 형식, 정규화 뒤 254자 이하
 */
public record PasswordResetRequestRequest(@NotNull(message = "필수 값입니다.") @MemberEmail String email)
		implements UnknownFieldRejecting {

	@Override
	public String toString() {
		return "PasswordResetRequestRequest[email=<hidden>]";
	}

}
