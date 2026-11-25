package com.beone.api.auth.application;

import java.time.Duration;
import java.util.Objects;

/**
 * 새로 발급한 세션. 세션 ID 원문은 쿠키에 담을 때만 쓰고 로그·응답 본문에 넣지 않는다.
 *
 * @param sessionId 세션 ID 원문(쿠키 값)
 * @param csrfToken 이 세션의 CSRF 토큰
 * @param cookieMaxAge 쿠키 {@code Max-Age}
 */
public record IssuedSession(String sessionId, String csrfToken, Duration cookieMaxAge) {

	public IssuedSession {
		Objects.requireNonNull(sessionId, "sessionId");
		Objects.requireNonNull(csrfToken, "csrfToken");
		Objects.requireNonNull(cookieMaxAge, "cookieMaxAge");
	}

	@Override
	public String toString() {
		return "IssuedSession[sessionId=<hidden>, csrfToken=<hidden>, cookieMaxAge=" + this.cookieMaxAge + "]";
	}

}
