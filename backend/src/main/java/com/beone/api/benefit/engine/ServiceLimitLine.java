package com.beone.api.benefit.engine;

import com.beone.api.benefit.state.StateSource;

/**
 * 서비스 체인의 한도 버킷 한 줄.
 *
 * <p>실적 구간이나 사용액을 모르거나, 사용액이 한도보다 크거나, 체인의 기여액이 미확정이면 {@code remainingAfterWon}은 null이다.
 *
 * @param limitBucketId 한도 버킷
 * @param parentLimitBucketId 상위 버킷. 없을 수 있다
 * @param limitWon 그 실적 구간의 한도. 모르면 null
 * @param usedBeforeWon 입력 사용액 유효값. 모르면 null
 * @param usedBeforeSource 사용액의 출처
 * @param remainingAfterWon 이 주문 뒤 남은 한도
 */
public record ServiceLimitLine(String limitBucketId, String parentLimitBucketId, Long limitWon, Long usedBeforeWon,
		String usedBeforeSource, Long remainingAfterWon) {

	public ServiceLimitLine {
		EngineChecks.requireToken(limitBucketId, "limitBucketId");
		EngineChecks.requireOptionalToken(parentLimitBucketId, "parentLimitBucketId");
		EngineChecks.requireOptionalNonNegative(limitWon, "limitWon");
		EngineChecks.requireOptionalNonNegative(usedBeforeWon, "usedBeforeWon");
		EngineChecks.requireEnumName(StateSource.class, usedBeforeSource, "usedBeforeSource");
		EngineChecks.requireOptionalNonNegative(remainingAfterWon, "remainingAfterWon");
		if (remainingAfterWon != null) {
			if (limitWon == null || usedBeforeWon == null) {
				throw new IllegalArgumentException("remainingAfterWon needs a known limit and usage");
			}
			if (remainingAfterWon > limitWon - usedBeforeWon) {
				throw new IllegalArgumentException("remainingAfterWon must not exceed limit minus usage");
			}
		}
	}

}
