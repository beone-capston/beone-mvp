package com.beone.api.recommendation.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.beone.api.benefit.engine.BucketUsageInput;
import com.beone.api.benefit.engine.StateInput;
import com.beone.api.ledger.application.LedgerActivity;
import com.beone.api.ledger.application.LedgerEntryType;
import com.beone.api.ledger.application.LedgerUsageEffect;

/**
 * 월간 상태 스냅샷 값에 스냅샷 뒤 모의 승인 차감액을 더해 엔진의 한도 사용액 유효값을 만든다(D-16·D-17, #120).
 *
 * <p>관리자 승인 규칙(결정 28 보충):
 * <ul>
 * <li>승인 차감액은 {@code limitBucketUsages}에 입력된 같은 버킷 ID에 더한다. 입력되지 않은 버킷 ID의 차감액은 계약 정의
 * ("limitBucketUsages에 없는 한도 묶음의 사용액")대로 {@code otherBucketsUsedWon}에 더한다. 더할 값이 미입력이면 미입력 그대로다.</li>
 * <li>차감액을 모르는({@code null}) 승인이 있거나, 차감액이 0이 아닌 승인을 스냅샷 뒤에 취소했으면 그 버킷은 {@code 확인 필요}다. 4종
 * 규칙 모두 한도 복원 확정 규칙이 없기 때문이다(D-17). 스냅샷 전 승인을 스냅샷 뒤 취소한 경우도 같다. 0으로 대신하지 않는다.</li>
 * <li>이 경우는 엔진 입구에 {@code POST_SNAPSHOT_ACTIVITY_UNRESOLVED}로 넘긴다(결정 32 Q6). 사용자는 값을 입력했으므로
 * {@code NOT_ENTERED}가 아니다. 스냅샷 값이 원래 미입력이면 그 값을 그대로 둔다.</li>
 * <li>값을 더해도 출처는 {@code MANUAL}, 기준 기간·시각은 스냅샷 값 그대로다.</li>
 * </ul>
 */
final class SnapshotUsage {

	private SnapshotUsage() {
	}

	/**
	 * 버킷별 사용액에 스냅샷 뒤 활동을 반영한다.
	 * @param buckets 스냅샷 값으로 만든 버킷 입력(입력한 버킷만)
	 * @param otherBuckets 스냅샷 값으로 만든 그 밖의 버킷 사용액
	 * @param activities 스냅샷 뒤 원장 활동. 스냅샷이 없으면 빈 목록
	 * @return 반영한 결과
	 */
	static Adjusted adjust(List<BucketUsageInput> buckets, StateInput<Long> otherBuckets,
			List<LedgerActivity> activities) {
		if (activities.isEmpty()) {
			return new Adjusted(buckets, otherBuckets);
		}
		Set<String> entered = new HashSet<>();
		buckets.forEach(bucket -> entered.add(bucket.limitBucketId()));
		Map<String, Long> added = new LinkedHashMap<>();
		Set<String> unresolved = new HashSet<>();
		long otherAdded = 0;
		boolean otherUnresolved = false;
		for (LedgerActivity activity : activities) {
			for (LedgerUsageEffect effect : activity.usageEffects()) {
				boolean unknown = (activity.entryType() == LedgerEntryType.APPROVAL) ? effect.deductionWon() == null
						: effect.deductionWon() == null || effect.deductionWon() != 0;
				long amount = (activity.entryType() == LedgerEntryType.APPROVAL && !unknown) ? effect.deductionWon() : 0;
				if (entered.contains(effect.limitBucketId())) {
					if (unknown) {
						unresolved.add(effect.limitBucketId());
					}
					added.merge(effect.limitBucketId(), amount, Long::sum);
				}
				else {
					otherUnresolved |= unknown;
					otherAdded += amount;
				}
			}
		}
		List<BucketUsageInput> adjusted = new ArrayList<>();
		for (BucketUsageInput bucket : buckets) {
			String id = bucket.limitBucketId();
			adjusted.add(new BucketUsageInput(id, unresolved.contains(id) ? unresolvedOrSame(bucket.usedWon())
					: plus(bucket.usedWon(), added.getOrDefault(id, 0L))));
		}
		return new Adjusted(adjusted, otherUnresolved ? unresolvedOrSame(otherBuckets) : plus(otherBuckets, otherAdded));
	}

	// 값이 없으면(미입력) 그대로 두고, 있으면 스냅샷 값의 출처·기간·시각을 유지한 채 더한다.
	private static StateInput<Long> plus(StateInput<Long> value, long amount) {
		if (amount == 0 || value.value() == null || !value.usable()) {
			return value;
		}
		return new StateInput<>(value.value() + amount, value.source(), value.periodFrom(), value.periodTo(),
				value.updatedAt(), true, null);
	}

	// 원래 모르는 값(미입력 등)은 사유를 그대로 두고, 입력한 값만 스냅샷 뒤 활동 영향 불확정으로 바꾼다.
	private static StateInput<Long> unresolvedOrSame(StateInput<Long> value) {
		return (value.value() == null) ? value : StateInput.postSnapshotActivityUnresolved();
	}

	/**
	 * 반영 결과.
	 *
	 * @param buckets 버킷별 사용액 입력
	 * @param otherBuckets 그 밖의 버킷 사용액 입력
	 */
	record Adjusted(List<BucketUsageInput> buckets, StateInput<Long> otherBuckets) {
	}

}
