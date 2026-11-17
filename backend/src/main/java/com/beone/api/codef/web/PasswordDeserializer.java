package com.beone.api.codef.web;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * 비밀번호를 JSON 문자열로만 받아 {@code char[]}로 만든다. 서비스가 쓰고 나서 지울 수 있게 문자열로 보관하지 않는다.
 *
 * <p>숫자·불리언 등은 역직렬화 오류(400 {@code INVALID_REQUEST}, {@code errors: []})다. 오류 메시지에 입력값을 넣지 않는다.
 */
public class PasswordDeserializer extends ValueDeserializer<char[]> {

	@Override
	public char[] deserialize(JsonParser parser, DeserializationContext context) {
		if (parser.currentToken() != JsonToken.VALUE_STRING) {
			return context.reportInputMismatch(this, "value must be a string");
		}
		return parser.getString().toCharArray();
	}

}
