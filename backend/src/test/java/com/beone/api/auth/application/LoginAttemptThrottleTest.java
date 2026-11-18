package com.beone.api.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

/**
 * 로그인 실패 제한의 남은 시간 계산을 확인한다. 기대값: 15분 잠금(2026-11-18 사용자 직접 결정), 남은 초는 올림하고 최소 1초다(U2 메일
 * 요청 제한과 같은 방식). 실패 수 세기·잠금은 통합 테스트가 실제 MySQL로 본다.
 */
class LoginAttemptThrottleTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 10, 0, 0);

	@Test
	void policyIsFiveFailuresInFifteenMinutes() {
		assertThat(LoginAttemptThrottle.MAX_FAILURES).isEqualTo(5);
		assertThat(LoginAttemptThrottle.WINDOW).isEqualTo(Duration.ofMinutes(15));
	}

	@Test
	void retryAfterIsRemainingSecondsRoundedUpWithMinimumOne() {
		assertThat(LoginAttemptThrottle.retryAfter(NOW.plusMinutes(15), NOW)).isEqualTo(Duration.ofSeconds(900));
		assertThat(LoginAttemptThrottle.retryAfter(NOW.plusSeconds(1), NOW)).isEqualTo(Duration.ofSeconds(1));
		assertThat(LoginAttemptThrottle.retryAfter(NOW.plusNanos(1), NOW)).isEqualTo(Duration.ofSeconds(1));
		assertThat(LoginAttemptThrottle.retryAfter(NOW.plusSeconds(1).plusNanos(1), NOW))
			.isEqualTo(Duration.ofSeconds(2));
	}

}
