package com.beone.api.recommendation.application;

/**
 * 저장한 추천을 승인 근거로 쓸 수 없게 된 이유(docs/api/recommendations.md). 여럿이면 선언 순서가 우선순위다(결정 27 B2-4).
 */
public enum InvalidationReason {

	/** 주문이 모의 승인됐다(주문 상태가 {@code CREATED}가 아님). */
	ORDER_PAID,

	/**
	 * 보유 카드의 비교 대상 여부가 바뀌었다. 매핑 상태·규칙 상품 변경, 카드 해제, 추천 제외 변경을 묶는다. 추천 제외 변경은 계약 사유 목록에
	 * 따로 없어 이 값으로 묶었다(cards.md의 추천 제외 변경 무효화, 관리자 판단).
	 */
	MAPPING_CHANGED,

	/** 그 주문 월의 월간 상태를 다시 입력했다(스냅샷 시각이 달라짐). */
	MONTHLY_STATE_CHANGED

}
