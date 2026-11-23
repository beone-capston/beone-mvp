package com.beone.api.card.web;

/**
 * {@code asOf} 쿼리 값이 {@code YYYY-MM-DD} 날짜가 아닐 때 던진다. 입력값을 메시지에 담지 않는다.
 *
 * <p>{@link CardProductExceptionHandler}가 400 {@code INVALID_REQUEST}로 바꾼다.
 */
class InvalidAsOfException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	InvalidAsOfException() {
		super(null, null, false, false);
	}

}
