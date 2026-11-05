package com.beone.api.benefit.engine;

import java.util.List;

import com.beone.api.benefit.result.SelectionBasis;

/**
 * D-12로 고른 카드. 대표 카드 고정이어도 최적 카드이며, 승인 카드는 모의 승인이 설정으로 정한다(D-22).
 *
 * <p>비교 카드가 한 장이면 {@code decidedBy}와 {@code gapToRunnerUpWon}이 null이다. 이때 "최적"이라고 표시하지 않는다.
 *
 * @param userCardId 보유 카드 ID
 * @param cardProductId 규칙 카드 ID
 * @param decidedBy {@link SelectionBasis} 이름
 * @param arbitraryTieBreak 임의 규칙으로 정했으면 참
 * @param tiedUserCardIds 임의 규칙으로 정했을 때의 동률 카드
 * @param gapToRunnerUpWon 차선 카드와의 월간 합계 차이
 */
public record SelectedCard(String userCardId, String cardProductId, String decidedBy, boolean arbitraryTieBreak,
		List<String> tiedUserCardIds, Long gapToRunnerUpWon) {

	public SelectedCard {
		EngineChecks.requireToken(userCardId, "userCardId");
		EngineChecks.requireToken(cardProductId, "cardProductId");
		tiedUserCardIds = EngineChecks.copy(tiedUserCardIds, "tiedUserCardIds");
		EngineChecks.requireDistinct(tiedUserCardIds, "tiedUserCardIds");
		EngineChecks.requireOptionalNonNegative(gapToRunnerUpWon, "gapToRunnerUpWon");
		if (decidedBy == null) {
			if (arbitraryTieBreak || !tiedUserCardIds.isEmpty() || gapToRunnerUpWon != null) {
				throw new IllegalArgumentException("a single compared card has no basis");
			}
		}
		else {
			EngineChecks.requireEnumName(SelectionBasis.class, decidedBy, "decidedBy");
			SelectionBasis basis = SelectionBasis.valueOf(decidedBy);
			if (arbitraryTieBreak != basis.isArbitrary()) {
				throw new IllegalArgumentException("arbitraryTieBreak must match decidedBy");
			}
			if (gapToRunnerUpWon == null) {
				throw new IllegalArgumentException("gapToRunnerUpWon is required when cards were compared");
			}
			if (arbitraryTieBreak) {
				if (tiedUserCardIds.size() < 2 || !tiedUserCardIds.contains(userCardId) || gapToRunnerUpWon != 0) {
					throw new IllegalArgumentException("a tie lists the selected card among two or more with no gap");
				}
			}
			else if (!tiedUserCardIds.isEmpty()) {
				throw new IllegalArgumentException("tied cards are listed only for an arbitrary basis");
			}
			if (basis == SelectionBasis.MONTHLY_TOTAL && gapToRunnerUpWon == 0) {
				throw new IllegalArgumentException("a monthly total decision has a positive gap");
			}
		}
	}

}
