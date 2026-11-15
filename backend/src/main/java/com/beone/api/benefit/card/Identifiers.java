package com.beone.api.benefit.card;

/**
 * 식별자 값 객체의 공통 검증. 빈 값과 공백이 들어간 값을 거부한다.
 */
final class Identifiers {

	private Identifiers() {
	}

	static void requireToken(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " is required");
		}
		if (!value.equals(value.strip()) || value.chars().anyMatch(Character::isWhitespace)) {
			throw new IllegalArgumentException(name + " must not contain whitespace: '" + value + "'");
		}
	}

}
