package com.beone.api.card.application;

/**
 * 결제 전 설정의 대표 카드 고정 모드에서 쓰는 대표 카드를 추천에서 빼거나 해제하려 할 때
 * {@link RepresentativeCardGuard} 구현이 던진다(docs/api/cards.md, D-23). 입력값을 메시지에 담지 않는다.
 *
 * <p>웹 계층의 기능별 처리기가 409 {@code REPRESENTATIVE_CARD_IN_USE}로 바꾼다.
 */
public class RepresentativeCardInUseException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public RepresentativeCardInUseException() {
		super(null, null, false, false);
	}

}
