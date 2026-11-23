package com.beone.api.card.application;

/**
 * 지원하지 않는 카드 상품 ID를 조회했을 때 던진다. 입력값을 메시지에 담지 않는다.
 *
 * <p>웹 계층의 기능별 처리기가 404 {@code CARD_PRODUCT_NOT_FOUND}로 바꾼다.
 */
public class CardProductNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public CardProductNotFoundException() {
		super(null, null, false, false);
	}

}
