package com.beone.api.benefit.input;

/**
 * 거래가 속한 원장. CODEF 승인내역과 서비스 자체 모의 원장을 섞지 않는다(PRD FR-02, FR-07).
 */
public enum LedgerSource {

	/** 서비스 자체의 가상·모의 승인 원장. */
	SIMULATION,

	/** CODEF가 실제로 반환한 승인내역. */
	CODEF

}
