package com.beone.api.ledger.application;

/**
 * 원장 행의 출처(docs/api/ledger.md {@code source}). 실제 조회 거래와 자체 모의 거래를 섞지 않는다(CLAUDE.md 보안).
 *
 * <p>1차는 자체 모의 원장 행만 저장한다. {@code CODEF}는 조회 필터와 출처별 완전성(coverage) 항목에만 쓰이며, 2차 CODEF 작업이
 * 같은 테이블에 그 행을 넣는다.
 */
public enum LedgerSource {

	/** BeONE이 만든 모의 승인·취소. 실제 카드 결제가 아니다. */
	SIMULATION,

	/** CODEF로 조회한 실제 카드 거래. 1차에는 저장하는 코드가 없다. */
	CODEF

}
