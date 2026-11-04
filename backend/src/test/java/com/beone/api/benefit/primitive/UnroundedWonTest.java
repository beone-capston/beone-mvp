package com.beone.api.benefit.primitive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.rule.FractionalWonPolicy;

class UnroundedWonTest {

	@Test
	void exactAmountHasNoFraction() {
		UnroundedWon exact = UnroundedWon.exact(Won.of(2_500));

		assertThat(exact.hasFractionalWon()).isFalse();
		assertThat(exact.round(FractionalWonPolicy.TRUNCATE)).isEqualTo(Won.of(2_500));
		assertThat(exact.round(FractionalWonPolicy.ROUND_HALF_UP)).isEqualTo(Won.of(2_500));
	}

	@Test
	void truncateDropsFraction() {
		// 12.9999원 → 12원
		assertThat(new UnroundedWon(Won.of(12), 9_999).round(FractionalWonPolicy.TRUNCATE)).isEqualTo(Won.of(12));
	}

	@Test
	void roundHalfUpBoundaryIsHalfWon() {
		// 12.4999원 → 12원, 12.5원 → 13원
		assertThat(new UnroundedWon(Won.of(12), 4_999).round(FractionalWonPolicy.ROUND_HALF_UP)).isEqualTo(Won.of(12));
		assertThat(new UnroundedWon(Won.of(12), 5_000).round(FractionalWonPolicy.ROUND_HALF_UP)).isEqualTo(Won.of(13));
	}

	@Test
	void roundingNeedsExplicitPolicy() {
		UnroundedWon amount = new UnroundedWon(Won.of(12), 5_000);

		assertThat(amount.hasFractionalWon()).isTrue();
		assertThatThrownBy(() -> amount.round(null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void roundingUpBeyondLongRangeFailsInsteadOfWrapping() {
		UnroundedWon largest = new UnroundedWon(Won.of(Long.MAX_VALUE), 5_000);

		assertThat(largest.round(FractionalWonPolicy.TRUNCATE)).isEqualTo(Won.of(Long.MAX_VALUE));
		assertThatThrownBy(() -> largest.round(FractionalWonPolicy.ROUND_HALF_UP))
			.isInstanceOf(ArithmeticException.class);
	}

	@Test
	void rejectsNegativeOrOutOfRangeParts() {
		assertThatThrownBy(() -> new UnroundedWon(Won.of(-1), 0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new UnroundedWon(Won.of(1), -1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new UnroundedWon(Won.of(1), 10_000)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new UnroundedWon(null, 0)).isInstanceOf(NullPointerException.class);
	}

}
