package com.beone.api.benefit.input;

import java.util.Objects;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.state.BasisPeriod;

/**
 * 원장이 카드의 해당 기간 거래를 빠짐없이 갖고 있다는 선언.
 * 선언되지 않은 기간은 완전하다고 보지 않으므로, 그 기간에서 산출한 값은 {@code UNKNOWN}으로 둔다(D-04).
 */
public record LedgerCoverage(CardId cardId, BasisPeriod period) {

	public LedgerCoverage {
		Objects.requireNonNull(cardId, "cardId");
		Objects.requireNonNull(period, "period");
	}

}
