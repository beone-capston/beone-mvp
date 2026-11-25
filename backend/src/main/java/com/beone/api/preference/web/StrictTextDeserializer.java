package com.beone.api.preference.web;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * 문자열 필드를 JSON 문자열로만 받는다. 숫자·불리언을 문자열로 바꿔 받지 않고 역직렬화 오류(400 {@code INVALID_REQUEST},
 * {@code errors: []})로 만든다. {@code null}은 Jackson이 이 역직렬화기를 거치지 않고 그대로 넘긴다.
 *
 * <p>공유 Jackson 설정을 바꾸지 않고 결제 전 설정 요청 필드에만 붙인다. 오류 메시지에 입력값을 넣지 않는다.
 */
public class StrictTextDeserializer extends ValueDeserializer<String> {

	@Override
	public String deserialize(JsonParser parser, DeserializationContext context) {
		if (parser.currentToken() != JsonToken.VALUE_STRING) {
			return context.reportInputMismatch(this, "value must be a string");
		}
		return parser.getString();
	}

}
