package com.beone.api.preference.application;

/**
 * 추천 비교에서 빠진 카드를 대표 카드로 고르려 할 때 던진다(D-15). 입력값을 메시지에 담지 않는다.
 *
 * <p>웹 계층의 기능별 처리기가 409 {@code REPRESENTATIVE_CARD_NOT_ELIGIBLE}로 바꾼다.
 */
public class RepresentativeCardNotEligibleException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public RepresentativeCardNotEligibleException() {
		super(null, null, false, false);
	}

}
