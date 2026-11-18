package com.beone.api.auth.application;

import java.time.Duration;
import java.util.Objects;

/**
 * 로그인 판정 결과. 실패도 실패 기록을 커밋해야 하므로 예외 대신 값으로 돌려준다.
 */
public sealed interface LoginResult {

	/**
	 * 로그인 성공.
	 * @param memberId 회원 ID
	 * @param email 정규화한 이메일
	 * @param session 새로 발급한 회원 세션
	 */
	record Succeeded(String memberId, String email, IssuedSession session) implements LoginResult {

		public Succeeded {
			Objects.requireNonNull(memberId, "memberId");
			Objects.requireNonNull(email, "email");
			Objects.requireNonNull(session, "session");
		}

		@Override
		public String toString() {
			return "Succeeded[memberId=" + this.memberId + ", email=<hidden>, session=" + this.session + "]";
		}

	}

	/** 계정 없음 또는 비밀번호 불일치(둘을 구분하지 않음). */
	record InvalidCredentials() implements LoginResult {
	}

	/** 비밀번호는 맞지만 이메일 미인증. */
	record EmailNotVerified() implements LoginResult {
	}

	/**
	 * 실패 제한으로 잠겨 있음.
	 * @param retryAfter 남은 시간(1초 이상)
	 */
	record RateLimited(Duration retryAfter) implements LoginResult {

		public RateLimited {
			Objects.requireNonNull(retryAfter, "retryAfter");
		}

	}

}
