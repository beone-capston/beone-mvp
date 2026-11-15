package com.beone.api.benefit.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BenefitAmountTest {

	@Test
	void unconfirmedIsNeverEqualToZeroWon() {
		BenefitAmount unknown = BenefitAmount.needsConfirmation("prior performance is UNKNOWN");
		BenefitAmount zero = BenefitAmount.confirmed(0);

		assertThat(unknown).isNotEqualTo(zero);
		assertThat(zero).isNotEqualTo(unknown);
		assertThat(unknown.isConfirmed()).isFalse();
		assertThat(zero.isConfirmed()).isTrue();
	}

	@Test
	void unconfirmedHasNoZeroFallback() {
		BenefitAmount unknown = BenefitAmount.needsConfirmation("rule expired");

		assertThatThrownBy(unknown::confirmedWon).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("rule expired");
	}

	@Test
	void confirmedAmountMustNotBeNegative() {
		assertThatThrownBy(() -> BenefitAmount.confirmed(-1)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void unconfirmedNeedsAReason() {
		assertThatThrownBy(() -> BenefitAmount.needsConfirmation(" ")).isInstanceOf(IllegalArgumentException.class);
	}

}
