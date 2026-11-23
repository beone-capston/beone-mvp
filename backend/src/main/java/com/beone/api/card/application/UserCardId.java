package com.beone.api.card.application;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 보유 카드 ID의 외부 표현. {@code uc-} 뒤에 저장소 번호를 선행 0 없이 붙인다(결정 27 A2-1, 예: {@code uc-1001}).
 */
public final class UserCardId {

	private static final String PREFIX = "uc-";

	private static final Pattern FORMAT = Pattern.compile("uc-[1-9][0-9]{0,18}");

	private UserCardId() {
	}

	/** 저장소 번호를 외부 표현으로 바꾼다. */
	public static String format(long id) {
		if (id < 1) {
			throw new IllegalArgumentException("user card id must be positive");
		}
		return PREFIX + id;
	}

	/**
	 * 외부 표현을 저장소 번호로 바꾼다.
	 * @param value 경로로 받은 값
	 * @return 형식이 맞으면 번호, 아니면 빈 값. 형식 오류도 호출자가 "없는 카드"로 다룬다
	 */
	public static Optional<Long> parse(String value) {
		if (value == null || !FORMAT.matcher(value).matches()) {
			return Optional.empty();
		}
		try {
			return Optional.of(Long.parseLong(value.substring(PREFIX.length())));
		}
		catch (NumberFormatException overflow) {
			return Optional.empty();
		}
	}

}
