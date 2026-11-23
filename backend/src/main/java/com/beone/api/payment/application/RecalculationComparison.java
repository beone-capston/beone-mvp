package com.beone.api.payment.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.beone.api.recommendation.application.RecommendationDocument;
import com.beone.api.recommendation.application.RecommendationView;

/**
 * 사용자가 본 추천과 승인 직전 재계산을 비교한다(D-14 ⑥⑦, D-23-3). 무효가 된 추천이어도 막지 않고 사유를 담는다.
 *
 * <p>비교하는 값은 계약 문구 "선택 카드·혜택"의 두 가지다.
 * <ul>
 * <li>{@code selected.userCardId}: 고른 카드. 확정 불가면 {@code null}</li>
 * <li>{@code selected.currentOrderBenefit.amountWon}: 고른 카드의 현재 주문 혜택. 미확정이면 {@code null}(0으로 바꾸지 않음)</li>
 * </ul>
 * {@code reason}은 본 추천의 무효화 사유({@code MAPPING_CHANGED}·{@code MONTHLY_STATE_CHANGED})이고, 무효가 아닌데 값이 다르면
 * {@code null}이다. 본 추천을 보내지 않으면 비교하지 않고 {@code changed=null}이다.
 */
final class RecalculationComparison {

	static final String SELECTED_CARD = "selected.userCardId";

	static final String SELECTED_BENEFIT = "selected.currentOrderBenefit.amountWon";

	private RecalculationComparison() {
	}

	static ApprovalDocument.Recalculation compare(RecommendationView seen, RecommendationDocument recalculated) {
		if (seen == null) {
			return new ApprovalDocument.Recalculation(recalculated.recommendationId(), null, null, List.of());
		}
		String reason = (seen.invalidatedReason() == null) ? null : seen.invalidatedReason().name();
		RecommendationDocument before = seen.document();
		List<ApprovalDocument.Difference> differences = new ArrayList<>();
		String beforeCard = selectedCard(before);
		String afterCard = selectedCard(recalculated);
		if (!Objects.equals(beforeCard, afterCard)) {
			differences.add(new ApprovalDocument.Difference(SELECTED_CARD, beforeCard, afterCard, reason));
		}
		Long beforeBenefit = selectedBenefit(before);
		Long afterBenefit = selectedBenefit(recalculated);
		if (!Objects.equals(beforeBenefit, afterBenefit)) {
			differences.add(new ApprovalDocument.Difference(SELECTED_BENEFIT, beforeBenefit, afterBenefit, reason));
		}
		return new ApprovalDocument.Recalculation(recalculated.recommendationId(), before.recommendationId(),
				!differences.isEmpty(), List.copyOf(differences));
	}

	private static String selectedCard(RecommendationDocument document) {
		return (document.selected() == null) ? null : document.selected().userCardId();
	}

	private static Long selectedBenefit(RecommendationDocument document) {
		String card = selectedCard(document);
		if (card == null) {
			return null;
		}
		return document.comparedCards()
			.stream()
			.filter(compared -> compared.userCardId().equals(card))
			.findFirst()
			.map(compared -> compared.currentOrderBenefit().amountWon())
			.orElse(null);
	}

}
