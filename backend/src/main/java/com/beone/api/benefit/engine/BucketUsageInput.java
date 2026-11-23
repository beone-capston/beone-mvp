package com.beone.api.benefit.engine;

import java.util.Objects;

/**
 * 한도 버킷 하나의 사용액 유효값(스냅샷 + 스냅샷 뒤 승인 차감액, D-16).
 *
 * @param limitBucketId 한도 버킷
 * @param usedWon 사용액. 음수는 받지 않는다
 */
public record BucketUsageInput(String limitBucketId, StateInput<Long> usedWon) {

	public BucketUsageInput {
		EngineChecks.requireToken(limitBucketId, "limitBucketId");
		Objects.requireNonNull(usedWon, "usedWon");
		EngineChecks.requireOptionalNonNegative(usedWon.value(), "usedWon");
	}

}
