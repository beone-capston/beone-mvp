package com.beone.api.card.application;

/**
 * 보유 카드가 추천 비교에 들어갈 수 있는지와 빠지는 이유(D-15).
 *
 * <p>A2에서는 계약의 이유 정의가 다루는 매핑 상태와 사용자 제외만 본다. 규칙 만료·월간 상태·결제 전 설정은 보지 않는다
 * (결정 27 A2-3).
 *
 * @param eligible 추천 비교 대상이면 {@code true}
 * @param reason 대상이 아니면 이유, 대상이면 {@code null}
 */
public record RecommendationEligibility(boolean eligible, EligibilityReason reason) {

	private static final RecommendationEligibility ELIGIBLE = new RecommendationEligibility(true, null);

	public RecommendationEligibility {
		if (eligible != (reason == null)) {
			throw new IllegalArgumentException("reason must be present only when not eligible");
		}
	}

	/**
	 * 판정 순서: 사용자 제외 → 매핑 미확인 → 지원 규칙 없음(결정 27 A2-3). 가상 포트폴리오 카드는 규칙 카드 자체라 매핑을 보지 않는다.
	 * @param source 보유 출처
	 * @param mappingStatus 매핑 상태. {@link HoldingSource#SYNTHETIC}이면 {@code null}
	 * @param excludedFromRecommendation 사용자가 추천에서 뺐는지
	 * @return 판정 결과
	 */
	public static RecommendationEligibility of(HoldingSource source, MappingStatus mappingStatus,
			boolean excludedFromRecommendation) {
		if (excludedFromRecommendation) {
			return new RecommendationEligibility(false, EligibilityReason.USER_EXCLUDED);
		}
		if (source == HoldingSource.SYNTHETIC) {
			return ELIGIBLE;
		}
		return switch (mappingStatus) {
			case UNMAPPED, CANDIDATE -> new RecommendationEligibility(false, EligibilityReason.MAPPING_UNCONFIRMED);
			case NOT_SUPPORTED -> new RecommendationEligibility(false, EligibilityReason.NO_SUPPORTED_RULE);
			case USER_CONFIRMED -> ELIGIBLE;
		};
	}

}
