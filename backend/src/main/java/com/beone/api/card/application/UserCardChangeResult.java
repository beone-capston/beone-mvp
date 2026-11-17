package com.beone.api.card.application;

import java.util.List;
import java.util.Objects;

/**
 * 보유 카드 변경 결과와 그 변경으로 무효가 된 추천 ID.
 *
 * <p>추천(B2)이 아직 없어 무효화할 추천이 없으므로 무효화 대상이 있는 변경은 {@code []}다(결정 27 A2-6). 추천 저장이 생기면 이 값을
 * 채우도록 연결한다.
 *
 * @param card 변경 뒤 카드
 * @param invalidatedRecommendationIds 무효가 된 추천 ID. 이 변경이 추천을 무효화하는 종류가 아니면 {@code null}
 */
public record UserCardChangeResult(UserCardView card, List<String> invalidatedRecommendationIds) {

	public UserCardChangeResult {
		Objects.requireNonNull(card, "card");
		invalidatedRecommendationIds = (invalidatedRecommendationIds == null) ? null
				: List.copyOf(invalidatedRecommendationIds);
	}

}
