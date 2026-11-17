package com.beone.api.ledger.application;

/**
 * 원장 행 귀속월의 확정 여부(docs/api/ledger.md {@code attributionMonth.status}).
 *
 * <p>모의 원장 행은 BeONE이 날짜를 정하므로 거래일의 달을 {@code CONFIRMED}로 둔다. 2차 CODEF 취소 행은 접수일이 없어
 * {@code NEEDS_CONFIRMATION}이다(D-17). 카드 실적의 차감 달(취소 귀속 규칙)과는 다른 값이며, 그 판단은 취소 영향에서 한다.
 */
public enum AttributionStatus {

	CONFIRMED,

	NEEDS_CONFIRMATION

}
