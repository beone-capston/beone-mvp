package com.beone.api.monthlystate.web;

/**
 * 경로 {@code month} 형식 오류. 입력값을 담지 않는다. 기능별 처리기가 400 {@code INVALID_REQUEST}로 바꾼다.
 */
class InvalidMonthException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	InvalidMonthException() {
		super(null, null, false, false);
	}

}
