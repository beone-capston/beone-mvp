package com.beone.api.monthlystate.application;

/**
 * 고르려는 항목·기준 기간에 출처 충돌이 없을 때 던진다. 입력값을 메시지에 담지 않는다.
 *
 * <p>웹 계층의 기능별 처리기가 409 {@code SOURCE_CONFLICT_NOT_FOUND}로 바꾼다.
 */
public class SourceConflictNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public SourceConflictNotFoundException() {
		super(null, null, false, false);
	}

}
