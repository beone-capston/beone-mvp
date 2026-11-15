package com.beone.api.benefit.state;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * 값이 가리키는 기간. 시작일과 종료일을 포함하며 Asia/Seoul 기준 날짜다.
 */
public record BasisPeriod(LocalDate from, LocalDate to) {

	public BasisPeriod {
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
		if (to.isBefore(from)) {
			throw new IllegalArgumentException("basis period ends before it starts: " + from + " ~ " + to);
		}
	}

	public static BasisPeriod of(LocalDate from, LocalDate to) {
		return new BasisPeriod(from, to);
	}

	public static BasisPeriod day(LocalDate date) {
		return new BasisPeriod(date, date);
	}

	public static BasisPeriod month(YearMonth month) {
		return new BasisPeriod(month.atDay(1), month.atEndOfMonth());
	}

	public boolean contains(LocalDate date) {
		return !date.isBefore(from) && !date.isAfter(to);
	}

}
