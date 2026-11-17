package com.beone.api.monthlystate.web;

import java.time.DateTimeException;
import java.time.YearMonth;
import java.util.regex.Pattern;

/**
 * 경로 {@code month}를 {@code YYYY-MM}으로만 받는다(결정 27 A3-9). {@code 2026-1}, {@code 2026-13}은 형식 오류다.
 */
final class MonthParameter {

	/** 오류 응답의 필드 이름. */
	static final String NAME = "month";

	private static final Pattern FORMAT = Pattern.compile("\\d{4}-\\d{2}");

	private MonthParameter() {
	}

	/**
	 * @param value 경로 값
	 * @return 계산 대상 월
	 * @throws InvalidMonthException 형식이 틀렸을 때
	 */
	static YearMonth parse(String value) {
		if (value == null || !FORMAT.matcher(value).matches()) {
			throw new InvalidMonthException();
		}
		try {
			return YearMonth.of(Integer.parseInt(value.substring(0, 4)), Integer.parseInt(value.substring(5)));
		}
		catch (DateTimeException ex) {
			throw new InvalidMonthException();
		}
	}

}
