package com.beone.api.benefit.rule;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.beone.api.benefit.card.LimitBucketId;

/**
 * 월 혜택 한도 묶음.
 * 상위 한도가 있으면 상위 한도도 함께 차감한다. 여러 영역 한도 위의 통합 한도를 이렇게 표현한다.
 */
public record LimitBucket(LimitBucketId id, List<LimitTier> tiers, Optional<LimitBucketId> parent) {

	public LimitBucket {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(parent, "parent");
		tiers = Tiers.requireStrictlyAscending(tiers, LimitTier::minimumPerformance, "limit bucket " + id);
		if (parent.isPresent() && parent.get().equals(id)) {
			throw new IllegalArgumentException("limit bucket " + id + " cannot be its own parent");
		}
	}

	/**
	 * 한도액이 전월 실적에 따라 달라지면 참. 구간이 둘 이상이거나 단일 구간의 최소 실적이 0원보다 크면 해당한다.
	 */
	public boolean dependsOnPerformance() {
		return tiers.size() > 1 || tiers.get(0).minimumPerformance().isPositive();
	}

}
