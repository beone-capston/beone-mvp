package com.beone.api.benefit.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;

import com.beone.api.benefit.money.Won;
import org.junit.jupiter.api.Test;

class PlannedSpendingsTest {

	@Test
	void acceptsZeroToFiveItems() {
		assertThat(PlannedSpendings.none().items()).isEmpty();
		assertThat(new PlannedSpendings(items(5)).items()).hasSize(5);
	}

	@Test
	void rejectsSixItems() {
		assertThatThrownBy(() -> new PlannedSpendings(items(6))).hasMessageContaining("at most 5");
	}

	@Test
	void rejectsNonPositiveAmount() {
		PlannedSpending valid = InputTestData.planned(20);

		assertThatThrownBy(() -> new PlannedSpending(valid.date(), valid.time(), Won.ZERO,
				valid.merchantClass(), valid.merchantName(), valid.paymentMethod()))
			.hasMessageContaining("positive");
	}

	private static List<PlannedSpending> items(int count) {
		return IntStream.rangeClosed(1, count).mapToObj(day -> InputTestData.planned(15 + day)).toList();
	}

}
