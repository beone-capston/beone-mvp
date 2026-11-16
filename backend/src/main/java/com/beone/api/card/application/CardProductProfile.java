package com.beone.api.card.application;

import java.util.Objects;

import com.beone.api.benefit.card.CardId;

/**
 * 카드 상품의 표시 정보. 규칙 데이터에는 없는 이름·카드사·종류를 담는다(결정 27 A1-2).
 *
 * @param cardProductId 선행 0을 보존하는 공식 상품 식별자. 숫자로 바꾸지 않는다
 * @param cardName 화면에 보일 상품 이름
 * @param issuer 카드사 이름
 * @param cardType 카드 종류
 */
public record CardProductProfile(String cardProductId, String cardName, String issuer, CardType cardType) {

	public CardProductProfile {
		// 규칙 카탈로그와 같은 식별자 규칙(빈 값·공백 거부)을 따른다.
		CardId.of(cardProductId);
		requireText(cardName, "cardName");
		requireText(issuer, "issuer");
		Objects.requireNonNull(cardType, "cardType");
	}

	private static void requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " is required");
		}
	}

}
