package com.beone.api.codef.application;

/**
 * CODEF 응답을 쓸 수 없음(미분류·전송 실패·게이트웨이 없음). 502 CODEF_UNAVAILABLE.
 *
 * <p>웹 계층의 기능별 처리기가 공개 오류로 바꾼다. 문구에 요청 값을 넣지 않는다.
 */
public class CodefUnavailableException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public CodefUnavailableException() {
		super(null, null, false, false);
	}

}
