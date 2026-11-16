package com.beone.api.order.web;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * 원 단위 금액을 JSON 정수 토큰으로만 받는다. 소수({@code 10000.5}, {@code 10000.0})·문자열({@code "10000"})·
 * {@code long} 범위 밖 정수는 역직렬화 오류(400 {@code INVALID_REQUEST})다(docs/api/orders.md).
 *
 * <p>공유 Jackson 설정을 바꾸지 않고 주문 DTO 필드에만 붙인다(결정 27). 오류 메시지에 입력값을 넣지 않는다.
 */
public class StrictWonDeserializer extends ValueDeserializer<Long> {

	@Override
	public Long deserialize(JsonParser parser, DeserializationContext context) {
		if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
			return context.reportInputMismatch(this, "amount must be a JSON integer");
		}
		if (parser.getNumberType() != JsonParser.NumberType.INT
				&& parser.getNumberType() != JsonParser.NumberType.LONG) {
			return context.reportInputMismatch(this, "amount is out of range");
		}
		return parser.getLongValue();
	}

}
