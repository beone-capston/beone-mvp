package com.beone.api.payment.application;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 모의 결제·취소 외부 ID. 접두어({@code pay-}·{@code cn-})와 DB 자동 증가 번호(10진수, 선행 0 없음)다(주문 ID 규칙 준용, 결정 27 B1-1).
 * 형식이 틀린 경로 값은 호출자가 "없는 자원"(404)으로 다룬다.
 */
public enum PaymentIds {

	/** 모의 결제 ID. 예: {@code pay-7001}. */
	SIMULATED_PAYMENT("pay-"),

	/** 모의 취소 ID. 예: {@code cn-8001}. */
	CANCELLATION("cn-");

	private final String prefix;

	private final Pattern format;

	PaymentIds(String prefix) {
		this.prefix = prefix;
		this.format = Pattern.compile(Pattern.quote(prefix) + "[1-9][0-9]{0,18}");
	}

	/** DB 번호를 외부 ID로 쓴다. */
	public String format(long id) {
		if (id < 1) {
			throw new IllegalArgumentException("id must be positive");
		}
		return this.prefix + id;
	}

	/**
	 * 외부 ID를 DB 번호로 바꾼다. 형식이 틀리거나 범위를 넘으면 비어 있다.
	 * @param value 경로 값
	 * @return DB 번호
	 */
	public Optional<Long> parse(String value) {
		if (value == null || !this.format.matcher(value).matches()) {
			return Optional.empty();
		}
		try {
			return Optional.of(Long.parseLong(value.substring(this.prefix.length())));
		}
		catch (NumberFormatException ex) {
			return Optional.empty();
		}
	}

}
