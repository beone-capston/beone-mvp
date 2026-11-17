package com.beone.api.codef.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/**
 * 동기화 조회 기간. 기대값은 docs/api/codef.md(생략하면 이번 달 1일~오늘, 최대 12개월, 역순 400)와 관리자 판단 P9(12개월은
 * {@code from >= to.minusMonths(12).plusDays(1)}, 오늘보다 늦은 {@code to}는 400)에서 썼다.
 */
class CodefSyncPeriodTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

	@Test
	void omittedPeriodIsFirstOfThisMonthToToday() {
		assertThat(CodefSyncPeriod.resolve(null, null, TODAY))
			.isEqualTo(new CodefSyncPeriod(LocalDate.of(2026, 11, 1), TODAY));
	}

	@Test
	void omittedEndIsTodayAndOmittedStartIsFirstOfTheEndMonth() {
		assertThat(CodefSyncPeriod.resolve(LocalDate.of(2026, 10, 3), null, TODAY))
			.isEqualTo(new CodefSyncPeriod(LocalDate.of(2026, 10, 3), TODAY));
		assertThat(CodefSyncPeriod.resolve(null, LocalDate.of(2026, 9, 20), TODAY))
			.isEqualTo(new CodefSyncPeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 20)));
	}

	@Test
	void singleDayAndTodayAreAllowed() {
		assertThat(CodefSyncPeriod.resolve(TODAY, TODAY, TODAY)).isEqualTo(new CodefSyncPeriod(TODAY, TODAY));
	}

	@Test
	void endAfterTodayIsRejectedOnTheToField() {
		assertThatThrownBy(() -> CodefSyncPeriod.resolve(null, TODAY.plusDays(1), TODAY))
			.isInstanceOfSatisfying(CodefSyncPeriodException.class, ex -> {
				assertThat(ex.field()).isEqualTo("to");
				assertThat(ex.violation()).isEqualTo("오늘보다 늦을 수 없습니다.");
			});
	}

	@Test
	void reversedPeriodIsRejectedWithoutAField() {
		assertThatThrownBy(() -> CodefSyncPeriod.resolve(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 9), TODAY))
			.isInstanceOfSatisfying(CodefSyncPeriodException.class, ex -> {
				assertThat(ex.field()).isNull();
				assertThat(ex.violation()).isEqualTo("시작일이 종료일보다 늦습니다.");
			});
	}

	@Test
	void twelveMonthsIsTheLongestPeriod() {
		// to=2026-11-18이면 가장 이른 from은 2025-11-19다
		assertThat(CodefSyncPeriod.resolve(LocalDate.of(2025, 11, 19), TODAY, TODAY).from())
			.isEqualTo(LocalDate.of(2025, 11, 19));
		assertThatThrownBy(() -> CodefSyncPeriod.resolve(LocalDate.of(2025, 11, 18), TODAY, TODAY))
			.isInstanceOfSatisfying(CodefSyncPeriodException.class, ex -> {
				assertThat(ex.field()).isNull();
				assertThat(ex.violation()).isEqualTo("조회 기간은 최대 12개월입니다.");
			});
	}

	@Test
	void monthEndUsesTheCalendarRule() {
		// 2026-02-28의 12개월 전 다음 날은 2025-03-01이다
		LocalDate end = LocalDate.of(2026, 2, 28);
		assertThat(CodefSyncPeriod.resolve(LocalDate.of(2025, 3, 1), end, TODAY).from()).isEqualTo(LocalDate.of(2025, 3, 1));
		assertThatThrownBy(() -> CodefSyncPeriod.resolve(LocalDate.of(2025, 2, 28), end, TODAY))
			.isInstanceOf(CodefSyncPeriodException.class);
	}

}
