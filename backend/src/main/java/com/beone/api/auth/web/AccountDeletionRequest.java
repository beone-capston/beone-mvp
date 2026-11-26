package com.beone.api.auth.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code DELETE /api/v1/me} 요청 본문(docs/api/auth.md). 정의하지 않은 필드는 400이다.
 *
 * <p>비밀번호는 로그인과 같이 빈 값 불가·최대 32자만 보고 원문 그대로 비교한다.
 *
 * @param password 필수. 1~32자. 현재 비밀번호
 */
public record AccountDeletionRequest(
		@NotNull(message = "필수 값입니다.") @Size(min = 1, max = 32, message = "1~32자여야 합니다.") String password)
		implements UnknownFieldRejecting {

	@Override
	public String toString() {
		return "AccountDeletionRequest[password=<hidden>]";
	}

}
