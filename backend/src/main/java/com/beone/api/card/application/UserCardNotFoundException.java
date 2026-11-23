package com.beone.api.card.application;

/**
 * 없거나, 이미 해제했거나, 다른 사용자의 보유 카드를 요청했을 때 던진다. 형식이 틀린 ID도 같다(결정 27 A2-1). 입력값을 메시지에 담지 않는다.
 *
 * <p>웹 계층의 기능별 처리기가 404 {@code USER_CARD_NOT_FOUND}로 바꾼다.
 */
public class UserCardNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public UserCardNotFoundException() {
		super(null, null, false, false);
	}

}
