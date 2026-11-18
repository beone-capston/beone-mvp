package com.beone.api.monthlystate.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.beone.api.monthlystate.application.SourceSelection;
import com.beone.api.monthlystate.application.StateSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * {@code PUT /api/v1/me/cards/{userCardId}/monthly-states/{month}/source-selections} 요청 본문(docs/api/monthly-states.md).
 * 네 필드 모두 필수이고 알 수 없는 필드는 거부한다(월간 상태 PUT과 같은 규칙).
 *
 * @param item 항목. {@code previousMonthPerformanceWon}, {@code otherBucketsUsedWon}, {@code registeredOn},
 * {@code limitBucketUsage:}{@code {limitBucketId}}만 받는다. 한도 묶음 ID가 카드 규칙에 있는지는 충돌 후보가 생길 때 본다
 * @param period 기준 기간. 시작일이 종료일보다 늦을 수 없다
 * @param selectedSource {@code MANUAL}, {@code CODEF}, {@code INFERRED}만 받는다
 * @param conflictVersion 월간 상태 조회 {@code conflicts[]}의 충돌 버전
 */
@CrossFieldChecked
public record SourceSelectionRequest(@NotBlank(message = "필수 값입니다.") String item,
		@NotNull(message = "필수 값입니다.") @Valid MonthlyStateRequest.PeriodRequest period,
		@NotBlank(message = "필수 값입니다.") String selectedSource,
		@NotBlank(message = "필수 값입니다.") String conflictVersion)
		implements UnknownFieldRejecting, CrossFieldChecked.Checkable {

	private static final Set<String> FIXED_ITEMS = Set.of("previousMonthPerformanceWon", "otherBucketsUsedWon",
			"registeredOn");

	private static final String BUCKET_ITEM_PREFIX = "limitBucketUsage:";

	private static final Set<String> SOURCES = Set.of("MANUAL", "CODEF", "INFERRED");

	private static final String NOT_ALLOWED = "허용되지 않는 값입니다.";

	@Override
	public List<CrossFieldChecked.Violation> crossFieldViolations() {
		List<CrossFieldChecked.Violation> violations = new ArrayList<>();
		if (this.item != null && !this.item.isBlank() && !allowedItem(this.item)) {
			violations.add(new CrossFieldChecked.Violation("item", NOT_ALLOWED));
		}
		if (this.selectedSource != null && !this.selectedSource.isBlank() && !SOURCES.contains(this.selectedSource)) {
			violations.add(new CrossFieldChecked.Violation("selectedSource", NOT_ALLOWED));
		}
		return violations;
	}

	SourceSelection toSelection() {
		return new SourceSelection(this.item, this.period.toPeriod(), StateSource.valueOf(this.selectedSource),
				this.conflictVersion);
	}

	private static boolean allowedItem(String item) {
		if (FIXED_ITEMS.contains(item)) {
			return true;
		}
		return item.startsWith(BUCKET_ITEM_PREFIX) && !item.substring(BUCKET_ITEM_PREFIX.length()).isBlank();
	}

}
