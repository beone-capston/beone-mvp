package com.beone.api.codef.web;

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
 * 날짜를 {@code YYYY-MM-DD} 문자열로만 받는다. 다른 모양·없는 날짜({@code 2026-02-30})·숫자는 역직렬화 오류(400
 * {@code INVALID_REQUEST})다(docs/api.md 값 형식). 오류 메시지에 입력값을 넣지 않는다. 다른 기능 패키지에 의존하지 않으려고
 * {@code monthlystate.web}의 같은 이름 클래스를 옮겨 왔다.
 */
class StrictDateDeserializer extends ValueDeserializer<LocalDate> {

	private static final Pattern FORMAT = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd")
		.withResolverStyle(ResolverStyle.STRICT);

	@Override
	public LocalDate deserialize(JsonParser parser, DeserializationContext context) {
		if (parser.currentToken() != JsonToken.VALUE_STRING) {
			return context.reportInputMismatch(this, "date must be a string");
		}
		String text = parser.getString();
		if (!FORMAT.matcher(text).matches()) {
			return context.reportInputMismatch(this, "date must be YYYY-MM-DD");
		}
		try {
			return LocalDate.parse(text, DATE);
		}
		catch (DateTimeParseException ex) {
			return context.reportInputMismatch(this, "date does not exist");
		}
	}

}
