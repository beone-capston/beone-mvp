package com.beone.api.benefit.state;

/**
 * 상태값의 출처(PRD FR-03, D-04).
 */
public enum StateSource {

	/** CODEF가 실제로 반환한 값. 의미를 바꾸지 않고 정규화만 한다. */
	CODEF,

	/** 사용자가 입력하거나 확인한 값. */
	MANUAL,

	/** VERIFIED 규칙과 빠짐없이 순서가 확인된 원장으로 다시 계산한 값. */
	INFERRED,

	/** 명시적인 가상 포트폴리오·평가 데이터. 실제 관찰값으로 표시하지 않는다. */
	SYNTHETIC,

	/** 누락, 충돌, 기간 불명, 오래된 값. 0원으로 대체하지 않는다. */
	UNKNOWN

}
