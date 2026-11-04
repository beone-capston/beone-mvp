package com.beone.api.benefit.rule;

/**
 * 미확정 조건부 조건이 결과에 영향을 주는 입력 상황.
 * 이 상황이 없다고 확인되기 전까지는 조건이 필요한 것으로 본다.
 */
public enum TermTrigger {

	/**
	 * 실적 기간에 귀속될 수 있는 취소: 실적 기간에 접수됐거나, 실적 기간 거래를 취소했거나, 원거래일을 모르는 취소(취소 귀속월, D-03).
	 */
	CANCELLATION_AFFECTING_PERFORMANCE_WINDOW,

	/** 실적 기간 안에 할인받은 매출이 있음(실적 제외). */
	BENEFITED_SALE_IN_PERFORMANCE_WINDOW,

	/** 같은 기간에 같은 한도를 쓰는 거래가 둘 이상 있음(한도 차감 순서, D-03). */
	SHARED_LIMIT_CONSUMPTION,

	/** 카드가 신규 유예 기간 안에 있음(D-03). */
	WITHIN_NEW_CARD_GRACE,

	/** 한 거래에 둘 이상의 서비스가 해당됨(중복 허용, PRD FR-05). */
	MULTIPLE_SERVICES_QUALIFY,

	/** 요율 계산 결과에 원 미만 금액이 생김(원 미만 처리, D-03). */
	FRACTIONAL_WON_RESULT

}
