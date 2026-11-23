package com.beone.api.benefit.engine;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 입구 record들이 함께 쓰는 생성 시 계약 검증.
 */
final class EngineChecks {

	private EngineChecks() {
	}

	/** 공백 없는 필수 식별자를 확인한다. */
	static void requireToken(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " is required");
		}
		if (value.chars().anyMatch(Character::isWhitespace)) {
			throw new IllegalArgumentException(name + " must not contain whitespace");
		}
	}

	/** 값이 있을 때만 식별자 형식을 확인한다. */
	static void requireOptionalToken(String value, String name) {
		if (value != null) {
			requireToken(value, name);
		}
	}

	/** 값이 있을 때만 빈 문자열이 아닌지 확인한다. */
	static void requireOptionalText(String value, String name) {
		if (value != null && value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank when present");
		}
	}

	/** 필수 문자열을 확인한다. */
	static void requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " is required");
		}
	}

	/** 내부 enum 이름과 같은 값만 받는다. */
	static <E extends Enum<E>> void requireEnumName(Class<E> type, String value, String name) {
		if (value == null) {
			throw new IllegalArgumentException(name + " is required");
		}
		try {
			Enum.valueOf(type, value);
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException(name + " is not allowed: " + value, ex);
		}
	}

	/** 정해진 값만 받는다. */
	static void requireOneOf(Set<String> allowed, String value, String name) {
		if (value == null) {
			throw new IllegalArgumentException(name + " is required");
		}
		if (!allowed.contains(value)) {
			throw new IllegalArgumentException(name + " is not allowed: " + value);
		}
	}

	/** 값이 있을 때 음수를 거부한다. {@code null}은 모름이며 0으로 바꾸지 않는다. */
	static void requireOptionalNonNegative(Long value, String name) {
		if (value != null && value < 0) {
			throw new IllegalArgumentException(name + " must not be negative: " + value);
		}
	}

	/** 중복을 거부한다. */
	static void requireDistinct(Collection<String> values, String name) {
		Set<String> seen = new HashSet<>();
		for (String value : values) {
			if (!seen.add(value)) {
				throw new IllegalArgumentException(name + " is duplicated: " + value);
			}
		}
	}

	/** 목록 원소에 null이 없는 불변 사본을 돌려준다. */
	static <T> List<T> copy(List<T> values, String name) {
		Objects.requireNonNull(values, name);
		return List.copyOf(values);
	}

}
