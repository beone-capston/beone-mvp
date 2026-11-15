package com.beone.api.benefit.engine;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 카드 한 장의 월간 상태.
 *
 * @param previousMonthPerformanceWon 전월 실적. 음수가 올 수 있다(D-17)
 * @param limitBucketUsages 버킷별 사용액 유효값
 * @param otherBucketsUsedWon 목록에 없는 한도 묶음의 사용액
 * @param serviceSelectionStatus {@code UNKNOWN}(미입력), {@code SELECTED}, {@code NONE}
 * @param serviceSelections 택1 선택. {@code UNKNOWN}이면 비고, {@code NONE}이면 서비스 없는 항목 하나다
 */
public record MonthlyStateInput(StateInput<Long> previousMonthPerformanceWon, List<BucketUsageInput> limitBucketUsages,
		StateInput<Long> otherBucketsUsedWon, String serviceSelectionStatus, List<ServiceSelectionInput> serviceSelections) {

	public MonthlyStateInput {
		Objects.requireNonNull(previousMonthPerformanceWon, "previousMonthPerformanceWon");
		limitBucketUsages = EngineChecks.copy(limitBucketUsages, "limitBucketUsages");
		EngineChecks.requireDistinct(limitBucketUsages.stream().map(BucketUsageInput::limitBucketId).toList(),
				"limitBucketId");
		Objects.requireNonNull(otherBucketsUsedWon, "otherBucketsUsedWon");
		EngineChecks.requireOptionalNonNegative(otherBucketsUsedWon.value(), "otherBucketsUsedWon");
		EngineChecks.requireOneOf(Set.of("UNKNOWN", "SELECTED", "NONE"), serviceSelectionStatus, "serviceSelectionStatus");
		serviceSelections = EngineChecks.copy(serviceSelections, "serviceSelections");
		boolean named = serviceSelections.stream().allMatch(selection -> selection.serviceId() != null);
		boolean valid = switch (serviceSelectionStatus) {
			case "UNKNOWN" -> serviceSelections.isEmpty();
			case "SELECTED" -> !serviceSelections.isEmpty() && named;
			default -> serviceSelections.size() == 1 && serviceSelections.get(0).serviceId() == null;
		};
		if (!valid) {
			throw new IllegalArgumentException("serviceSelections do not match status " + serviceSelectionStatus);
		}
	}

}
