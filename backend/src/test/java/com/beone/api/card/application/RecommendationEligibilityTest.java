package com.beone.api.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 추천 비교 대상 판정. 기대값은 docs/api/cards.md 응답 필드 정의와 결정 27 A2-3(사용자 제외 → 매핑 미확인 → 지원 규칙 없음)에서
 * 옮겼다.
 */
class RecommendationEligibilityTest {

	@Test
	void userConfirmedCardIsEligible() {
		assertThat(RecommendationEligibility.of(HoldingSource.MANUAL, MappingStatus.USER_CONFIRMED, false))
			.isEqualTo(new RecommendationEligibility(true, null));
	}

	@Test
	void unconfirmedMappingsNeedConfirmation() {
		assertThat(RecommendationEligibility.of(HoldingSource.MANUAL, MappingStatus.UNMAPPED, false))
			.isEqualTo(new RecommendationEligibility(false, EligibilityReason.MAPPING_UNCONFIRMED));
		assertThat(RecommendationEligibility.of(HoldingSource.CODEF, MappingStatus.CANDIDATE, false))
			.isEqualTo(new RecommendationEligibility(false, EligibilityReason.MAPPING_UNCONFIRMED));
	}

	@Test
	void notSupportedCardHasNoSupportedRule() {
		assertThat(RecommendationEligibility.of(HoldingSource.MANUAL, MappingStatus.NOT_SUPPORTED, false))
			.isEqualTo(new RecommendationEligibility(false, EligibilityReason.NO_SUPPORTED_RULE));
	}

	@ParameterizedTest
	@EnumSource(MappingStatus.class)
	void userExclusionComesFirstForEveryMapping(MappingStatus status) {
		assertThat(RecommendationEligibility.of(HoldingSource.MANUAL, status, true))
			.isEqualTo(new RecommendationEligibility(false, EligibilityReason.USER_EXCLUDED));
	}

	@Test
	void syntheticCardIsEligibleWithoutMapping() {
		assertThat(RecommendationEligibility.of(HoldingSource.SYNTHETIC, null, false))
			.isEqualTo(new RecommendationEligibility(true, null));
		assertThat(RecommendationEligibility.of(HoldingSource.SYNTHETIC, null, true))
			.isEqualTo(new RecommendationEligibility(false, EligibilityReason.USER_EXCLUDED));
	}

	@Test
	void reasonIsPresentExactlyWhenNotEligible() {
		assertThatIllegalArgumentException().isThrownBy(() -> new RecommendationEligibility(true,
				EligibilityReason.USER_EXCLUDED));
		assertThatIllegalArgumentException().isThrownBy(() -> new RecommendationEligibility(false, null));
	}

}
