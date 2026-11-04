package com.beone.api.benefit.rule;

/**
 * 취소 금액을 실적에서 차감하는 달.
 * 후보 약관에 적힌 방식만 두고, 나머지는 미확정 조건으로 남긴다(D-03).
 */
public enum CancellationAttribution {

	/** 취소전표 접수월에 차감(09271, 01664, 01914). */
	RECEIPT_MONTH

}
