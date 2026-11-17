package com.beone.api.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 추천 ID 형식(결정 27 B2-2: {@code rec-}+선행 0 없는 10진수, 형식 틀림은 404로 다룸)을 확인한다.
 */
class RecommendationIdTest {

	@Test
	void formatsAndParsesPositiveIds() {
		assertThat(RecommendationId.format(3001)).isEqualTo("rec-3001");
		assertThat(RecommendationId.parse("rec-3001")).contains(3001L);
		assertThat(RecommendationId.parse("rec-9223372036854775807")).contains(Long.MAX_VALUE);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "rec-", "rec-0", "rec-01", "REC-1", "rec-1a", "ord-1", "rec--1", "rec-9223372036854775808",
			"rec-12345678901234567890" })
	void malformedIdsAreEmpty(String value) {
		assertThat(RecommendationId.parse(value)).isEmpty();
	}

	@Test
	void nullIsEmptyAndNonPositiveCannotBeFormatted() {
		assertThat(RecommendationId.parse(null)).isEmpty();
		assertThatThrownBy(() -> RecommendationId.format(0)).isInstanceOf(IllegalArgumentException.class);
	}

}
