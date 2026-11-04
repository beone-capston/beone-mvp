package com.beone.api.benefit.rule;

/**
 * 입력과 원장으로 확인한 {@link TermTrigger} 발생 여부.
 */
public enum TriggerEvidence {

	/** 영향 상황이 있음. 조건이 필요하다. */
	PRESENT,

	/**
	 * 완전한 입력으로 영향 상황이 없음을 확인함.
	 * 미확정 조건부 조건을 무시할 수 있는 유일한 근거다.
	 */
	CONFIRMED_ABSENT,

	/**
	 * 입력이 불완전하거나 알 수 없어 영향 상황을 배제할 수 없음.
	 * {@link #PRESENT}와 같이 취급하며, 확인하지 않은 경우에도 이 값을 쓴다.
	 */
	UNDETERMINED

}
