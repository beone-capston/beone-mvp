package com.beone.api.ledger.application;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 원장 행 외부 ID. {@code lt-}와 DB 자동 증가 번호(10진수, 선행 0 없음)다(주문 ID와 같은 규칙, 결정 27 B1-1 준용).
 */
public final class LedgerTransactionId {

	private static final String PREFIX = "lt-";

	private static final Pattern FORMAT = Pattern.compile("lt-[1-9][0-9]{0,18}");

	private LedgerTransactionId() {
	}

	/**
	 * DB 번호를 외부 ID로 쓴다.
	 * @param id 양수 DB 번호
	 * @return 외부 거래 ID
	 */
	public static String format(long id) {
		if (id < 1) {
			throw new IllegalArgumentException("id must be positive");
		}
		return PREFIX + id;
	}

	/**
	 * 외부 ID를 DB 번호로 바꾼다. 형식이 틀리거나 범위를 넘으면 비어 있다.
	 * @param value 외부 ID
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
