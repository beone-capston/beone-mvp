package com.beone.api.benefit.rule;

/**
 * 규칙 버전의 검증 상태. 운영 계산에는 {@link #VERIFIED}만 쓴다(PRD FR-01).
 */
public enum VerificationStatus {

	/** 공식 약관을 근거로 사람이 승인한 버전 있는 규칙(PRD FR-01). */
	VERIFIED,

	/** AI가 추출했고 아직 사람이 승인하지 않은 규칙 초안(PRD FR-01, FR-08). */
	AI_DRAFT,

	/** 공식 자료에서 읽었지만 검증되지 않은 규칙(평가 fixture 기본값). */
	CANDIDATE,

	/** 규칙 선택 시험에만 쓰는 합성 규칙 스냅샷(평가 fixture C02). */
	SYNTHETIC_TEST_SNAPSHOT

}
