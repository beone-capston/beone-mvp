package com.beone.api.payment.application;

import static com.beone.api.payment.application.PaymentFixtures.auto;
import static com.beone.api.payment.application.PaymentFixtures.compared;
import static com.beone.api.payment.application.PaymentFixtures.selected;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.beone.api.recommendation.application.InvalidationReason;
import com.beone.api.recommendation.application.RecommendationDocument;
import com.beone.api.recommendation.application.RecommendationView;
import org.junit.jupiter.api.Test;

/**
 * 본 추천과 재계산의 비교(D-14 ⑥⑦, D-23-3). 차이 형식은 계약 응답 필드 설명의 예
 * {@code {"field":"selected.userCardId","before":"uc-1001","after":"uc-1003","reason":"MONTHLY_STATE_CHANGED"}}에서 옮겼다.
 */
class RecalculationComparisonTest {

	private static final RecommendationDocument SEEN = auto("rec-3001", selected("uc-1001", "09174", null),
			List.of(compared("uc-1001", "09174", 2100L, List.of())), List.of(), false);

	@Test
	void withoutSeenRecommendationNothingIsComparedAndChangedIsNull() {
		ApprovalDocument.Recalculation recalculation = RecalculationComparison.compare(null, SEEN);

		assertThat(recalculation)
			.isEqualTo(new ApprovalDocument.Recalculation("rec-3001", null, null, List.of()));
	}

	@Test
	void sameSelectionAndBenefitIsNotChanged() {
		RecommendationDocument now = auto("rec-3002", selected("uc-1001", "09174", null),
				List.of(compared("uc-1001", "09174", 2100L, List.of())), List.of(), false);

		ApprovalDocument.Recalculation recalculation = RecalculationComparison
			.compare(new RecommendationView(SEEN, false, null), now);

		assertThat(recalculation)
			.isEqualTo(new ApprovalDocument.Recalculation("rec-3002", "rec-3001", false, List.of()));
	}

	@Test
	void invalidatedSeenRecommendationIsComparedWithItsReason() {
		RecommendationDocument now = auto("rec-3002", selected("uc-1003", "01664", null),
				List.of(compared("uc-1003", "01664", 1200L, List.of())), List.of(), false);

		ApprovalDocument.Recalculation recalculation = RecalculationComparison
			.compare(new RecommendationView(SEEN, true, InvalidationReason.MONTHLY_STATE_CHANGED), now);

		assertThat(recalculation.changed()).isTrue();
		assertThat(recalculation.differences()).containsExactly(
				new ApprovalDocument.Difference("selected.userCardId", "uc-1001", "uc-1003", "MONTHLY_STATE_CHANGED"),
				new ApprovalDocument.Difference("selected.currentOrderBenefit.amountWon", 2100L, 1200L,
						"MONTHLY_STATE_CHANGED"));
	}

	@Test
	void becomingUndeterminedKeepsNullInsteadOfZero() {
		RecommendationDocument now = auto("rec-3002", null, List.of(), List.of(), false);

		ApprovalDocument.Recalculation recalculation = RecalculationComparison
			.compare(new RecommendationView(SEEN, false, null), now);

		assertThat(recalculation.differences()).containsExactly(
				new ApprovalDocument.Difference("selected.userCardId", "uc-1001", null, null),
				new ApprovalDocument.Difference("selected.currentOrderBenefit.amountWon", 2100L, null, null));
	}

}
