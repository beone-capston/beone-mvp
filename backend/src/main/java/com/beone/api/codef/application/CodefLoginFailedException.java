package com.beone.api.codef.application;

/**
 * 카드사 로그인 실패. 400 CODEF_LOGIN_FAILED.
 *
 * <p>웹 계층의 기능별 처리기가 공개 오류로 바꾼다. 문구에 요청 값을 넣지 않는다.
 */
public class CodefLoginFailedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public CodefLoginFailedException() {
		super(null, null, false, false);
	}

}
