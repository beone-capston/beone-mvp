package com.beone.api.recommendation.application;

import java.util.Objects;

/**
 * 저장한 추천의 조회 결과. 본문은 계산 때 그대로이고 무효화 여부만 조회 때 판정한다.
 *
 * @param document 계산 때의 응답 본문
 * @param invalidated 승인 근거로 쓸 수 없게 됐으면 참
 * @param invalidatedReason 무효가 된 이유. 유효하면 {@code null}
 */
public record RecommendationView(RecommendationDocument document, boolean invalidated,
		InvalidationReason invalidatedReason) {

	public RecommendationView {
		Objects.requireNonNull(document, "document");
		if (invalidated != (invalidatedReason != null)) {
			throw new IllegalArgumentException("invalidatedReason must be present exactly when invalidated");
		}
	}

}
