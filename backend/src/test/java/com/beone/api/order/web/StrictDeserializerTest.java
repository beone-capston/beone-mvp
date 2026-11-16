package com.beone.api.order.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;

import com.beone.api.order.application.PaymentMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 주문 DTO의 엄격 역직렬화(docs/api/orders.md: 소수·문자열 금액 400, docs/api.md 값 형식)를 Spring 없이 확인한다.
 */
class StrictDeserializerTest {

	private final JsonMapper mapper = JsonMapper.builder().build();

	@Test
	void acceptsContractValues() {
		CreateOrderRequest request = read("""
				{"amountWon":10000,"orderDate":"2026-11-03","orderTime":"10:40:00","merchantName":"스타벅스 송도점",
				 "merchantClass":"STARBUCKS_STORE","paymentMethod":"DIRECT"}
				""");

		assertThat(request.amountWon()).isEqualTo(10000L);
		assertThat(request.orderDate()).isEqualTo(LocalDate.of(2026, 11, 3));
		assertThat(request.orderTime()).isEqualTo(LocalTime.of(10, 40, 0));
	}

	@Test
	void missingOrNullTimeStaysNull() {
		assertThat(read(body("10000", "\"2026-11-03\"", null)).orderTime()).isNull();
		assertThat(read(body("10000", "\"2026-11-03\"", "null")).orderTime()).isNull();
	}

	@Test
	void nonPositiveIntegerIsLeftToBeanValidation() {
		assertThat(read(body("0", "\"2026-11-03\"", null)).amountWon()).isZero();
		assertThat(read(body("-1", "\"2026-11-03\"", null)).amountWon()).isEqualTo(-1L);
	}

	@ParameterizedTest
	@ValueSource(strings = { "10000.5", "10000.0", "1e4", "\"10000\"", "true", "[10000]", "{}",
			"9223372036854775808" })
	void rejectsNonIntegerAmount(String amount) {
		assertThatThrownBy(() -> read(body(amount, "\"2026-11-03\"", null))).isInstanceOf(DatabindException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "\"2026-11-3\"", "\"2026/11/03\"", "\"20261103\"", "\"2026-02-30\"", "\"2026-13-01\"",
			"\"2026-11-03T10:40:00\"", "\"+2026-11-03\"", "[2026,11,3]", "20261103", "\"\"" })
	void rejectsNonIsoDate(String date) {
		assertThatThrownBy(() -> read(body("10000", date, null))).isInstanceOf(DatabindException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "\"10:40\"", "\"10:40:00.5\"", "\"24:00:00\"", "\"10:60:00\"", "\"1:40:00\"",
			"\"10:40:00+09:00\"", "[10,40,0]", "37200", "\"\"" })
	void rejectsNonClockTime(String time) {
		assertThatThrownBy(() -> read(body("10000", "\"2026-11-03\"", time))).isInstanceOf(DatabindException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "1", "2", "\"direct\"", "\" DIRECT\"", "\"CARD\"", "\"\"", "true", "[\"DIRECT\"]" })
	void rejectsNonNamePaymentMethod(String method) {
		String json = body("10000", "\"2026-11-03\"", null).replace("\"DIRECT\"", method);

		assertThatThrownBy(() -> read(json)).isInstanceOf(DatabindException.class);
	}

	@Test
	void acceptsEachPaymentMethodName() {
		for (PaymentMethod method : PaymentMethod.values()) {
			String json = body("10000", "\"2026-11-03\"", null).replace("DIRECT", method.name());
			assertThat(read(json).paymentMethod()).isEqualTo(method);
		}
	}

	private CreateOrderRequest read(String json) {
		return this.mapper.readValue(json, CreateOrderRequest.class);
	}

	private static String body(String amount, String date, String time) {
		String timePart = (time == null) ? "" : ",\"orderTime\":" + time;
		return "{\"amountWon\":" + amount + ",\"orderDate\":" + date + timePart
				+ ",\"merchantClass\":\"STARBUCKS_STORE\",\"paymentMethod\":\"DIRECT\"}";
	}

}
