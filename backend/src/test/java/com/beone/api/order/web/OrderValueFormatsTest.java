package com.beone.api.order.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

/**
 * 응답 값 형식(docs/api.md: 날짜 YYYY-MM-DD, 시간 HH:mm:ss, 시점 +09:00 ISO-8601)을 확인한다. 초가 0이어도 생략하지 않는다.
 */
class OrderValueFormatsTest {

	@Test
	void formatsContractShapes() {
		assertThat(OrderValueFormats.date(LocalDate.of(2026, 11, 3))).isEqualTo("2026-11-03");
		assertThat(OrderValueFormats.time(LocalTime.of(10, 40))).isEqualTo("10:40:00");
		assertThat(OrderValueFormats.time(null)).isNull();
		assertThat(OrderValueFormats.seoulInstant(LocalDateTime.of(2026, 11, 3, 10, 40)))
			.isEqualTo("2026-11-03T10:40:00+09:00");
	}

}
