package com.beone.api.order.web;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Pattern;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * 날짜를 {@code YYYY-MM-DD} 문자열로만 받는다(docs/api.md 값 형식). 없는 날짜({@code 2026-02-30}), 시각이 붙은 값,
 * 배열·숫자 표현은 역직렬화 오류(400 {@code INVALID_REQUEST})다. 오류 메시지에 입력값을 넣지 않는다.
 */
public class StrictDateDeserializer extends ValueDeserializer<LocalDate> {

	private static final Pattern SHAPE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

	private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd")
		.withResolverStyle(ResolverStyle.STRICT);

	@Override
	public LocalDate deserialize(JsonParser parser, DeserializationContext context) {
		if (parser.currentToken() != JsonToken.VALUE_STRING) {
			return context.reportInputMismatch(this, "date must be a YYYY-MM-DD string");
		}
		String text = parser.getString();
		if (!SHAPE.matcher(text).matches()) {
			return context.reportInputMismatch(this, "date must be a YYYY-MM-DD string");
		}
		try {
			return LocalDate.parse(text, FORMAT);
		}
		catch (DateTimeParseException exception) {
			return context.reportInputMismatch(this, "date does not exist");
		}
	}

}
