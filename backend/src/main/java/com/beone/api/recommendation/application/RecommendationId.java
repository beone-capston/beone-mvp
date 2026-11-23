package com.beone.api.recommendation.application;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 추천 외부 ID. {@code rec-}와 DB 자동 증가 번호(10진수, 선행 0 없음)다(결정 27 B2-2, 주문 ID와 같은 규칙).
 */
public final class RecommendationId {

	private static final String PREFIX = "rec-";

	private static final Pattern FORMAT = Pattern.compile("rec-[1-9][0-9]{0,18}");

	private RecommendationId() {
	}

	/** DB 번호를 외부 ID로 쓴다. */
	public static String format(long id) {
		if (id < 1) {
			throw new IllegalArgumentException("id must be positive");
		}
		return PREFIX + id;
	}

	/**
	 * 외부 ID를 DB 번호로 바꾼다. 형식이 틀리거나 범위를 넘으면 비어 있다(호출자가 404로 다룬다).
	 * @param value 경로 값
	 * @return DB 번호
	 */
	public static Optional<Long> parse(String value) {
		if (value == null || !FORMAT.matcher(value).matches()) {
			return Optional.empty();
		}
		try {
			return Optional.of(Long.parseLong(value.substring(PREFIX.length())));
		}
		catch (NumberFormatException ex) {
			return Optional.empty();
		}
	}

}
