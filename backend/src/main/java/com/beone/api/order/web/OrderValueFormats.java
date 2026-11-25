package com.beone.api.order.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import com.beone.api.order.application.OrderClock;

/**
 * 응답 값 형식(docs/api.md 값 형식). Jackson 기본 ISO 출력은 초가 0이면 초를 생략하므로({@code 10:40}) 직접 문자열로 만든다.
 */
final class OrderValueFormats {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd");

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

	private static final DateTimeFormatter INSTANT = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssXXX");

	private OrderValueFormats() {
	}

	/** {@code YYYY-MM-DD}. */
	static String date(LocalDate date) {
		return DATE.format(date);
	}

	/** {@code HH:mm:ss}. 값이 없으면 {@code null}. */
	static String time(LocalTime time) {
		return (time == null) ? null : TIME.format(time);
	}

	/** 서울 기준 시각을 {@code +09:00}이 붙은 ISO-8601로 쓴다. */
	static String seoulInstant(LocalDateTime seoulTime) {
		return INSTANT.format(seoulTime.atZone(OrderClock.ZONE));
	}

}
