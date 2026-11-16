package com.beone.api.order.application;

/**
 * 예정일이 이번 달 밖일 때 던진다. 웹 검증을 통과한 뒤 서비스에 들어오기 전에 달이 바뀐 경우(서울 기준 말일 자정 직전 요청)에 생긴다.
 * 입력값을 메시지에 담지 않는다.
 */
public class PlannedDateOutOfMonthException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public PlannedDateOutOfMonthException() {
		super(null, null, false, false);
	}

}
