package com.beone.api.card.application;

/**
 * {@code CODEF_CANDIDATE}로 확인하려는데 현재 매핑이 {@code CANDIDATE}가 아니거나 후보 상품과 다를 때 던진다. 입력값을 메시지에 담지
 * 않는다.
 *
 * <p>1차에서는 {@code CANDIDATE}를 만들지 않으므로 이 확인 방법은 항상 이 예외다(결정 27 A2-7). 웹 계층의 기능별 처리기가 400
 * {@code INVALID_REQUEST}로 바꾼다.
 */
public class CandidateMismatchException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public CandidateMismatchException() {
		super(null, null, false, false);
	}

}
