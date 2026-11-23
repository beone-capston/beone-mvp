package com.beone.api.benefit.engine;

/**
 * 그 카드로 승인했을 때 한도 버킷 하나에 더해지는 사용액(D-18 K1, D-22).
 *
 * <p>승인 때 승인 카드의 항목만 원장에 저장한다. {@code deductionWon=null}이면 차감액을 모르므로 이후 그 버킷 사용액을
 * {@code UNKNOWN}으로 넘긴다. 0으로 저장하지 않는다.
 *
 * @param userCardId 보유 카드 ID
 * @param limitBucketId 한도 버킷
 * @param deductionWon 차감액. 모르면 null
 */
public record BucketUsageEffect(String userCardId, String limitBucketId, Long deductionWon) {

	public BucketUsageEffect {
		EngineChecks.requireToken(userCardId, "userCardId");
		EngineChecks.requireToken(limitBucketId, "limitBucketId");
		EngineChecks.requireOptionalNonNegative(deductionWon, "deductionWon");
	}

}
