package com.beone.api.monthlystate.application;

/**
 * 없거나, 해제했거나, 다른 사용자의 보유 카드일 때 던진다. 입력값을 메시지에 담지 않는다.
 *
 * <p>웹 계층의 기능별 처리기가 404 {@code USER_CARD_NOT_FOUND}로 바꾼다.
 */
public class MonthlyStateCardNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public MonthlyStateCardNotFoundException() {
		super(null, null, false, false);
	}

}
