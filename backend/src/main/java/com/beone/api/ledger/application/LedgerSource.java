package com.beone.api.ledger.application;

/**
 * 원장 행의 출처(docs/api/ledger.md {@code source}). 실제 조회 거래와 자체 모의 거래를 섞지 않는다(CLAUDE.md 보안).
 *
 * <p>1차는 자체 모의 원장만 쓴다. 2차 CODEF 원장 행({@code CODEF})은 CODEF 작업이 이 값을 더해 같은 테이블에 넣는다.
 */
public enum LedgerSource {

	/** BeONE이 만든 모의 승인·취소. 실제 카드 결제가 아니다. */
	SIMULATION

}
