package com.beone.api.benefit.evaluation;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import tools.jackson.databind.JsonNode;

/**
 * 평가 fixture를 엄격하게 읽는 도구.
 * 모르는 키, 빠진 키, 잘못된 타입은 실패시켜 fixture 필드가 조용히 빠지거나 다르게 해석되지 않게 한다.
 */
final class FixtureJson {

	private FixtureJson() {
	}

	static void requireOnlyKeys(JsonNode node, String where, String... allowed) {
		if (!node.isObject()) {
			throw new IllegalArgumentException(where + " must be an object");
		}
		Set<String> allowedKeys = Set.of(allowed);
		for (String key : node.propertyNames()) {
			if (!allowedKeys.contains(key)) {
				throw new IllegalArgumentException(where + " has unmapped key '" + key + "'");
			}
		}
	}

	static JsonNode required(JsonNode node, String key) {
		JsonNode value = node.get(key);
		if (value == null) {
			throw new IllegalArgumentException("missing key '" + key + "'");
		}
		return value;
	}

	static boolean isAbsentOrNull(JsonNode node, String key) {
		JsonNode value = node.get(key);
		return value == null || value.isNull();
	}

	static String text(JsonNode node, String key) {
		JsonNode value = required(node, key);
		if (!value.isString()) {
			throw new IllegalArgumentException("'" + key + "' must be a string");
		}
		return value.stringValue();
	}

	static String textOrNull(JsonNode node, String key) {
		return isAbsentOrNull(node, key) ? null : text(node, key);
	}

	static LocalDate date(JsonNode node, String key) {
		return LocalDate.parse(text(node, key));
	}

	static long integer(JsonNode node, String key) {
		return integerValue(required(node, key), key);
	}

	static long integerValue(JsonNode value, String where) {
		if (!value.isIntegralNumber() || !value.canConvertToLong()) {
			throw new IllegalArgumentException("'" + where + "' must be an integer won amount");
		}
		return value.longValue();
	}

	static List<String> textList(JsonNode node, String key) {
		JsonNode value = required(node, key);
		if (!value.isArray()) {
			throw new IllegalArgumentException("'" + key + "' must be an array");
		}
		return value.values().stream().map(item -> {
			if (!item.isString()) {
				throw new IllegalArgumentException("'" + key + "' must contain strings");
			}
			return item.stringValue();
		}).toList();
	}

}
