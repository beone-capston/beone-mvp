package com.beone.api.recommendation.application;

import java.util.List;
import java.util.Map;

/**
 * 엔진의 {@code noteCodes}를 화면 문구로 바꾼다. 엔진은 코드만 내고 문구는 업무 API가 정한다(docs/api/recommendations.md).
 *
 * <p>문구는 이 한 곳에서만 고친다. {@code SINGLE_COMPARED_CARD} 문구는 계약에서 미정이라 결정 27 B2-1의 중립 문구를 쓰며, '최적' 표현을
 * 쓰지 않는다.
 */
public final class RecommendationNotes {

	/** 예정 소비 미반영(항상). */
	public static final String PLANNED_SPENDING_EXCLUDED = "예정 소비는 이번 버전에서 월간 합계에 반영하지 않습니다(D-18 K4).";

	/** 조건부 추천. */
	public static final String CONDITIONAL_RECOMMENDATION = "비교에서 빠진 카드가 더 나을 수 있어 조건부 추천입니다.";

	/** 비교 카드 0장. */
	public static final String NO_COMPARABLE_CARD = "비교할 수 있는 카드가 없어 추천을 확정할 수 없습니다.";

	/** 비교 카드 1장(결정 27 B2-1). */
	public static final String SINGLE_COMPARED_CARD = "비교할 수 있는 카드가 1장이라 다른 카드와 비교한 결과가 아닙니다.";

	private static final Map<String, String> TEXTS = Map.of("PLANNED_SPENDING_EXCLUDED", PLANNED_SPENDING_EXCLUDED,
			"CONDITIONAL_RECOMMENDATION", CONDITIONAL_RECOMMENDATION, "NO_COMPARABLE_CARD", NO_COMPARABLE_CARD,
			"SINGLE_COMPARED_CARD", SINGLE_COMPARED_CARD);

	private RecommendationNotes() {
	}

	/**
	 * 코드 순서대로 문구를 만든다.
	 * @param noteCodes 엔진이 낸 코드
	 * @return 문구 목록
	 * @throws IllegalStateException 문구가 정해지지 않은 코드. 엔진에 코드가 늘면 계약 문구를 먼저 정해야 한다
	 */
	public static List<String> toTexts(List<String> noteCodes) {
		return noteCodes.stream().map(code -> {
			String text = TEXTS.get(code);
			if (text == null) {
				throw new IllegalStateException("no text is defined for note code " + code);
			}
			return text;
		}).toList();
	}

}
