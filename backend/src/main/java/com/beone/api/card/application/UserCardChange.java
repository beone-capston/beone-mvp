package com.beone.api.card.application;

/**
 * 표시 이름·추천 제외 변경 입력. 보낸 필드만 바꾸며 둘 다 {@code null}일 수 없다(docs/api/cards.md).
 *
 * @param displayName 앞뒤 공백을 뗀 새 표시 이름. 바꾸지 않으면 {@code null}
 * @param excludedFromRecommendation 새 추천 제외 값. 바꾸지 않으면 {@code null}
 */
public record UserCardChange(String displayName, Boolean excludedFromRecommendation) {

	public UserCardChange {
		if (displayName == null && excludedFromRecommendation == null) {
			throw new IllegalArgumentException("at least one field must change");
		}
	}

}
