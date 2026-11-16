package com.beone.api.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/**
 * 이번 달·생성 시각이 Asia/Seoul 기준인지 확인한다(결정 27).
 */
class OrderClockTest {

	@Test
	void currentMonthUsesSeoulDateAtUtcMonthBoundary() {
		// UTC로는 10월 31일 15:30, 서울로는 11월 1일 00:30
		OrderClock clock = new OrderClock(Clock.fixed(Instant.parse("2026-10-31T15:30:00Z"), ZoneOffset.UTC));

		assertThat(clock.currentMonth()).isEqualTo(YearMonth.of(2026, 11));
	}

	@Test
	void currentMonthBeforeSeoulMidnightStaysInPreviousMonth() {
		OrderClock clock = new OrderClock(Clock.fixed(Instant.parse("2026-11-30T14:59:59Z"), ZoneOffset.UTC));

		assertThat(clock.currentMonth()).isEqualTo(YearMonth.of(2026, 11));
	}

	@Test
	void nowIsSeoulLocalTimeTruncatedToSeconds() {
		OrderClock clock = new OrderClock(Clock.fixed(Instant.parse("2026-11-03T01:40:00.789Z"), ZoneOffset.UTC));

		assertThat(clock.now()).isEqualTo(LocalDateTime.of(2026, 11, 3, 10, 40, 0));
	}

}
