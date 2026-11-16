package com.beone.api.card.application;

import java.util.List;

/**
 * 카드 상품 목록의 항목 하나(docs/api/products.md {@code GET /api/v1/card-products}).
 *
 * @param cardProductId 선행 0을 보존하는 공식 상품 식별자
 * @param cardName 상품 이름
 * @param issuer 카드사
 * @param cardType 카드 종류
 * @param ruleStatus 판정일 기준 규칙 상태
 * @param ruleVersion 판정일에 유효한 {@code VERIFIED} 규칙 버전. 없으면 {@code null}
 * @param officialSources 그 규칙의 공식 근거. 규칙이 없으면 빈 배열
 */
public record CardProductSummary(String cardProductId, String cardName, String issuer, CardType cardType,
		RuleStatus ruleStatus, String ruleVersion, List<OfficialSourceView> officialSources) {

	public CardProductSummary {
		officialSources = List.copyOf(officialSources);
	}

}
