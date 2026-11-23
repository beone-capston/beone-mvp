package com.beone.api.codef.application;

/**
 * 게이트웨이가 연결 무효를 알린다. 지금은 가짜 게이트웨이만 던지며 실 CODEF 결과 코드와의 대응은 없다(관리자 판단 P2).
 */
public class CodefConnectionInvalidException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public CodefConnectionInvalidException() {
		super(null, null, false, false);
	}

}
