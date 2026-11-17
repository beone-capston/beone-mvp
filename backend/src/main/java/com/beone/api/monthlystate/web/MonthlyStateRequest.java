package com.beone.api.monthlystate.web;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import com.beone.api.monthlystate.application.DatePeriod;
import com.beone.api.monthlystate.application.MonthlyStateInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * {@code PUT /api/v1/me/cards/{userCardId}/monthly-states/{month}} 요청 본문(docs/api/monthly-states.md). 모든 항목은 선택이고
 * 생략·{@code null}은 미입력이다. {@code source}·{@code updatedAt}은 서버가 정하므로 받지 않는다.
 *
 * @param previousMonthPerformanceWon 전월 실적. 음수를 받는다(D-17)
 * @param limitBucketUsages 한도 묶음 사용액. 같은 ID를 두 번 보낼 수 없다
 * @param otherBucketsUsedWon 나머지 한도 묶음 사용액. 0 이상
 * @param selectedServices 고른 택1 서비스. 같은 ID를 두 번 보낼 수 없다
 * @param noServiceSelection 선택 없음. {@code selectedServices} 항목과 함께 보낼 수 없다
 * @param registeredOn 카드 사용 등록일. 기준 기간은 그날 하루
 */
@CrossFieldChecked
public record MonthlyStateRequest(@Valid AmountRequest previousMonthPerformanceWon,
		List<@NotNull(message = "필수 값입니다.") @Valid BucketUsageRequest> limitBucketUsages,
		@Valid UsageRequest otherBucketsUsedWon,
		List<@NotNull(message = "필수 값입니다.") @Valid SelectionRequest> selectedServices,
		@Valid NoSelectionRequest noServiceSelection, @Valid RegisteredOnRequest registeredOn)
		implements UnknownFieldRejecting, CrossFieldChecked.Checkable {

	@Override
	public List<CrossFieldChecked.Violation> crossFieldViolations() {
		List<CrossFieldChecked.Violation> violations = new ArrayList<>();
		// 같은 ID 중복 금지(결정 27 A3-6)
		if (!unique(this.limitBucketUsages, BucketUsageRequest::limitBucketId)) {
			violations.add(new CrossFieldChecked.Violation("limitBucketUsages", "같은 한도 묶음을 두 번 보낼 수 없습니다."));
		}
		if (!unique(this.selectedServices, SelectionRequest::serviceId)) {
			violations.add(new CrossFieldChecked.Violation("selectedServices", "같은 서비스를 두 번 보낼 수 없습니다."));
		}
		// 서비스 선택과 선택 없음은 함께 보낼 수 없다(계약 제안 채택, 결정 27 A3-3)
		if (this.noServiceSelection != null && this.selectedServices != null && !this.selectedServices.isEmpty()) {
			violations.add(new CrossFieldChecked.Violation("noServiceSelection", "selectedServices와 함께 보낼 수 없습니다."));
		}
		return violations;
	}

	MonthlyStateInput toInput() {
		return new MonthlyStateInput(
				(this.previousMonthPerformanceWon == null) ? null : this.previousMonthPerformanceWon.toAmount(),
				(this.limitBucketUsages == null) ? null
						: this.limitBucketUsages.stream().map(BucketUsageRequest::toUsage).toList(),
				(this.otherBucketsUsedWon == null) ? null : this.otherBucketsUsedWon.toAmount(),
				(this.selectedServices == null) ? null
						: this.selectedServices.stream().map(SelectionRequest::toSelection).toList(),
				(this.noServiceSelection == null) ? null
						: new MonthlyStateInput.NoSelection(this.noServiceSelection.effectiveFrom()),
				(this.registeredOn == null) ? null : this.registeredOn.value());
	}

	private static <T> boolean unique(List<T> items, Function<T, String> id) {
		if (items == null) {
			return true;
		}
		Set<String> seen = new HashSet<>();
		for (T item : items) {
			if (item != null && id.apply(item) != null && !seen.add(id.apply(item))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 기준 기간. 시작일이 종료일보다 늦을 수 없다.
	 *
	 * @param from 필수. {@code YYYY-MM-DD}
	 * @param to 필수. {@code YYYY-MM-DD}
	 */
	@CrossFieldChecked
	public record PeriodRequest(
			@NotNull(message = "필수 값입니다.") @JsonDeserialize(using = StrictDateDeserializer.class) LocalDate from,
			@NotNull(message = "필수 값입니다.") @JsonDeserialize(using = StrictDateDeserializer.class) LocalDate to)
			implements UnknownFieldRejecting, CrossFieldChecked.Checkable {

		@Override
		public List<CrossFieldChecked.Violation> crossFieldViolations() {
			return (this.from != null && this.to != null && this.from.isAfter(this.to))
					? List.of(new CrossFieldChecked.Violation(null, "시작일은 종료일보다 늦을 수 없습니다.")) : List.of();
		}

		DatePeriod toPeriod() {
			return new DatePeriod(this.from, this.to);
		}

	}

	/**
	 * 전월 실적. 음수를 받는다(D-17).
	 *
	 * @param value 필수. JSON 정수
	 * @param period 필수
	 */
	public record AmountRequest(
			@NotNull(message = "필수 값입니다.") @JsonDeserialize(using = StrictWonDeserializer.class) Long value,
			@NotNull(message = "필수 값입니다.") @Valid PeriodRequest period) implements UnknownFieldRejecting {

		MonthlyStateInput.Amount toAmount() {
			return new MonthlyStateInput.Amount(this.value, this.period.toPeriod());
		}

	}

	/**
	 * 사용액. 0 이상이다.
	 *
	 * @param value 필수. 0 이상 JSON 정수
	 * @param period 필수
	 */
	public record UsageRequest(
			@NotNull(message = "필수 값입니다.") @Min(value = 0, message = "0 이상이어야 합니다.")
			@JsonDeserialize(using = StrictWonDeserializer.class) Long value,
			@NotNull(message = "필수 값입니다.") @Valid PeriodRequest period) implements UnknownFieldRejecting {

		MonthlyStateInput.Amount toAmount() {
			return new MonthlyStateInput.Amount(this.value, this.period.toPeriod());
		}

	}

	/**
	 * 한도 묶음 사용액.
	 *
	 * @param limitBucketId 필수. 카드 규칙의 한도 묶음 ID
	 * @param value 필수. 0 이상 JSON 정수
	 * @param period 필수
	 */
	public record BucketUsageRequest(@NotBlank(message = "필수 값입니다.") String limitBucketId,
			@NotNull(message = "필수 값입니다.") @Min(value = 0, message = "0 이상이어야 합니다.")
			@JsonDeserialize(using = StrictWonDeserializer.class) Long value,
			@NotNull(message = "필수 값입니다.") @Valid PeriodRequest period) implements UnknownFieldRejecting {

		MonthlyStateInput.BucketUsage toUsage() {
			return new MonthlyStateInput.BucketUsage(this.limitBucketId, this.value, this.period.toPeriod());
		}

	}

	/**
	 * 고른 택1 서비스. 적용일이 없으면 저장하되 계산에 쓰지 않는다(결정 27 A3-4).
	 *
	 * @param serviceId 필수. 카드 규칙의 선택형 서비스 ID
	 * @param effectiveFrom 선택. {@code YYYY-MM-DD}
	 */
	public record SelectionRequest(@NotBlank(message = "필수 값입니다.") String serviceId,
			@JsonDeserialize(using = StrictDateDeserializer.class) LocalDate effectiveFrom)
			implements UnknownFieldRejecting {

		MonthlyStateInput.Selection toSelection() {
			return new MonthlyStateInput.Selection(this.serviceId, this.effectiveFrom);
		}

	}

	/**
	 * 선택 없음. 적용일이 없으면 저장하되 계산에 쓰지 않는다.
	 *
	 * @param effectiveFrom 선택. {@code YYYY-MM-DD}
	 */
	public record NoSelectionRequest(@JsonDeserialize(using = StrictDateDeserializer.class) LocalDate effectiveFrom)
			implements UnknownFieldRejecting {
	}

	/**
	 * 카드 사용 등록일. 기준 기간은 등록일 하루다(D-23, 결정 27 A3-5).
	 *
	 * @param value 필수. {@code YYYY-MM-DD}
	 * @param period 필수. {@code from}·{@code to}가 모두 {@code value}와 같아야 한다
	 */
	@CrossFieldChecked
	public record RegisteredOnRequest(
			@NotNull(message = "필수 값입니다.") @JsonDeserialize(using = StrictDateDeserializer.class) LocalDate value,
			@NotNull(message = "필수 값입니다.") @Valid PeriodRequest period)
			implements UnknownFieldRejecting, CrossFieldChecked.Checkable {

		@Override
		public List<CrossFieldChecked.Violation> crossFieldViolations() {
			boolean comparable = this.value != null && this.period != null && this.period.from() != null
					&& this.period.to() != null;
			return (comparable && !(this.value.equals(this.period.from()) && this.value.equals(this.period.to())))
					? List.of(new CrossFieldChecked.Violation("period", "기준 기간은 등록일 하루여야 합니다.")) : List.of();
		}

	}

}
