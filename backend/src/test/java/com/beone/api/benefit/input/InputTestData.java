package com.beone.api.benefit.input;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.card.MerchantClass;
import com.beone.api.benefit.card.PaymentMethod;
import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.state.BasisPeriod;
import com.beone.api.benefit.state.StateSource;
import com.beone.api.benefit.state.StateValue;

/**
 * 입력 모델 테스트용 합성 데이터 생성 도구.
 */
final class InputTestData {

	static final LocalDate ORDER_DATE = LocalDate.of(2026, 10, 15);

	private InputTestData() {
	}

	static Order order(long amount) {
		return new Order(Optional.empty(), Optional.empty(), Won.of(amount), ORDER_DATE, Optional.empty(),
				MerchantClass.of("STARBUCKS_STORE"), PaymentMethod.DIRECT);
	}

	static PlannedSpending planned(int day) {
		return new PlannedSpending(LocalDate.of(2026, 10, day), Optional.empty(), Won.of(20_000),
				MerchantClass.of("STARBUCKS_STORE"), Optional.empty(), PaymentMethod.DIRECT);
	}

	static HeldCard held(String id) {
		return new HeldCard(CardId.of(id), StateSource.SYNTHETIC,
				StateValue.synthetic(LocalDate.of(2026, 1, 1), BasisPeriod.day(ORDER_DATE)));
	}

	static CardMonthlyState state(String id) {
		return new CardMonthlyState(CardId.of(id), Optional.empty(), Map.of(),
				StateValue.synthetic(Won.ZERO, BasisPeriod.month(YearMonth.of(2026, 10))), List.of());
	}

	static Ledger emptyLedger() {
		return new Ledger(LedgerSource.SIMULATION, List.of(), List.of());
	}

	static RecommendationInput input(List<HeldCard> cards, List<CardMonthlyState> states, Ledger ledger,
			SelectionPreference preference) {
		return new RecommendationInput(ORDER_DATE, order(10_000), cards, ledger, states, PlannedSpendings.none(),
				preference);
	}

}
