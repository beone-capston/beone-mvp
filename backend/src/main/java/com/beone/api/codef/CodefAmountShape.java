package com.beone.api.codef;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import tools.jackson.databind.JsonNode;

/**
 * 금액 필드 원문의 "형태"만 남긴 진단용 값(#274). 원문 문자열·숫자 값은 보관하지 않고, 고정 어휘의 형태 키와 길이 구간만 둔다.
 *
 * <p>
 * 형태 키는 아래 낱말을 이 순서로 {@code +}로 이은 것이다. 입력 문자를 그대로 담지 않는다.
 * <ul>
 * <li>{@code missing}: 키 누락. {@code null}: JSON null. {@code nonString}: 문자열·숫자가 아닌 값(불리언·객체·배열)</li>
 * <li>{@code jsonNumber}: 문자열이 아닌 JSON 숫자. 뒤에 그 숫자 표기의 문자 분류가 붙는다</li>
 * <li>{@code empty}: 빈 문자열</li>
 * <li>{@code digits}: ASCII 숫자 {@code 0-9}. {@code comma}: {@code ,}. {@code minus}: {@code -}. {@code dot}: {@code .}</li>
 * <li>{@code currency}: 통화 기호(유니코드 Sc 분류, {@code ₩}·{@code ￦}·{@code $} 등), {@code 원}, 통화 코드
 * {@code KRW}·{@code USD}·{@code EUR}·{@code JPY}·{@code CNY}(대소문자 무시)</li>
 * <li>{@code space}: 공백 문자(줄바꿈·탭·NBSP 포함)</li>
 * <li>{@code other}: 그 밖의 문자(다른 글자, {@code +}, 전각 숫자, 괄호 등)</li>
 * </ul>
 * 길이 구간은 원문 문자열의 코드 포인트 수로 {@code 0}·{@code 1-3}·{@code 4-6}·{@code 7-9}·{@code 10+}이며, 값이 문자열·숫자가 아니면
 * {@code none}이다. 길이(자릿수 구간) 외의 숫자 정보는 남기지 않는다.
 *
 * @param form 형태 키(고정 어휘)
 * @param length 길이 구간(고정 어휘)
 */
public record CodefAmountShape(String form, String length) {

	private static final List<String> TOKENS = List.of("notCaptured", "missing", "null", "nonString", "jsonNumber",
			"empty", "digits", "comma", "minus", "dot", "currency", "space", "other");

	private static final Set<String> LENGTHS = Set.of("none", "0", "1-3", "4-6", "7-9", "10+");

	private static final List<String> CURRENCY_CODES = List.of("KRW", "USD", "EUR", "JPY", "CNY");

	/** 형태를 기록하지 않고 만든 승인 항목(테스트 등 직접 생성)의 값. 어휘 목록보다 뒤에 둬야 초기화된다. */
	public static final CodefAmountShape NOT_CAPTURED = new CodefAmountShape("notCaptured", "none");

	/**
	 * 고정 어휘만 받는다. 입력 문자열이 형태 키로 새어 나가지 않게 막는다.
	 *
	 * @throws IllegalArgumentException 어휘 밖의 낱말·구간인 경우
	 */
	public CodefAmountShape {
		Objects.requireNonNull(form, "form");
		Objects.requireNonNull(length, "length");
		for (String token : form.split("\\+", -1)) {
			if (!TOKENS.contains(token)) {
				throw new IllegalArgumentException("unknown shape token");
			}
		}
		if (!LENGTHS.contains(length)) {
			throw new IllegalArgumentException("unknown length bucket");
		}
	}

	/**
	 * 객체의 한 필드 원문을 형태로 바꾼다.
	 * @param object 항목 객체
	 * @param key 필드 이름
	 * @return 형태와 길이 구간
	 */
	static CodefAmountShape of(JsonNode object, String key) {
		JsonNode value = object.get(key);
		if (value == null || value.isMissingNode()) {
			return new CodefAmountShape("missing", "none");
		}
		if (value.isNull()) {
			return new CodefAmountShape("null", "none");
		}
		if (value.isNumber()) {
			String text = value.asString();
			return new CodefAmountShape("jsonNumber+" + classify(text), bucket(text));
		}
		if (!value.isString()) {
			return new CodefAmountShape("nonString", "none");
		}
		String text = value.stringValue();
		return new CodefAmountShape(text.isEmpty() ? "empty" : classify(text), bucket(text));
	}

	// 문자마다 분류하고, 나온 분류를 고정 순서로 잇는다
	private static String classify(String text) {
		boolean[] currencyCode = new boolean[text.length()];
		String upper = text.toUpperCase(Locale.ROOT);
		boolean currency = false;
		if (upper.length() == text.length()) {
			for (String code : CURRENCY_CODES) {
				for (int from = upper.indexOf(code); from >= 0; from = upper.indexOf(code, from + 1)) {
					currency = true;
					for (int index = from; index < from + code.length(); index++) {
						currencyCode[index] = true;
					}
				}
			}
		}
		boolean digits = false;
		boolean comma = false;
		boolean minus = false;
		boolean dot = false;
		boolean space = false;
		boolean other = false;
		for (int index = 0; index < text.length();) {
			int codePoint = text.codePointAt(index);
			int width = Character.charCount(codePoint);
			if (currencyCode[index]) {
				// 통화 코드 글자는 이미 셌다
			}
			else if (codePoint >= '0' && codePoint <= '9') {
				digits = true;
			}
			else if (codePoint == ',') {
				comma = true;
			}
			else if (codePoint == '-') {
				minus = true;
			}
			else if (codePoint == '.') {
				dot = true;
			}
			else if (codePoint == '원' || Character.getType(codePoint) == Character.CURRENCY_SYMBOL) {
				currency = true;
			}
			else if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
				space = true;
			}
			else {
				other = true;
			}
			index += width;
		}
		List<String> tokens = new ArrayList<>();
		add(tokens, digits, "digits");
		add(tokens, comma, "comma");
		add(tokens, minus, "minus");
		add(tokens, dot, "dot");
		add(tokens, currency, "currency");
		add(tokens, space, "space");
		add(tokens, other, "other");
		return String.join("+", tokens);
	}

	private static void add(List<String> tokens, boolean present, String token) {
		if (present) {
			tokens.add(token);
		}
	}

	private static String bucket(String text) {
		int count = text.codePointCount(0, text.length());
		if (count == 0) {
			return "0";
		}
		if (count <= 3) {
			return "1-3";
		}
		if (count <= 6) {
			return "4-6";
		}
		if (count <= 9) {
			return "7-9";
		}
		return "10+";
	}

}
