package com.beone.api.order.web;

import com.beone.api.order.application.PaymentMethod;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * 결제 방식을 이름 문자열로만 받는다. 숫자(열거 순번)·다른 대소문자·허용 밖 이름은 역직렬화 오류(400 {@code INVALID_REQUEST})다.
 *
 * <p>Jackson 기본 설정은 숫자를 열거 순번으로 바꿔 받을 수 있어, 상수 순서가 바뀌면 같은 입력이 다른 결제 방식이 된다. 공유 설정을
 * 바꾸지 않고 주문 DTO 필드에만 붙인다(결정 27). 오류 메시지에 입력값을 넣지 않는다.
 */
public class StrictPaymentMethodDeserializer extends ValueDeserializer<PaymentMethod> {

	@Override
	public PaymentMethod deserialize(JsonParser parser, DeserializationContext context) {
		if (parser.currentToken() != JsonToken.VALUE_STRING) {
			return context.reportInputMismatch(this, "payment method must be a string");
		}
		String text = parser.getString();
		for (PaymentMethod method : PaymentMethod.values()) {
			if (method.name().equals(text)) {
				return method;
			}
		}
		return context.reportInputMismatch(this, "payment method is not allowed");
	}

}
