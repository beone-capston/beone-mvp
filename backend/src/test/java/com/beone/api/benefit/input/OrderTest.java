package com.beone.api.benefit.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import java.util.Optional;

import com.beone.api.benefit.card.MerchantClass;
import com.beone.api.benefit.card.PaymentMethod;
import com.beone.api.benefit.money.Won;
import org.junit.jupiter.api.Test;

class OrderTest {

	@Test
	void timeIsOptionalAndNotInvented() {
		assertThat(InputTestData.order(10_000).time()).isEmpty();
		Order withTime = new Order(Optional.of("ORDER-1"), Optional.of("스타벅스 강남점"), Won.of(10_000),
				InputTestData.ORDER_DATE, Optional.of(LocalTime.of(12, 30)), MerchantClass.of("STARBUCKS_STORE"),
				PaymentMethod.DIRECT);
		assertThat(withTime.time()).contains(LocalTime.of(12, 30));
	}

	@Test
	void rejectsNonPositiveAmount() {
		assertThatThrownBy(() -> InputTestData.order(0)).hasMessageContaining("positive");
		assertThatThrownBy(() -> InputTestData.order(-1)).hasMessageContaining("positive");
	}

	@Test
	void rejectsMissingRequiredValues() {
		assertThatThrownBy(() -> new Order(Optional.empty(), Optional.empty(), Won.of(10_000), null, Optional.empty(),
				MerchantClass.of("CU_STORE"), PaymentMethod.DIRECT))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new Order(Optional.empty(), Optional.empty(), Won.of(10_000), InputTestData.ORDER_DATE,
				Optional.empty(), null, PaymentMethod.DIRECT))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new Order(Optional.empty(), Optional.empty(), Won.of(10_000), InputTestData.ORDER_DATE,
				Optional.empty(), MerchantClass.of("CU_STORE"), null))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> MerchantClass.of(" ")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsBlankOptionalText() {
		assertThatThrownBy(() -> new Order(Optional.of(" "), Optional.empty(), Won.of(10_000), InputTestData.ORDER_DATE,
				Optional.empty(), MerchantClass.of("CU_STORE"), PaymentMethod.DIRECT))
			.hasMessageContaining("order id");
	}

}
