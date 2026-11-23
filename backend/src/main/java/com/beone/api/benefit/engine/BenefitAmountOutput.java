package com.beone.api.benefit.engine;

import java.util.Set;

/**
 * 혜택 금액과 상태. 미확정이면 금액이 null이고 0으로 바꾸지 않는다.
 *
 * @param amountWon 확정 금액. 0 이상
 * @param status {@code CONFIRMED}·{@code NEEDS_CONFIRMATION}
 */
public record BenefitAmountOutput(Long amountWon, String status) {

	public BenefitAmountOutput {
		EngineChecks.requireOneOf(Set.of("CONFIRMED", "NEEDS_CONFIRMATION"), status, "status");
		EngineChecks.requireOptionalNonNegative(amountWon, "amountWon");
		if ("CONFIRMED".equals(status) != (amountWon != null)) {
			throw new IllegalArgumentException("amountWon is present exactly when the status is CONFIRMED");
		}
	}

	public static BenefitAmountOutput confirmed(long amountWon) {
		return new BenefitAmountOutput(amountWon, "CONFIRMED");
	}

	public static BenefitAmountOutput needsConfirmation() {
		return new BenefitAmountOutput(null, "NEEDS_CONFIRMATION");
	}

	boolean isConfirmed() {
		return amountWon != null;
	}

}
