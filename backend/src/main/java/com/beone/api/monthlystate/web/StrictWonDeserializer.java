package com.beone.api.monthlystate.web;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * 원 단위 금액을 JSON 정수 토큰으로만 받는다. 소수·문자열·{@code long} 범위 밖 정수는 역직렬화 오류(400 {@code INVALID_REQUEST})다
 * (docs/api/monthly-states.md). 음수 허용 여부는 필드 제약이 정한다.
 *
 * <p>공유 Jackson 설정을 바꾸지 않고 월간 상태 요청 필드에만 붙인다. 오류 메시지에 입력값을 넣지 않는다.
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
