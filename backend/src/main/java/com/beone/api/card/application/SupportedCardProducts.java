package com.beone.api.card.application;

import java.util.List;

/**
 * BeONE이 계산을 지원하는 규칙 카드 4종의 표시 정보(D-02).
 *
 * <p>값의 근거는 docs/card-benefit-research.md 1절 「가상 시연용 4종 선정안」이다. 상품 이름은 그 표의 이름에서
 * 카드사 접두어 "KB국민"을 뗀 것이고, 카드사 표기는 docs/api/products.md 응답 예시의 {@code KB국민카드}를 따른다.
 * 서버 시작 때 {@link CardProductService}가 이 표의 ID와 규칙 카탈로그의 카드 ID가 같은지 확인한다.
 */
public final class SupportedCardProducts {

	private static final String ISSUER = "KB국민카드";

	/** 규칙 카드 4종. 순서는 의미가 없고 응답은 상품 ID 오름차순이다. */
	public static final List<CardProductProfile> ALL = List.of(
			new CardProductProfile("09271", "올라운드 티타늄카드", ISSUER, CardType.CREDIT),
			new CardProductProfile("09174", "청춘대로 톡톡카드", ISSUER, CardType.CREDIT),
			new CardProductProfile("01664", "노리 체크카드", ISSUER, CardType.CHECK),
			new CardProductProfile("01914", "첵첵 체크카드", ISSUER, CardType.CHECK));

	private SupportedCardProducts() {
	}

}
