package com.beone.api.benefit.input;

import java.time.LocalDate;
import java.util.Objects;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.state.StateSource;
import com.beone.api.benefit.state.StateValue;

/**
 * 포트폴리오의 보유 카드. {@code holdingSource}로 가상 카드와 실제 카드를 구분한다(PRD §3, D-04).
 *
 * @param registeredAt 카드 등록일. 신규 유예 판정에 쓴다
 */
public record HeldCard(CardId cardId, StateSource holdingSource, StateValue<LocalDate> registeredAt) {

	public HeldCard {
		Objects.requireNonNull(cardId, "cardId");
		Objects.requireNonNull(holdingSource, "holdingSource");
		if (holdingSource == StateSource.INFERRED || holdingSource == StateSource.UNKNOWN) {
			throw new IllegalArgumentException("card holding must come from CODEF, MANUAL or SYNTHETIC, not "
					+ holdingSource);
		}
		Objects.requireNonNull(registeredAt, "registeredAt");
	}

}
