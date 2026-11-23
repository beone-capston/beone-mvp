package com.beone.api.card.application;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 보유 카드 한 장의 조회 결과(docs/api/cards.md 보유 카드 목록 항목).
 *
 * @param userCardId 외부 ID({@code uc-}+번호)
 * @param displayName 표시 이름
 * @param holdingSource 보유 출처
 * @param cardProductId 가상 포트폴리오 카드의 상품 ID. 그 밖에는 {@code null}(매핑 안에 담는다, 결정 27 A2-4)
 * @param mapping 매핑 기록. 가상 포트폴리오 카드는 {@code null}
 * @param excludedFromRecommendation 사용자가 추천에서 뺐는지
 * @param recommendationEligibility 추천 비교 대상 여부
 * @param registeredAt 등록 시각(서울)
 */
public record UserCardView(String userCardId, String displayName, HoldingSource holdingSource, String cardProductId,
		UserCardMapping mapping, boolean excludedFromRecommendation, RecommendationEligibility recommendationEligibility,
		LocalDateTime registeredAt) {

	public UserCardView {
		Objects.requireNonNull(userCardId, "userCardId");
		Objects.requireNonNull(displayName, "displayName");
		Objects.requireNonNull(holdingSource, "holdingSource");
		Objects.requireNonNull(recommendationEligibility, "recommendationEligibility");
		Objects.requireNonNull(registeredAt, "registeredAt");
		if ((holdingSource == HoldingSource.SYNTHETIC) != (mapping == null)) {
			throw new IllegalArgumentException("only synthetic cards have no mapping");
		}
	}

}
