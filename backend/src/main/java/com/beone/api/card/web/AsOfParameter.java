package com.beone.api.card.web;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * {@code asOf} 쿼리 값 판정(docs/api.md 값 형식: 날짜 {@code YYYY-MM-DD}).
 *
 * <p>네 자리 연도·두 자리 월·두 자리 일만 받는다. 시간대·시각이 붙은 값, 공백, 빈 값, 없는 날짜(2월 30일 등)는
 * 형식 오류다. 값을 고치거나 다른 형식으로 해석하지 않는다.
 */
final class AsOfParameter {

	/** 쿼리 이름. */
	static final String NAME = "asOf";

	private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

	private AsOfParameter() {
	}

	/**
	 * 쿼리 값을 날짜로 바꾼다.
	 * @param value 쿼리 원문. 생략되지 않은 경우만 넘긴다
	 * @return 날짜
	 * @throws InvalidAsOfException 형식이 올바르지 않을 때
	 */
	static LocalDate parse(String value) {
		if (value == null || !DATE.matcher(value).matches()) {
			throw new InvalidAsOfException();
		}
		try {
			// ISO_LOCAL_DATE는 STRICT 해석이라 없는 날짜를 거부한다.
			return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
		}
		catch (DateTimeException ex) {
			throw new InvalidAsOfException();
		}
	}

}
