package com.beone.api.monthlystate.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

/**
 * 카드 한 장·한 달의 월간 상태 조회 결과(docs/api/monthly-states.md). 입력한 적이 없으면 모든 값이 미입력이다.
 *
 * @param userCardId 보유 카드 외부 ID
 * @param month 계산 대상 월
 * @param snapshotAt 수동 입력 시각(서울). 입력이 없으면 {@code null}
 * @param previousMonthPerformanceWon 전월 실적. 음수일 수 있다(D-17)
 * @param limitBucketUsages 입력한 한도 묶음 사용액(요청 순서)
 * @param otherBucketsUsedWon 나머지 한도 묶음 사용액
 * @param selectedServices 고른 택1 서비스(요청 순서)
 * @param serviceSelectionStatus 택1 입력 상태
 * @param noServiceSelection 선택 없음. {@code NONE}이 아니면 {@code null}
 * @param registeredOn 카드 사용 등록일. 이 달 입력의 일부다(결정 27 A3-5)
 * @param afterSnapshot 스냅샷 뒤 모의 승인 요약. {@code snapshotAt}이 없으면 {@code null}
 */
public record MonthlyStateView(String userCardId, YearMonth month, LocalDateTime snapshotAt,
		StateValue<Long> previousMonthPerformanceWon, List<BucketUsage> limitBucketUsages,
		StateValue<Long> otherBucketsUsedWon, List<Selection> selectedServices,
		ServiceSelectionStatus serviceSelectionStatus, NoSelection noServiceSelection,
		StateValue<LocalDate> registeredOn, AfterSnapshot afterSnapshot) {

	public MonthlyStateView {
		Objects.requireNonNull(userCardId, "userCardId");
		Objects.requireNonNull(month, "month");
		Objects.requireNonNull(previousMonthPerformanceWon, "previousMonthPerformanceWon");
		limitBucketUsages = List.copyOf(limitBucketUsages);
		Objects.requireNonNull(otherBucketsUsedWon, "otherBucketsUsedWon");
		selectedServices = List.copyOf(selectedServices);
		Objects.requireNonNull(serviceSelectionStatus, "serviceSelectionStatus");
		Objects.requireNonNull(registeredOn, "registeredOn");
		if ((serviceSelectionStatus == ServiceSelectionStatus.NONE) != (noServiceSelection != null)) {
			throw new IllegalArgumentException("noServiceSelection must be present exactly when status is NONE");
		}
		if ((serviceSelectionStatus == ServiceSelectionStatus.SELECTED) == selectedServices.isEmpty()) {
			throw new IllegalArgumentException("selectedServices must be non-empty exactly when status is SELECTED");
		}
	}

	/**
	 * 한도 묶음 하나의 사용액.
	 *
	 * @param limitBucketId 카드 규칙의 한도 묶음 ID
	 * @param usage 사용액(0 이상)
	 */
	public record BucketUsage(String limitBucketId, StateValue<Long> usage) {

		public BucketUsage {
			Objects.requireNonNull(limitBucketId, "limitBucketId");
			Objects.requireNonNull(usage, "usage");
		}

	}

	/**
	 * 고른 택1 서비스 하나. 적용일이 없으면 계산에 쓰지 않는다(D-16, 결정 27 A3-4).
	 *
	 * @param serviceId 카드 규칙의 선택형 서비스 ID
	 * @param effectiveFrom 적용일. 없으면 {@code null}
	 * @param updatedAt 입력 시각(서울)
	 */
	public record Selection(String serviceId, LocalDate effectiveFrom, LocalDateTime updatedAt) {

		public Selection {
			Objects.requireNonNull(serviceId, "serviceId");
			Objects.requireNonNull(updatedAt, "updatedAt");
		}

		/** 적용일이 있으면 계산에 쓸 수 있다. */
		public boolean usable() {
			return this.effectiveFrom != null;
		}

		/** 쓸 수 없는 이유. */
		public StateReason reason() {
			return usable() ? null : StateReason.NOT_ENTERED;
		}

	}

	/**
	 * 선택 없음(사용자 명시 입력). 적용일이 없으면 계산에 쓰지 않는다.
	 *
	 * @param effectiveFrom 적용일. 없으면 {@code null}
	 * @param updatedAt 입력 시각(서울)
	 */
	public record NoSelection(LocalDate effectiveFrom, LocalDateTime updatedAt) {

		public NoSelection {
			Objects.requireNonNull(updatedAt, "updatedAt");
		}

		/** 적용일이 있으면 계산에 쓸 수 있다. */
		public boolean usable() {
			return this.effectiveFrom != null;
		}

		/** 쓸 수 없는 이유. */
		public StateReason reason() {
			return usable() ? null : StateReason.NOT_ENTERED;
		}

	}

	/**
	 * 스냅샷 뒤 모의 승인·취소 요약. 화면 표시용이며 상태값을 덮어쓰지 않는다.
	 *
	 * <p>같은 카드·거래일이 그 달·기록 시각이 스냅샷보다 뒤인 자체 모의 원장 행으로 센다(#120). 승인 금액은 승인 행 합계이고 취소는 빼지
	 * 않고 건수로만 센다.
	 *
	 * @param approvalCount 승인 건수
	 * @param approvedAmountWon 승인 금액 합
	 * @param cancellationCount 취소 건수
	 */
	public record AfterSnapshot(int approvalCount, long approvedAmountWon, int cancellationCount) {

		/** 스냅샷 뒤 모의 승인·취소가 없을 때의 요약. */
		public static final AfterSnapshot NO_SIMULATED_PAYMENTS = new AfterSnapshot(0, 0, 0);

	}

}
