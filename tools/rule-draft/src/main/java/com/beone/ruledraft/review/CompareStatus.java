package com.beone.ruledraft.review;

/** 비교 분류. 표에는 한국어 이름을 쓴다. */
public enum CompareStatus {

	/** 초안과 수동 규칙의 값이 같다. */
	MATCH("일치"),
	/** 두 값이 다르다(종류가 다른 경우 포함). */
	MISMATCH("불일치"),
	/** 수동 규칙에는 있고 초안에는 없다. */
	MISSING_IN_DRAFT("누락(초안에 없음)"),
	/** 초안에는 있고 수동 규칙에는 없다. */
	MISSING_IN_MANUAL("누락(수동에 없음)"),
	/** 초안 값에 확인된 원문 근거가 자기·조상 경로 어디에도 없다. 원래 분류는 {@link CompareRow#comparison()}에 남긴다. */
	NO_EVIDENCE("근거 없음");

	private final String label;

	CompareStatus(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

}
