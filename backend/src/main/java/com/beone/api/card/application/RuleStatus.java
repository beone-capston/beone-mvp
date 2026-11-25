package com.beone.api.card.application;

/**
 * 판정일 기준 카드 규칙 상태. 응답 {@code ruleStatus} 값과 같다. 계산에는 {@link #VERIFIED}만 쓴다.
 *
 * <p>판정 순서는 {@code RuleCatalog#select}를 따른다(기간을 먼저, 검증 상태를 나중에 본다).
 */
public enum RuleStatus {

	/** 판정일에 유효한 {@code VERIFIED} 규칙이 있다. */
	VERIFIED,

	/** 규칙 버전은 있지만 판정일을 포함하는 버전이 없다. */
	RULE_EXPIRED,

	/** 규칙 버전이 없거나, 판정일을 포함하는 버전 중 {@code VERIFIED}가 없다. */
	NO_VERIFIED_RULE

}
