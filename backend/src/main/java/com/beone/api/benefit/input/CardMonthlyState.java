package com.beone.api.benefit.input;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.card.LimitBucketId;
import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.state.StateValue;

/**
 * 보유 카드 한 장의 월간 상태. 모든 값은 출처와 함께 보관한다(D-04).
 *
 * @param priorPerformance 직접 입력된 전월 실적. 비어 있으면 원장에서 산출해야 한다
 * @param usedBenefits 한도 묶음별 이번 달 혜택 사용액
 * @param otherBucketsUsed {@code usedBenefits}에 없는 한도 묶음의 사용액. 입력이 없으면 {@code UNKNOWN}이며 0원으로 보지 않는다. 한도 판정은 체인 버킷의 값을 대신하는 데 쓰지 않는다(D-18 K1)
 * @param serviceSelections 택1 선택(서비스 또는 {@code NONE})과 적용 시작일. 비어 있으면 입력 없음이다
 */
public record CardMonthlyState(CardId cardId, Optional<StateValue<Won>> priorPerformance,
		Map<LimitBucketId, StateValue<Won>> usedBenefits, StateValue<Won> otherBucketsUsed,
		List<StateValue<ServiceSelection>> serviceSelections) {

	public CardMonthlyState {
		Objects.requireNonNull(cardId, "cardId");
		Objects.requireNonNull(priorPerformance, "priorPerformance");
		usedBenefits = Map.copyOf(usedBenefits);
		usedBenefits.forEach((bucket, used) -> requireNotNegative(used, "used benefit of " + bucket));
		Objects.requireNonNull(otherBucketsUsed, "otherBucketsUsed");
		requireNotNegative(otherBucketsUsed, "used benefit of other buckets");
		serviceSelections = List.copyOf(serviceSelections);
	}

	private static void requireNotNegative(StateValue<Won> value, String name) {
		if (value.isKnown() && value.knownValue().isNegative()) {
			throw new IllegalArgumentException(name + " must not be negative");
		}
	}

}
