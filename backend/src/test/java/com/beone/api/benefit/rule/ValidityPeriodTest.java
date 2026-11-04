package com.beone.api.benefit.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class ValidityPeriodTest {

	@Test
	void includesBothEndDates() {
		ValidityPeriod period = RuleTestData.between("2026-01-01", "2026-10-14");

		assertThat(period.contains(LocalDate.of(2026, 1, 1))).isTrue();
		assertThat(period.contains(LocalDate.of(2026, 10, 14))).isTrue();
		assertThat(period.contains(LocalDate.of(2025, 12, 31))).isFalse();
		assertThat(period.contains(LocalDate.of(2026, 10, 15))).isFalse();
	}

	@Test
	void openEndedPeriodContainsLaterDates() {
		ValidityPeriod period = ValidityPeriod.startingAt(LocalDate.of(2026, 1, 1));

		assertThat(period.contains(LocalDate.of(2030, 1, 1))).isTrue();
	}

	@Test
	void rejectsReversedPeriod() {
		assertThatThrownBy(() -> RuleTestData.between("2026-10-15", "2026-10-14"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void detectsOverlapIncludingSharedBoundaryDay() {
		ValidityPeriod first = RuleTestData.between("2026-01-01", "2026-06-30");

		assertThat(first.overlaps(RuleTestData.between("2026-06-30", "2026-12-31"))).isTrue();
		assertThat(first.overlaps(RuleTestData.between("2026-07-01", "2026-12-31"))).isFalse();
		assertThat(first.overlaps(ValidityPeriod.startingAt(LocalDate.of(2026, 3, 1)))).isTrue();
	}

}
