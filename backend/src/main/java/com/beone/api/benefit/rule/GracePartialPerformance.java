package com.beone.api.benefit.rule;

/**
 * 신규 유예 중 전월 실적이 0원보다 크고 간주 구간 기준보다 작을 때의 처리(D-20). 약관 문구의 차이를 규칙 데이터로 옮긴 값이다.
 */
public enum GracePartialPerformance {

	/** 문구가 실적을 가리지 않고 구간을 제공한다("1구간 서비스 제공"). 간주 구간을 쓴다. */
	DEEMED_TIER,

	/** 문구가 실적이 없는 경우만 말한다("실적이 없는 경우에도"). 일부 실적의 처리를 알 수 없으므로 확인 필요다. */
	NEEDS_CONFIRMATION

}
