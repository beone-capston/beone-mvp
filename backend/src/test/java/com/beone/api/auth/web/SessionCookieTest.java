package com.beone.api.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 세션 쿠키 헤더 원문과 읽기를 확인한다. 기대 문자열은 docs/api/auth.md 로그인 응답 헤더 예시의 속성·순서(D-24)다.
 */
class SessionCookieTest {

	@Test
	void issueHeaderMatchesContractAttributesWithoutExpiresOrDomain() {
		assertThat(SessionCookie.issue("abc", Duration.ofDays(30)))
			.isEqualTo("__Host-BEONE_SESSION=abc; Path=/; Max-Age=2592000; Secure; HttpOnly; SameSite=Lax");
	}

	@Test
	void clearHeaderMatchesLogoutContract() {
		assertThat(SessionCookie.clear())
			.isEqualTo("__Host-BEONE_SESSION=; Path=/; Max-Age=0; Secure; HttpOnly; SameSite=Lax");
	}

	@Test
	void readReturnsFirstSessionCookieOrNull() {
		MockHttpServletRequest none = new MockHttpServletRequest();
		assertThat(SessionCookie.read(none)).isNull();

		MockHttpServletRequest other = new MockHttpServletRequest();
		other.setCookies(new Cookie("OTHER", "x"));
		assertThat(SessionCookie.read(other)).isNull();

		MockHttpServletRequest two = new MockHttpServletRequest();
		two.setCookies(new Cookie("OTHER", "x"), new Cookie("__Host-BEONE_SESSION", "first"),
				new Cookie("__Host-BEONE_SESSION", "second"));
		assertThat(SessionCookie.read(two)).isEqualTo("first");
	}

}
