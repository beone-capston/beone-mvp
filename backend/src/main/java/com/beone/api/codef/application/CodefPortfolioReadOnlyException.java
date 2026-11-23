package com.beone.api.codef.application;

/**
 * 가상 포트폴리오 사용자의 CODEF 쓰기 요청. 409 PORTFOLIO_READ_ONLY.
 *
 * <p>웹 계층의 기능별 처리기가 공개 오류로 바꾼다. 문구에 요청 값을 넣지 않는다.
 */
public class CodefPortfolioReadOnlyException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public CodefPortfolioReadOnlyException() {
		super(null, null, false, false);
	}

}
