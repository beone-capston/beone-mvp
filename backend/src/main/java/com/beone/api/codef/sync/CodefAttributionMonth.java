package com.beone.api.codef.sync;

/**
 * 취소의 실적 귀속월 상태. CODEF 응답에는 취소 접수일이 없으므로 귀속월을 정하지 않는다(D-17).
 */
public enum CodefAttributionMonth {

	/** 확인 필요. 접수월 차감 카드라도 접수일을 모르면 실적 차감을 확정하지 않는다. */
	NEEDS_CONFIRMATION

}
