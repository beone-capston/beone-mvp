package com.beone.api.card.web;

import java.util.List;

import com.beone.api.card.application.EligibilityReason;
import com.beone.api.card.application.RecommendationEligibility;
import com.beone.api.card.application.UserCardChangeResult;
import com.beone.api.card.application.UserCardMapping;
import com.beone.api.card.application.UserCardView;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * 보유 카드 API 응답 {@code data} 모양(docs/api/cards.md). 필드 순서는 계약 예시 순서다.
 */
final class UserCardResponses {

	private UserCardResponses() {
	}

	/**
	 * 매핑 기록. CODEF 후보 값은 2차에서 만들므로 항상 {@code null}이다(결정 27 A2-10).
	 */
	record Mapping(String status, String cardProductId, String confirmationMethod, String confirmedAt,
			String candidateCardProductId, Object candidateBasis, String note) {

		static Mapping from(UserCardMapping mapping) {
			if (mapping == null) {
				return null;
			}
			return new Mapping(mapping.status().name(), mapping.cardProductId(),
					(mapping.confirmationMethod() == null) ? null : mapping.confirmationMethod().name(),
					UserCardValueFormats.seoulInstant(mapping.confirmedAt()), null, null, mapping.note());
		}

	}

	/** 추천 비교 대상 여부. */
	record Eligibility(boolean eligible, EligibilityReason reason) {

		static Eligibility from(RecommendationEligibility eligibility) {
			return new Eligibility(eligibility.eligible(), eligibility.reason());
		}

	}

	/**
	 * 보유 카드 목록 항목. 최상위 {@code cardProductId}는 가상 포트폴리오 카드에만 있다(결정 27 A2-4). 표시 이름·추천 제외 변경
	 * 응답은 추천 제외 값이 실제로 바뀌었을 때만 {@code invalidatedRecommendationIds}를 담는다.
	 */
	@JsonPropertyOrder({ "userCardId", "displayName", "holdingSource", "cardProductId", "mapping",
			"excludedFromRecommendation", "recommendationEligibility", "registeredAt",
			"invalidatedRecommendationIds" })
	record Item(String userCardId, String displayName, String holdingSource,
			@JsonInclude(JsonInclude.Include.NON_NULL) String cardProductId, Mapping mapping,
			boolean excludedFromRecommendation, Eligibility recommendationEligibility, String registeredAt,
			@JsonInclude(JsonInclude.Include.NON_NULL) List<String> invalidatedRecommendationIds) {

		static Item from(UserCardView card) {
			return of(card, null);
		}

		static Item from(UserCardChangeResult result) {
			return of(result.card(), result.invalidatedRecommendationIds());
		}

		private static Item of(UserCardView card, List<String> invalidatedRecommendationIds) {
			return new Item(card.userCardId(), card.displayName(), card.holdingSource().name(), card.cardProductId(),
					Mapping.from(card.mapping()), card.excludedFromRecommendation(),
					Eligibility.from(card.recommendationEligibility()),
					UserCardValueFormats.seoulInstant(card.registeredAt()), invalidatedRecommendationIds);
		}

	}

	/** 보유 카드 목록. */
	record ItemList(List<Item> items) {

		static ItemList from(List<UserCardView> cards) {
			return new ItemList(cards.stream().map(Item::from).toList());
		}

	}

	/**
	 * 매핑 확인·변경 응답. 계약 예시 모양 그대로 {@code registeredAt}을 담지 않는다(결정 27 A2-5).
	 */
	record MappingChanged(String userCardId, String displayName, String holdingSource, Mapping mapping,
			boolean excludedFromRecommendation, Eligibility recommendationEligibility,
			List<String> invalidatedRecommendationIds) {

		static MappingChanged from(UserCardChangeResult result) {
			UserCardView card = result.card();
			return new MappingChanged(card.userCardId(), card.displayName(), card.holdingSource().name(),
					Mapping.from(card.mapping()), card.excludedFromRecommendation(),
					Eligibility.from(card.recommendationEligibility()), result.invalidatedRecommendationIds());
		}

	}

}
