package com.beone.api.benefit.input;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.beone.api.benefit.card.CardId;

/**
 * 추천 한 번에 필요한 입력 전체.
 *
 * 생성 시 보유 카드 중복, 동일 카드의 월간 상태 중복, 비보유 카드의 월간 상태, 보유하지 않은 대표 카드를 거부한다.
 *
 * @param referenceDate 상태를 판단하는 기준일(시나리오 시계). 주문일은 이보다 늦을 수 있다
 */
public record RecommendationInput(LocalDate referenceDate, Order order, List<HeldCard> cards, Ledger ledger,
		List<CardMonthlyState> states, PlannedSpendings plannedSpendings, SelectionPreference preference) {

	public RecommendationInput {
		Objects.requireNonNull(referenceDate, "referenceDate");
		Objects.requireNonNull(order, "order");
		cards = List.copyOf(cards);
		Objects.requireNonNull(ledger, "ledger");
		states = List.copyOf(states);
		Objects.requireNonNull(plannedSpendings, "plannedSpendings");
		Objects.requireNonNull(preference, "preference");
		if (cards.isEmpty()) {
			throw new IllegalArgumentException("at least one held card is required");
		}
		Set<CardId> held = new HashSet<>();
		for (HeldCard card : cards) {
			if (!held.add(card.cardId())) {
				throw new IllegalArgumentException("card " + card.cardId() + " is held twice");
			}
		}
		Set<CardId> withState = new HashSet<>();
		for (CardMonthlyState state : states) {
			if (!held.contains(state.cardId())) {
				throw new IllegalArgumentException("monthly state given for card " + state.cardId() + " that is not held");
			}
			if (!withState.add(state.cardId())) {
				throw new IllegalArgumentException("monthly state given twice for card " + state.cardId());
			}
		}
		preference.representativeCard().ifPresent(card -> {
			if (!held.contains(card)) {
				throw new IllegalArgumentException("representative card " + card + " is not held");
			}
		});
	}

}
