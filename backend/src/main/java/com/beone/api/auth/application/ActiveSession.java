package com.beone.api.auth.application;

import java.util.Objects;

/**
 * 쿠키로 찾은 유효한 세션.
 *
 * @param sessionId 세션 ID 원문(요청 쿠키 값)
 */
public record ActiveSession(String sessionId) {

	public ActiveSession {
		Objects.requireNonNull(sessionId, "sessionId");
	}

	/** 이 세션의 CSRF 토큰. */
	public String csrfToken() {
		return SessionTokens.csrfTokenOf(this.sessionId);
	}

	/**
	 * 받은 CSRF 토큰이 이 세션의 토큰과 같은지 상수 시간으로 비교한다.
	 * @param presentedToken 요청 헤더 값, {@code null} 가능
	 * @return 같으면 {@code true}
	 */
	public boolean csrfMatches(String presentedToken) {
		return SessionTokens.csrfMatches(this.sessionId, presentedToken);
	}

	@Override
	public String toString() {
		return "ActiveSession[sessionId=<hidden>]";
	}

}
