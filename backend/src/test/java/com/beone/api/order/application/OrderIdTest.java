package com.beone.api.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 주문·예정 소비 ID의 외부 표현(결정 27: 접두 + 자동 증가 번호, 선행 0 없음)을 확인한다.
 */
class OrderIdTest {

	@Test
	void formatsWithPrefix() {
		assertThat(OrderId.format(5001)).isEqualTo("ord-5001");
		assertThat(OrderId.format(1)).isEqualTo("ord-1");
		assertThat(PlannedSpendId.format(1)).isEqualTo("ps-1");
	}

	@Test
	void parsesWellFormedValue() {
		assertThat(OrderId.parse("ord-5001")).contains(5001L);
		assertThat(OrderId.parse("ord-" + Long.MAX_VALUE)).contains(Long.MAX_VALUE);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "5001", "ord-", "ord-0", "ord-007", "ord-abc", "ORD-5001", "ord-5001 ", " ord-5001",
			"ord--1", "ord-1.0", "ps-1", "ord-9223372036854775808", "ord-99999999999999999999" })
	void rejectsMalformedValue(String value) {
		assertThat(OrderId.parse(value)).isEmpty();
	}

	@Test
	void rejectsNonPositiveStorageId() {
		assertThatIllegalArgumentException().isThrownBy(() -> OrderId.format(0));
		assertThatIllegalArgumentException().isThrownBy(() -> PlannedSpendId.format(0));
	}

}
