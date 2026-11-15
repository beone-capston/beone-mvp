package com.beone.api.benefit.card;

/**
 * 카드 규칙 안의 혜택 서비스 식별자(예: {@code BASE}, {@code FUEL}, {@code Great}).
 * 대소문자를 바꾸지 않고 적힌 그대로 비교한다.
 */
public record ServiceId(String value) {

	public ServiceId {
		Identifiers.requireToken(value, "service id");
	}

	public static ServiceId of(String value) {
		return new ServiceId(value);
	}

	@Override
	public String toString() {
		return value;
	}

}
