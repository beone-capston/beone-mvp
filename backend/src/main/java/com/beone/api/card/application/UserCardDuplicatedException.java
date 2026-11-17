package com.beone.api.card.application;

/**
 * 같은 사용자에게 같은 상품의 {@code USER_CONFIRMED} 카드가 이미 있을 때 던진다(D-23 2항). 입력값을 메시지에 담지 않는다.
 *
 * <p>웹 계층의 기능별 처리기가 409 {@code USER_CARD_DUPLICATED}로 바꾼다.
 */
public class UserCardDuplicatedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public UserCardDuplicatedException() {
		super(null, null, false, false);
	}

}
