package com.beone.api.benefit.result;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.money.Won;

/**
 * 카드 선택 여부와 선택 기준.
 */
public sealed interface RecommendationOutcome {

	/**
	 * 카드를 선택한 결과.
	 *
	 * @param basis 선택을 결정한 D-12 단계. 비교한 카드가 한 장이면 비어 있다
	 * @param tiedCards 모든 혜택 단계에서 같은 카드들. 임의 규칙 단계로 정해졌을 때만 적는다(D-12)
	 * @param marginOverRunnerUp 차선 카드와의 월간 혜택 차이. 확정된 경우에만 있다
	 * @param conditional 미확인 상태 때문에 선택이 바뀔 수 있으면 참. 이때는 확정 최적 추천으로 표시하지 않는다(PRD FR-05, D-03).
	 * 바뀔 수 있는지는 계산 단계가 판단하고 모델은 값만 보관한다.
	 */
	record Selected(CardId card, Optional<SelectionBasis> basis, List<CardId> tiedCards,
			Optional<Won> marginOverRunnerUp, boolean conditional) implements RecommendationOutcome {

		public Selected {
			Objects.requireNonNull(card, "card");
			Objects.requireNonNull(basis, "basis");
			tiedCards = List.copyOf(tiedCards);
			Objects.requireNonNull(marginOverRunnerUp, "marginOverRunnerUp");
			boolean arbitrary = basis.map(SelectionBasis::isArbitrary).orElse(false);
			if (arbitrary != !tiedCards.isEmpty()) {
				throw new IllegalArgumentException("tied cards are listed exactly when an arbitrary basis decided");
			}
			if (!tiedCards.isEmpty()) {
				if (tiedCards.size() < 2 || Set.copyOf(tiedCards).size() != tiedCards.size()) {
					throw new IllegalArgumentException("a tie needs at least two distinct cards");
				}
				if (!tiedCards.contains(card)) {
					throw new IllegalArgumentException("the selected card must be one of the tied cards");
				}
			}
			marginOverRunnerUp.ifPresent(margin -> {
				if (margin.isNegative()) {
					throw new IllegalArgumentException("margin over the runner-up must not be negative");
				}
			});
		}

	}

	/**
	 * 추천할 카드를 정하지 못한 결과(예: 쓸 수 있는 규칙이 없는 평가 C02).
	 */
	record NotDetermined(String reason) implements RecommendationOutcome {

		public NotDetermined {
			if (reason == null || reason.isBlank()) {
				throw new IllegalArgumentException("reason is required");
			}
		}

	}

}
