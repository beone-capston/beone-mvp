package com.beone.api.payment.application;

import java.time.LocalDate;
import java.util.List;

import com.beone.api.recommendation.application.RecommendationDocument;

/**
 * 모의 결제 단위 테스트의 추천 본문 재료. 값은 docs/api/recommendations.md 계산 응답 예시(09174 톡톡, 스타벅스 10,000원 → 2,100원)와
 * docs/api/mock-payments.md 승인 응답 예시에서 옮겼다.
 */
final class PaymentFixtures {

	static final String SOURCE_URI = "https://img2.kbcard.com/obj/card/download/09174__prdctOpmn_20260714.pdf";

	private PaymentFixtures() {
	}

	static RecommendationDocument.ComparedCard compared(String userCardId, String productId, Long benefitWon,
			List<RecommendationDocument.Service> services) {
		RecommendationDocument.Amount amount = new RecommendationDocument.Amount(benefitWon,
				(benefitWon == null) ? "NEEDS_CONFIRMATION" : "CONFIRMED");
		return new RecommendationDocument.ComparedCard(userCardId, productId, "카드", null, "APPLIED_CAP_REMAINING",
				rule(), amount, amount, services, null, List.of(), List.of());
	}

	static RecommendationDocument.AppliedRule rule() {
		return new RecommendationDocument.AppliedRule("09174-v1", "VERIFIED",
				List.of(new RecommendationDocument.OfficialSource(SOURCE_URI, "p.1")));
	}

	static RecommendationDocument.Service service(String serviceId, Long contributionWon) {
		return new RecommendationDocument.Service(serviceId, "APPLIED", contributionWon,
				new RecommendationDocument.Amount(contributionWon,
						(contributionWon == null) ? "NEEDS_CONFIRMATION" : "CONFIRMED"),
				(contributionWon == null) ? "UNDETERMINED" : (contributionWon == 0 ? "NOT_ADOPTED" : "ADOPTED"),
				List.of(), null, null, false, null, false, null, List.of());
	}

	static RecommendationDocument.ExcludedCard excluded(String userCardId, String productId, String reason,
			List<String> pendingServiceIds) {
		boolean calculated = "NEEDS_CONFIRMATION".equals(reason) || "UNSUPPORTED".equals(reason);
		return new RecommendationDocument.ExcludedCard(userCardId, productId, reason, "설명", null,
				new RecommendationDocument.Amount(null, "NEEDS_CONFIRMATION"), calculated ? "NEEDS_CONFIRMATION" : null,
				calculated ? rule() : null,
				pendingServiceIds.isEmpty() ? List.of()
						: List.of(new RecommendationDocument.PendingReason("NEEDS_CONFIRMATION", pendingServiceIds,
								"설명")));
	}

	static RecommendationDocument auto(String recommendationId, RecommendationDocument.Selected selected,
			List<RecommendationDocument.ComparedCard> compared, List<RecommendationDocument.ExcludedCard> excluded,
			boolean conditional) {
		return document(recommendationId, "AUTO_RECOMMEND", selected, compared, excluded, null, conditional);
	}

	static RecommendationDocument fixed(String representative, RecommendationDocument.Selected selected,
			List<RecommendationDocument.ComparedCard> compared, List<RecommendationDocument.ExcludedCard> excluded) {
		return document("rec-3002", "FIXED_REPRESENTATIVE", selected, compared, excluded,
				new RecommendationDocument.FixedCardComparison(representative,
						(selected == null) ? null : selected.userCardId(), null),
				false);
	}

	static RecommendationDocument.Selected selected(String userCardId, String productId, String decidedBy) {
		return new RecommendationDocument.Selected(userCardId, productId,
				new RecommendationDocument.Basis(decidedBy, false, List.of(), (decidedBy == null) ? null : 900L));
	}

	private static RecommendationDocument document(String recommendationId, String mode,
			RecommendationDocument.Selected selected, List<RecommendationDocument.ComparedCard> compared,
			List<RecommendationDocument.ExcludedCard> excluded, RecommendationDocument.FixedCardComparison fixed,
			boolean conditional) {
		return new RecommendationDocument(recommendationId, "ord-5001", "2026-11-03T10:41:00+09:00",
				LocalDate.of(2026, 11, 3), mode, (selected == null) ? "NOT_DETERMINED" : "SELECTED", conditional,
				selected, compared, excluded, fixed, null, List.of());
	}


}
