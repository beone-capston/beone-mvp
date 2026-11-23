package com.beone.api.card.application;

/**
 * 매핑을 확인한 방법(D-15). 응답 {@code mapping.confirmationMethod} 값과 같다.
 */
public enum ConfirmationMethod {

	/** 사용자가 상품 목록에서 직접 골랐다. */
	MANUAL_SELECTION,

	/** 사용자가 CODEF 후보를 확인했다(2차). */
	CODEF_CANDIDATE

}
