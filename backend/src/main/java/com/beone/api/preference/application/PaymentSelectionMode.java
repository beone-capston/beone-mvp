package com.beone.api.preference.application;

/**
 * 결제할 때 카드를 고르는 방식(D-12, docs/api/preferences.md). 응답·저장 값은 상수 이름이다.
 */
public enum PaymentSelectionMode {

	/** 매 주문 추천 결과로 카드를 고른다. 대표 카드는 동률 처리 3단계에만 쓴다. */
	AUTO_RECOMMEND,

	/** 대표 카드로 고정한다. 대표 카드가 반드시 있다. */
	FIXED_REPRESENTATIVE

}
