package com.beone.api.codef;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;

/**
 * CODEF 응답 필드를 읽는 공통 규칙. 빈 값을 기본값으로 바꾸지 않고, 예외·결과에 원문을 남기지 않는다.
 */
final class CodefFields {

	private static final Pattern SIGNED_INTEGER = Pattern.compile("-?[0-9]+");

	private CodefFields() {
	}

	/** 문자열 필드. 누락·null·공백뿐인 문자열·문자열이 아닌 값은 빈 값(미확인)이다. */
	static Optional<String> text(JsonNode object, String key) {
		JsonNode value = object.get(key);
		if (value == null || !value.isString() || value.stringValue().isBlank()) {
			return Optional.empty();
		}
		return Optional.of(value.stringValue());
	}

	/** 금액 문자열. 부호 있는 정수만 금액이며 쉼표·소수점·통화 기호는 해석하지 않는다. */
	static CodefAmount amount(JsonNode object, String key) {
		Optional<String> value = text(object, key);
		if (value.isEmpty()) {
			return new CodefAmount.Absent();
		}
		String trimmed = value.get().strip();
		if (!SIGNED_INTEGER.matcher(trimmed).matches()) {
			return new CodefAmount.Unparsed();
		}
		try {
			return new CodefAmount.Parsed(Long.parseLong(trimmed));
		}
		catch (NumberFormatException overflow) {
			return new CodefAmount.Unparsed();
		}
	}

	/**
	 * 객체·배열 목록. 객체는 1건, 배열은 원소마다 1건이며 원소가 모두 객체여야 한다.
	 *
	 * @param node 목록 값. 키가 없으면 null
	 * @param reader 객체 하나를 항목으로 읽는 함수
	 * @param blankIsMissing 빈 문자열을 값 없음으로 볼지. 중첩 목록은 "미제공시 빈칸" 규칙에 따라 그렇게 본다
	 */
	static <T> CodefList<T> list(JsonNode node, Function<JsonNode, T> reader, boolean blankIsMissing) {
		if (node == null || node.isMissingNode()) {
			return new CodefList.Missing<>();
		}
		if (node.isObject()) {
			return new CodefList.Items<>(CodefList.Shape.SINGLE_OBJECT, List.of(reader.apply(node)));
		}
		if (node.isArray()) {
			List<T> items = new ArrayList<>();
			for (JsonNode element : node.values()) {
				if (!element.isObject()) {
					return new CodefList.Malformed<>("array element is not an object");
				}
				items.add(reader.apply(element));
			}
			return new CodefList.Items<>(CodefList.Shape.ARRAY, items);
		}
		if (blankIsMissing && node.isString() && node.stringValue().isBlank()) {
			return new CodefList.Missing<>();
		}
		return new CodefList.Malformed<>(node.isNull() ? "list is null" : "list is neither an object nor an array");
	}

	/**
	 * 문서에 값 목록이 있는 코드 필드. 목록 밖 값과 빈 값은 {@code unknown}이다.
	 *
	 * @param object 항목 객체
	 * @param key 필드 이름
	 * @param values 열거형 값 전체
	 * @param code 열거형 값의 문서 코드. 미확인 값이면 null
	 * @param unknown 미확인 값
	 */
	static <E extends Enum<E>> E code(JsonNode object, String key, E[] values, Function<E, String> code, E unknown) {
		Optional<String> value = text(object, key);
		if (value.isEmpty()) {
			return unknown;
		}
		for (E candidate : values) {
			if (value.get().equals(code.apply(candidate))) {
				return candidate;
			}
		}
		return unknown;
	}

}
