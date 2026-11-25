package com.beone.api.card.application;

/**
 * 가상 포트폴리오 사용자가 보유 카드를 쓰려 할 때 던진다(D-19, D-23 5항). 입력값을 메시지에 담지 않는다.
 *
 * <p>웹 계층의 기능별 처리기가 409 {@code PORTFOLIO_READ_ONLY}로 바꾼다.
 */
public class PortfolioReadOnlyException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public PortfolioReadOnlyException() {
		super(null, null, false, false);
	}

}
