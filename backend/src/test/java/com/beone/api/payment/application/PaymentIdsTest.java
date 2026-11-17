package com.beone.api.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * 외부 ID 형식({@code pay-7001}·{@code cn-8001}, 계약 예시). 형식이 틀리면 비어 있어 404로 다룬다.
 */
class PaymentIdsTest {

	@Test
	void formatsAndParsesContractExamples() {
		assertThat(PaymentIds.SIMULATED_PAYMENT.format(7001)).isEqualTo("pay-7001");
		assertThat(PaymentIds.CANCELLATION.format(8001)).isEqualTo("cn-8001");
		assertThat(PaymentIds.SIMULATED_PAYMENT.parse("pay-7001")).contains(7001L);
		assertThat(PaymentIds.CANCELLATION.parse("cn-8001")).contains(8001L);
	}

	@Test
	void rejectsOtherFormats() {
		for (String value : new String[] { null, "", "pay-", "pay-0", "pay-07001", "PAY-1", "cn-1x", "pay-99999999999999999999",
				"cn-7001 " }) {
			assertThat(PaymentIds.SIMULATED_PAYMENT.parse(value)).as(String.valueOf(value)).isEqualTo(Optional.empty());
		}
		assertThat(PaymentIds.SIMULATED_PAYMENT.parse("cn-1")).isEmpty();
		assertThatThrownBy(() -> PaymentIds.CANCELLATION.format(0)).isInstanceOf(IllegalArgumentException.class);
	}

}
