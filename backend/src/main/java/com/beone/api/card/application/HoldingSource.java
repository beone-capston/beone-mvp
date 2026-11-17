package com.beone.api.card.application;

/**
 * 보유 카드를 어디서 얻었는지. 응답 {@code holdingSource} 값과 같다(docs/api/cards.md).
 */
public enum HoldingSource {

	/** 사용자가 수동 등록한 카드. */
	MANUAL,

	/** 2차 CODEF 보유카드 조회로 얻은 카드. 1차에서는 만들지 않는다. */
	CODEF,

	/** 가상 포트폴리오 시드 카드. 규칙 카드 자체라 매핑 대상이 아니다(D-15). */
	SYNTHETIC

}
