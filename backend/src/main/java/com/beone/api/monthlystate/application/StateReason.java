package com.beone.api.monthlystate.application;

/**
 * 상태값을 계산에 쓸 수 없는 이유. 응답 {@code reason} 값과 같다(docs/api/monthly-states.md).
 */
public enum StateReason {

	/** 입력하지 않았거나 적용일이 없다. */
	NOT_ENTERED,

	/** 출처 충돌(2차). 1차에서는 만들지 않는다. */
	SOURCE_CONFLICT,

	/** 귀속이 확인되지 않은 취소로 생긴 음수 전월 실적(D-17). 1차 수동 입력에는 없다. */
	NEGATIVE_UNCONFIRMED_ATTRIBUTION

}
