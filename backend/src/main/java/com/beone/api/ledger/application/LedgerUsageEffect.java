package com.beone.api.ledger.application;

import java.util.Objects;

/**
 * 승인 행이 한도 버킷 하나에 더하는 사용액(D-18 K1). 모르면 {@code deductionWon}이 {@code null}이고 0으로 바꾸지 않는다.
 *
 * @param limitBucketId 한도 버킷
 * @param deductionWon 차감액. 모르면 {@code null}
 */
public record LedgerUsageEffect(String limitBucketId, Long deductionWon) {

	public LedgerUsageEffect {
		Objects.requireNonNull(limitBucketId, "limitBucketId");
		if (deductionWon != null && deductionWon < 0) {
			throw new IllegalArgumentException("deductionWon must not be negative");
		}
	}

}
