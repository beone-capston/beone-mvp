package com.beone.api.card.web;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 보유 카드 응답의 시점 형식(docs/api.md 값 형식). 서울 시각을 {@code +09:00}이 붙은 ISO-8601 문자열로 쓴다. Jackson 기본 출력은 초가
 * 0이면 초를 생략하므로 직접 만든다.
 */
final class UserCardValueFormats {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private static final DateTimeFormatter INSTANT = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssXXX");

	private UserCardValueFormats() {
	}

	/** 서울 시각을 {@code 2026-11-03T10:12:00+09:00} 형식으로 쓴다. 값이 없으면 {@code null}. */
	static String seoulInstant(LocalDateTime seoulTime) {
		return (seoulTime == null) ? null : INSTANT.format(seoulTime.atZone(SEOUL));
	}

}
