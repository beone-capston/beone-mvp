package com.beone.api.benefit.engine;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 입구 계약 시험의 합성 값.
 */
final class EngineTestData {

	static final LocalDate ORDER_DATE = LocalDate.of(2026, 11, 3);

	static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-03T10:00:00+09:00");

	private EngineTestData() {
	}

	static OrderInput order() {
		return new OrderInput("ord-1", "합성 가맹점", 10_000, ORDER_DATE, null, "STARBUCKS_STORE", "DIRECT");
	}

	static MonthlyStateInput emptyState() {
		return new MonthlyStateInput(StateInput.notEntered(), List.of(), StateInput.notEntered(), "UNKNOWN", List.of());
	}

	static HeldCardInput confirmed(String userCardId, String product) {
		return new HeldCardInput(userCardId, "USER_CONFIRMED", product, null, false, "MANUAL", StateInput.notEntered(),
				emptyState());
	}

	static HeldCardInput unmapped(String userCardId) {
		return new HeldCardInput(userCardId, "UNMAPPED", null, null, false, "MANUAL", StateInput.notEntered(), emptyState());
	}

	static AppliedRuleOutput rule() {
		return new AppliedRuleOutput("09174-v1", "VERIFIED", List.of(new OfficialSourceOutput("https://example.test/a.pdf", "p.1")));
	}

	static ComparedCard compared(String userCardId, String product, long amount) {
		return new ComparedCard(userCardId, product, amount == 0 ? "NOT_APPLICABLE_CATEGORY" : "APPLIED", rule(),
				BenefitAmountOutput.confirmed(amount), BenefitAmountOutput.confirmed(amount), List.of(),
				amount == 0 ? "CONFIRMED_NOT_MET" : null, List.of(), List.of());
	}

	static ExcludedCard excluded(String userCardId, String reason) {
		String status = "RULE_UNAVAILABLE".equals(reason) ? "NO_VERIFIED_RULE" : null;
		return new ExcludedCard(userCardId, "RULE_UNAVAILABLE".equals(reason) ? "09271" : null, reason, "합성 사유", null,
				BenefitAmountOutput.needsConfirmation(), status, null, List.of());
	}

	static SelectedCard single(String userCardId, String product) {
		return new SelectedCard(userCardId, product, null, false, List.of(), null);
	}

}
