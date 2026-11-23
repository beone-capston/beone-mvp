package com.beone.api.recommendation.application;

/**
 * 없거나 형식이 틀렸거나 다른 사용자의 추천일 때 던진다. 웹 계층이 404 {@code RECOMMENDATION_NOT_FOUND}로 바꾼다. 입력값을 담지 않는다.
 */
public class RecommendationNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public RecommendationNotFoundException() {
		super(null, null, false, false);
	}

}
