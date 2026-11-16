package com.beone.api.user.web;

/**
 * 시연 사용자 헤더가 없거나 잘못됐을 때 던진다. 입력값을 메시지에 담지 않는다.
 *
 * <p>{@link DemoUserExceptionHandler}가 400 {@code INVALID_REQUEST}로 바꾼다.
 */
public class InvalidDemoUserHeaderException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public InvalidDemoUserHeaderException() {
		super(null, null, false, false);
	}

}
