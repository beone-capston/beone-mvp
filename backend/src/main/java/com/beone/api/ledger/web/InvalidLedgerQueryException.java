package com.beone.api.ledger.web;

/**
 * 원장 조회 쿼리 값의 형식 오류. 오류 응답에 쓸 필드 이름만 갖는다.
 */
class InvalidLedgerQueryException extends RuntimeException {

	private final String field;

	InvalidLedgerQueryException(String field) {
		super(null, null, false, false);
		this.field = field;
	}

	String field() {
		return this.field;
	}

}
