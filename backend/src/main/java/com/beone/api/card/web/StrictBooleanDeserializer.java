package com.beone.api.card.web;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * 참·거짓 값을 JSON {@code true}·{@code false} 토큰으로만 받는다. 문자열({@code "true"})·숫자({@code 1})는 역직렬화 오류(400
 * {@code INVALID_REQUEST})다.
 *
 * <p>공유 Jackson 설정을 바꾸지 않고 보유 카드 요청 필드에만 붙인다. 오류 메시지에 입력값을 넣지 않는다.
 */
public class StrictBooleanDeserializer extends ValueDeserializer<Boolean> {

	@Override
	public Boolean deserialize(JsonParser parser, DeserializationContext context) {
		JsonToken token = parser.currentToken();
		if (token == JsonToken.VALUE_TRUE) {
			return Boolean.TRUE;
		}
		if (token == JsonToken.VALUE_FALSE) {
			return Boolean.FALSE;
		}
		return context.reportInputMismatch(this, "value must be a JSON boolean");
	}

}
