package com.beone.api.card.application;

/**
 * 보유 카드와 규칙 카드의 매핑 상태(D-15). 응답 {@code mapping.status} 값과 같다. 계산에는 {@link #USER_CONFIRMED}만 쓴다.
 */
public enum MappingStatus {

	/** 아직 어떤 상품인지 모름. */
	UNMAPPED,

	/** 2차 CODEF 카드명으로 서버가 낸 후보. 사용자가 확인하기 전에는 확정하지 않는다. 1차에서는 만들지 않는다. */
	CANDIDATE,

	/** 사용자가 직접 고르거나 후보를 확인한 상품. */
	USER_CONFIRMED,

	/** 사용자가 규칙 카드 4종이 아니라고 고른 카드. */
	NOT_SUPPORTED

}
