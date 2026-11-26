package com.beone.api.auth.application;

import java.time.Duration;
import java.util.Objects;

/**
 * 회원 탈퇴 판정 결과. 비밀번호 불일치 기록이 커밋되도록 실패도 예외가 아니라 결과 값으로 돌려준다.
 */
public sealed interface WithdrawalResult {

	/** 탈퇴했다. 회원·세션·인증 토큰·업무 장부가 지워졌다. */
	record Withdrawn() implements WithdrawalResult {
	}

	/** 비밀번호 재확인 실패. 로그인 실패 제한에 한 번 더해졌다. */
	record PasswordMismatch() implements WithdrawalResult {
	}

	/**
	 * 로그인 실패 제한으로 잠겨 있다.
	 * @param retryAfter 남은 시간(1초 이상)
	 */
	record RateLimited(Duration retryAfter) implements WithdrawalResult {

		public RateLimited {
			Objects.requireNonNull(retryAfter, "retryAfter");
		}

	}

	/** 세션은 통과했지만 회원 행이 없다(동시에 탈퇴가 끝난 경우). */
	record MemberGone() implements WithdrawalResult {
	}

}
