package com.beone.api.recommendation.web;

import java.time.LocalDate;
import java.util.List;

import com.beone.api.recommendation.application.InvalidationReason;
import com.beone.api.recommendation.application.RecommendationDocument;
import com.beone.api.recommendation.application.RecommendationView;

/**
 * 추천 결과 조회 응답(docs/api/recommendations.md). 계산 응답과 같은 형식에 {@code invalidated}·{@code invalidatedReason}만 더한다.
 *
 * @param recommendationId 추천 ID
 * @param orderId 주문 ID
 * @param calculatedAt 계산 시각
 * @param decisionDate 판정일
 * @param selectionMode 결제 선택 방식
 * @param outcome 결과
 * @param conditional 조건부 추천
 * @param selected 고른 카드
 * @param comparedCards 비교한 카드
 * @param excludedCards 제외한 카드
 * @param fixedCardComparison 대표 카드 고정 비교
 * @param inputSnapshot 계산에 쓴 입력
 * @param notes 안내 문구
 * @param invalidated 승인 근거로 쓸 수 없게 됐으면 참
 * @param invalidatedReason 무효가 된 이유
 */
public record RecommendationDetailResponse(String recommendationId, String orderId, String calculatedAt,
		LocalDate decisionDate, String selectionMode, String outcome, boolean conditional,
		RecommendationDocument.Selected selected, List<RecommendationDocument.ComparedCard> comparedCards,
		List<RecommendationDocument.ExcludedCard> excludedCards,
		RecommendationDocument.FixedCardComparison fixedCardComparison,
		RecommendationDocument.InputSnapshot inputSnapshot, List<String> notes, boolean invalidated,
		InvalidationReason invalidatedReason) {

	static RecommendationDetailResponse from(RecommendationView view) {
		RecommendationDocument d = view.document();
		return new RecommendationDetailResponse(d.recommendationId(), d.orderId(), d.calculatedAt(), d.decisionDate(),
				d.selectionMode(), d.outcome(), d.conditional(), d.selected(), d.comparedCards(), d.excludedCards(),
				d.fixedCardComparison(), d.inputSnapshot(), d.notes(), view.invalidated(), view.invalidatedReason());
	}

}
