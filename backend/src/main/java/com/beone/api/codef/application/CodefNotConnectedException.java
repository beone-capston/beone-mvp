package com.beone.api.codef.application;

/**
 * CODEF 연결이 없는 사용자의 해제·동기화 요청. 409 CODEF_NOT_CONNECTED.
 *
 * <p>웹 계층의 기능별 처리기가 공개 오류로 바꾼다. 문구에 요청 값을 넣지 않는다.
 */
public class CodefNotConnectedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public CodefNotConnectedException() {
		super(null, null, false, false);
	}

}
