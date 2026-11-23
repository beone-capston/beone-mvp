package com.beone.api.order.web;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Pattern;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * 시각 없는 시간 값을 {@code HH:mm:ss} 문자열로만 받는다(docs/api.md 값 형식). {@code 10:40}, 소수 초, 범위 밖 값은
 * 역직렬화 오류(400 {@code INVALID_REQUEST})다. JSON {@code null}은 이 클래스까지 오지 않고 {@code null}로 남는다.
 */
public class StrictTimeDeserializer extends ValueDeserializer<LocalTime> {

	private static final Pattern SHAPE = Pattern.compile("\\d{2}:\\d{2}:\\d{2}");

	private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
		.withResolverStyle(ResolverStyle.STRICT);

	@Override
	public LocalTime deserialize(JsonParser parser, DeserializationContext context) {
		if (parser.currentToken() != JsonToken.VALUE_STRING) {
			return context.reportInputMismatch(this, "time must be a HH:mm:ss string");
		}
		String text = parser.getString();
		if (!SHAPE.matcher(text).matches()) {
			return context.reportInputMismatch(this, "time must be a HH:mm:ss string");
		}
		try {
			return LocalTime.parse(text, FORMAT);
		}
		catch (DateTimeParseException exception) {
			return context.reportInputMismatch(this, "time does not exist");
		}
	}

}
