package com.beone.api.payment.application;

/**
 * 자동 추천인데 비교 가능한 카드가 0장이라 추천을 확정할 수 없음(409 RECOMMENDATION_NOT_DETERMINED, D-18 K2).
 */
public class RecommendationNotDeterminedException extends RuntimeException {

	public RecommendationNotDeterminedException() {
		super(null, null, false, false);
	}

}
