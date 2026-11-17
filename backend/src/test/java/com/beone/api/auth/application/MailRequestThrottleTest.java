package com.beone.api.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

/**
 * 남은 시간({@code Retry-After}) 계산을 확인한다. 기대값: 간격 60초, 남은 초를 올림하고 최소 1초(docs/api/auth.md).
 */
class MailRequestThrottleTest {

	private static final LocalDateTime LAST = LocalDateTime.of(2026, 11, 18, 10, 0, 0);

	@Test
	void retryAfterIsRemainingSecondsOfSixtySecondInterval() {
		assertThat(MailRequestThrottle.retryAfter(LAST, LAST)).isEqualTo(Duration.ofSeconds(60));
		assertThat(MailRequestThrottle.retryAfter(LAST, LAST.plusSeconds(1))).isEqualTo(Duration.ofSeconds(59));
		assertThat(MailRequestThrottle.retryAfter(LAST, LAST.plusSeconds(59))).isEqualTo(Duration.ofSeconds(1));
	}

	@Test
	void retryAfterRoundsUpAndIsAtLeastOneSecond() {
		assertThat(MailRequestThrottle.retryAfter(LAST, LAST.plusNanos(500_000_000))).isEqualTo(Duration.ofSeconds(60));
		assertThat(MailRequestThrottle.retryAfter(LAST, LAST.plusSeconds(60))).isEqualTo(Duration.ofSeconds(1));
	}

}
