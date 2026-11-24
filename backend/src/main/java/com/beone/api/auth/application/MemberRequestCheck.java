package com.beone.api.auth.application;

import java.time.Duration;
import java.util.Objects;

/**
 * 회원 프로필 업무 요청({@code /api/v1/me/**})의 세션 검사 결과(결정 37 D3·D5·D6). 검사 순서는 회원 세션(401) → 쓰기 CSRF(403)다.
 */
public sealed interface MemberRequestCheck {

	/**
	 * 유효한 회원 세션이고 쓰기면 CSRF 토큰도 맞다. 미사용 만료를 늘렸다.
	 *
	 * @param memberId 회원 ID
	 * @param sessionId 요청 쿠키의 세션 ID(쿠키를 다시 심는 데 쓴다)
	 * @param cookieMaxAge 늘린 미사용 만료까지의 쿠키 수명. 절대 만료(30일)를 넘지 않는다
	 */
	record Authenticated(String memberId, String sessionId, Duration cookieMaxAge) implements MemberRequestCheck {

		public Authenticated {
			Objects.requireNonNull(memberId, "memberId");
			Objects.requireNonNull(sessionId, "sessionId");
			Objects.requireNonNull(cookieMaxAge, "cookieMaxAge");
		}

		@Override
		public String toString() {
			return "Authenticated[memberId=" + this.memberId + ", sessionId=<hidden>, cookieMaxAge=" + this.cookieMaxAge
					+ "]";
		}

	}

	/** 쿠키 없음·형식 오류·없는 세션·만료·익명 세션. 401이다. */
	record Unauthenticated() implements MemberRequestCheck {
	}

	/** 회원 세션이지만 쓰기 요청의 CSRF 토큰이 없거나 다르다. 403이며 만료를 늘리지 않는다. */
	record CsrfInvalid() implements MemberRequestCheck {
	}

}
