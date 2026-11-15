package com.beone.api.benefit.result;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.beone.api.benefit.card.CardId;

/**
 * 추천 입력 하나에 대한 카드별 결과와 선택 결과.
 */
public record Recommendation(List<CardBenefit> cardBenefits, RecommendationOutcome outcome) {

	public Recommendation {
		cardBenefits = List.copyOf(cardBenefits);
		Objects.requireNonNull(outcome, "outcome");
		if (cardBenefits.isEmpty()) {
			throw new IllegalArgumentException("a recommendation needs at least one card result");
		}
		Set<CardId> cards = new HashSet<>();
		for (CardBenefit benefit : cardBenefits) {
			if (!cards.add(benefit.cardId())) {
				throw new IllegalArgumentException("card " + benefit.cardId() + " has two results");
			}
		}
		if (outcome instanceof RecommendationOutcome.Selected selected) {
			if (!cards.contains(selected.card()) || !cards.containsAll(selected.tiedCards())) {
				throw new IllegalArgumentException("selected and tied cards must have results");
			}
			CardBenefit chosen = cardBenefits.stream()
				.filter(benefit -> benefit.cardId().equals(selected.card()))
				.findFirst()
				.orElseThrow();
			if (!chosen.monthlyTotal().isConfirmed()) {
				throw new IllegalArgumentException("card " + selected.card() + " cannot be selected with an unconfirmed monthly total");
			}
		}
	}

}
