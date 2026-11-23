package com.beone.api.codef.application;

/**
 * 동기화 조회 기간이 규칙에 맞지 않는다(관리자 판단 P9). 400 {@code INVALID_REQUEST}다. 문구에 입력값을 넣지 않는다.
 */
public class CodefSyncPeriodException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final String field;

	private final String violation;

	/**
	 * @param field 문제 필드. 두 필드의 관계 오류면 {@code null}
	 * @param violation 고정 문구
	 */
	public CodefSyncPeriodException(String field, String violation) {
		super(null, null, false, false);
		this.field = field;
		this.violation = violation;
	}

	/**
	 * 문제 필드.
	 * @return 필드 이름. 관계 오류면 {@code null}
	 */
	public String field() {
		return this.field;
	}

	/**
	 * 고정 문구.
	 * @return 문구
	 */
	public String violation() {
		return this.violation;
	}

}
