package com.beone.api.monthlystate.application;

/**
 * 상태값의 출처(D-04, D-16). 응답 {@code source} 값과 같다. 1차는 {@link #MANUAL}과 {@link #UNKNOWN}만 만든다.
 */
public enum StateSource {

	/** 사용자가 직접 입력한 값. */
	MANUAL,

	/** 2차 CODEF 조회값. 1차에서는 만들지 않는다. */
	CODEF,

	/** 원장 기반 산출값. 따로 정하며 1차에서는 만들지 않는다. */
	INFERRED,

	/** 가상 포트폴리오 시드 값. 시드가 없어 지금은 만들지 않는다(결정 27 A3). */
	SYNTHETIC,

	/** 입력하지 않은 값. {@code value}는 {@code null}이다. */
	UNKNOWN

}
