package com.beone.api.codef.application;

/**
 * 카드소지확인 필요(지원 보류). 409 CODEF_CARD_VERIFICATION_NOT_SUPPORTED.
 *
 * <p>웹 계층의 기능별 처리기가 공개 오류로 바꾼다. 문구에 요청 값을 넣지 않는다.
 */
public class CodefCardVerificationNotSupportedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public CodefCardVerificationNotSupportedException() {
		super(null, null, false, false);
	}

}
