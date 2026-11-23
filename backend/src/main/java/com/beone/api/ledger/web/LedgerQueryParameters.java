package com.beone.api.ledger.web;

import java.time.DateTimeException;
import java.time.YearMonth;
import java.util.regex.Pattern;

import com.beone.api.ledger.application.LedgerSource;

/**
 * 원장 조회 쿼리 값 검사. 틀리면 {@link InvalidLedgerQueryException}에 필드 이름만 담는다(입력 원문은 남기지 않음).
 */
final class LedgerQueryParameters {

	static final String MONTH = "month";

	static final String SOURCE = "source";

	private static final Pattern MONTH_FORMAT = Pattern.compile("\\d{4}-\\d{2}");

	private LedgerQueryParameters() {
	}

	/** {@code YYYY-MM}만 받는다. {@code 2026-1}·{@code 2026-13}은 형식 오류다. */
	static YearMonth month(String value) {
		if (!MONTH_FORMAT.matcher(value).matches()) {
			throw new InvalidLedgerQueryException(MONTH);
		}
		try {
			return YearMonth.of(Integer.parseInt(value.substring(0, 4)), Integer.parseInt(value.substring(5)));
		}
		catch (DateTimeException ex) {
			throw new InvalidLedgerQueryException(MONTH);
		}
	}

	/** 생략하면 {@code null}(모든 출처). 빈 값·소문자·그 밖의 값은 형식 오류다. */
	static LedgerSource source(String value) {
		if (value == null) {
			return null;
		}
		for (LedgerSource source : LedgerSource.values()) {
			if (source.name().equals(value)) {
				return source;
			}
		}
		throw new InvalidLedgerQueryException(SOURCE);
	}

}
