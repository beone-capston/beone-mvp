package com.beone.api.benefit.primitive;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.beone.api.benefit.card.LimitBucketId;
import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.result.LimitReason;
import com.beone.api.benefit.rule.LimitBucket;
import com.beone.api.benefit.rule.LimitTier;
import com.beone.api.benefit.state.StateValue;

/**
 * 서비스 하나의 한도 체인과 입력 사용액(D-18 K1). 남은 한도는 체인 내 버킷별 (한도 − 입력 사용액)의 최솟값이다.
 *
 * <p>
 * 체인 버킷의 사용액이 없으면 {@code UNKNOWN}으로 보며, 다른 버킷 사용액(otherBucketsUsed)이나 0원으로 대신하지 않는다.
 *
 * @param chain 서비스가 속한 버킷과 상위 버킷. 한도가 없는 서비스는 비어 있다
 * @param used 버킷별 입력 사용액
 */
public record ServiceLimits(List<LimitBucket> chain, Map<LimitBucketId, StateValue<Won>> used) {

	/** 약관상 한도가 없는 서비스. */
	public static final ServiceLimits NONE = new ServiceLimits(List.of(), Map.of());

	public ServiceLimits {
		chain = List.copyOf(chain);
		used = Map.copyOf(used);
	}

	/**
	 * 체인 안에 입력 실적이 어느 한도 구간에도 들지 않는 버킷이 있으면 참.
	 */
	boolean belowEveryTierOfSomeBucket(Won performance) {
		return chain.stream().anyMatch(bucket -> PerformanceTiers.limitTier(bucket, performance).isEmpty());
	}

	/**
	 * 체인 안에 입력 실적이 최상위 한도 구간이 아닌 버킷이 있으면 참. 간주 실적으로 한도가 커질 수 있다는 뜻이다.
	 */
	boolean belowHighestTierOfSomeBucket(Won performance) {
		return chain.stream().anyMatch(bucket -> {
			Optional<LimitTier> tier = PerformanceTiers.limitTier(bucket, performance);
			return tier.isEmpty() || !bucket.tiers().get(bucket.tiers().size() - 1).equals(tier.get());
		});
	}

	/**
	 * 남은 한도를 판정한다. 모든 버킷이 한도 구간에 든 실적에서만 호출한다.
	 *
	 * @param performance 구간을 고를 실적
	 * @return 남은 한도(0원 포함) 또는 판정하지 못한 사유
	 */
	Remaining remaining(Won performance) {
		Objects.requireNonNull(performance, "performance");
		long smallest = Long.MAX_VALUE;
		String unknown = null;
		String exceeded = null;
		for (LimitBucket bucket : chain) {
			long cap = PerformanceTiers.limitTier(bucket, performance).orElseThrow().monthlyCap().value();
			StateValue<Won> value = used.get(bucket.id());
			if (value == null || !value.isKnown()) {
				unknown = "used benefit of limit bucket " + bucket.id() + " is UNKNOWN";
				continue;
			}
			long usedWon = value.knownValue().value();
			if (usedWon > cap) {
				exceeded = "used benefit of limit bucket " + bucket.id() + " exceeds its limit";
				continue;
			}
			smallest = Math.min(smallest, cap - usedWon);
		}
		if (exceeded != null) {
			return new Remaining(Optional.empty(), Optional.of(exceeded), Optional.of(LimitReason.USAGE_EXCEEDS_LIMIT));
		}
		if (unknown != null) {
			return new Remaining(Optional.empty(), Optional.of(unknown), Optional.empty());
		}
		return new Remaining(Optional.of(Won.of(smallest)), Optional.empty(), Optional.empty());
	}

	/**
	 * 남은 한도 판정 결과. 값이 있으면 사유가 없고, 값이 없으면 확인 필요 사유가 있다.
	 */
	record Remaining(Optional<Won> won, Optional<String> pendingReason, Optional<LimitReason> limitReason) {

	}

}
