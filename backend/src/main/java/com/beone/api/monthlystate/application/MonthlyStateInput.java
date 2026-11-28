package com.beone.api.monthlystate.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * 월간 상태 수동 입력. 웹 계층이 형식 검증을 마친 값이며 이 카드·이 달의 입력을 통째로 교체한다. {@code null}·빈 목록은 미입력이다.
 *
 * @param previousMonthPerformanceWon 전월 실적. 음수 가능
 * @param limitBucketUsages 한도 묶음 사용액(요청 순서, ID 중복 없음)
 * @param otherBucketsUsedWon 나머지 한도 묶음 사용액
 * @param selectedServices 고른 택1 서비스(요청 순서, ID 중복 없음)
 * @param noServiceSelection 선택 없음. {@code selectedServices}와 함께 올 수 없다
 * @param registeredOn 카드 사용 등록일. 기준 기간은 그날 하루다
 */
public record MonthlyStateInput(Amount previousMonthPerformanceWon, List<BucketUsage> limitBucketUsages,
		Amount otherBucketsUsedWon, List<Selection> selectedServices, NoSelection noServiceSelection,
		LocalDate registeredOn) {

	public MonthlyStateInput {
		limitBucketUsages = (limitBucketUsages == null) ? List.of() : List.copyOf(limitBucketUsages);
		selectedServices = (selectedServices == null) ? List.of() : List.copyOf(selectedServices);
		if (!selectedServices.isEmpty() && noServiceSelection != null) {
			throw new IllegalArgumentException("selectedServices and noServiceSelection are exclusive");
		}
	}

	/**
	 * 규칙(매핑된 상품)이 있어야 해석할 수 있는 항목을 보냈는지. 매핑 확인 필요(409)의 기준이다.
	 * @return 한도 묶음 사용액·택1 선택·선택 없음 중 하나라도 보냈으면 {@code true}
	 */
	public boolean needsRule() {
		return !this.limitBucketUsages.isEmpty() || !this.selectedServices.isEmpty() || this.noServiceSelection != null;
	}

	/**
	 * 금액과 기준 기간.
	 *
	 * @param valueWon 원 단위 정수
	 * @param period 기준 기간
	 * @param referencedCodefId 사용자가 보고 확인한 CODEF 실적 참고값 ID(전월 실적만, D-16). 없으면 {@code null}
	 * @param referencedInferredSyncId 사용자가 보고 확인한 승인 합계 추정값의 근거 동기화 ID(전월 실적만, #289). 없으면 {@code null}
	 */
	public record Amount(long valueWon, DatePeriod period, Long referencedCodefId, Long referencedInferredSyncId) {

		public Amount {
			Objects.requireNonNull(period, "period");
		}

		/**
		 * 참고값 없이 입력한 금액.
		 * @param valueWon 원 단위 정수
		 * @param period 기준 기간
		 */
		public Amount(long valueWon, DatePeriod period) {
			this(valueWon, period, null, null);
		}

		/**
		 * 실적 참고값만 참고한 금액.
		 * @param valueWon 원 단위 정수
		 * @param period 기준 기간
		 * @param referencedCodefId CODEF 실적 참고값 ID. 없으면 {@code null}
		 */
		public Amount(long valueWon, DatePeriod period, Long referencedCodefId) {
			this(valueWon, period, referencedCodefId, null);
		}

	}

	/**
	 * 한도 묶음 사용액.
	 *
	 * @param limitBucketId 한도 묶음 ID
	 * @param valueWon 0 이상
	 * @param period 기준 기간
	 */
	public record BucketUsage(String limitBucketId, long valueWon, DatePeriod period) {

		public BucketUsage {
			Objects.requireNonNull(limitBucketId, "limitBucketId");
			Objects.requireNonNull(period, "period");
			if (valueWon < 0) {
				throw new IllegalArgumentException("bucket usage must not be negative");
			}
		}

	}

	/**
	 * 고른 택1 서비스.
	 *
	 * @param serviceId 서비스 ID
	 * @param effectiveFrom 적용일. 없으면 {@code null}
	 */
	public record Selection(String serviceId, LocalDate effectiveFrom) {

		public Selection {
			Objects.requireNonNull(serviceId, "serviceId");
		}

	}

	/**
	 * 선택 없음.
	 *
	 * @param effectiveFrom 적용일. 없으면 {@code null}
	 */
	public record NoSelection(LocalDate effectiveFrom) {
	}

}
