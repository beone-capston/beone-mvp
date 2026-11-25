package com.beone.api.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * noteCode 문구 변환. 문구는 docs/api/recommendations.md의 notes 목록과 결정 27 B2-1에서 옮겼다.
 */
class RecommendationNotesTest {

	@Test
	void convertsEveryEngineCodeInOrder() {
		assertThat(RecommendationNotes.toTexts(List.of("PLANNED_SPENDING_EXCLUDED", "NO_COMPARABLE_CARD",
				"CONDITIONAL_RECOMMENDATION", "SINGLE_COMPARED_CARD")))
			.containsExactly("예정 소비는 이번 버전에서 월간 합계에 반영하지 않습니다(D-18 K4).", "비교할 수 있는 카드가 없어 추천을 확정할 수 없습니다.",
					"비교에서 빠진 카드가 더 나을 수 있어 조건부 추천입니다.", "비교할 수 있는 카드가 1장이라 다른 카드와 비교한 결과가 아닙니다.");
	}

	@Test
	void singleComparedCardTextDoesNotClaimOptimal() {
		assertThat(RecommendationNotes.SINGLE_COMPARED_CARD).doesNotContain("최적");
	}

	@Test
	void unknownCodeFailsInsteadOfInventingText() {
		assertThatThrownBy(() -> RecommendationNotes.toTexts(List.of("BASIS_DATE_NOT_APPROVAL")))
			.isInstanceOf(IllegalStateException.class);
	}

}
