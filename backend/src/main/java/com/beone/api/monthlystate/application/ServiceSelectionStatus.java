package com.beone.api.monthlystate.application;

/**
 * 택1 선택 입력 상태(D-18 K1). 서버가 정하며 요청으로 받지 않는다.
 */
public enum ServiceSelectionStatus {

	/** 미입력. 택1 서비스는 확인 필요. */
	UNKNOWN,

	/** 서비스를 골랐다. */
	SELECTED,

	/** 선택 없음을 사용자가 명시했다. */
	NONE

}
