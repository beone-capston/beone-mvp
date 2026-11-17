package com.beone.api.auth.web;

import java.time.Duration;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 인증 세션 쿠키 {@code __Host-BEONE_SESSION}(D-24). {@code Secure}·{@code HttpOnly}·{@code SameSite=Lax}·{@code Path=/}이며
 * {@code Domain}을 지정하지 않는다.
 *
 * <p>헤더 문자열은 docs/api/auth.md 예시와 같은 순서·속성으로 직접 만든다({@code Expires}를 붙이지 않는다).
 */
final class SessionCookie {

	/** 쿠키 이름. */
	static final String NAME = "__Host-BEONE_SESSION";

	private SessionCookie() {
	}

	/**
	 * 요청에서 세션 쿠키 값을 읽는다. 같은 이름이 여러 개면 첫 번째만 쓴다.
	 * @param request 요청
	 * @return 쿠키 값, 없으면 {@code null}
	 */
	static String read(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (NAME.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}

	/**
	 * 세션 쿠키를 심는 {@code Set-Cookie} 값.
	 * @param sessionId 세션 ID 원문
	 * @param maxAge 쿠키 수명
	 * @return 헤더 값
	 */
	static String issue(String sessionId, Duration maxAge) {
		return NAME + "=" + sessionId + "; Path=/; Max-Age=" + maxAge.toSeconds() + "; Secure; HttpOnly; SameSite=Lax";
	}

}
