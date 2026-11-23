package com.beone.api.payment.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.util.List;

import com.beone.api.ledger.application.LedgerUsageEffect;
import org.junit.jupiter.api.Test;

/**
 * 취소 영향 분류(D-16·D-17). 기대값은 docs/api/mock-payments.md 취소 응답 예시(09174 톡톡: 실적·혜택·한도 모두 확인 필요)와
 * {@code impact.confirmed[]} 형식 설명({@code {"target":"PERFORMANCE","userCardId":"uc-1002","month":"2026-11","deltaWon":-4000,"basis":"RECEIPT_MONTH"}}),
 * "다른 달 취소 + 귀속 규칙 없음 → 두 달 모두 확인 필요"에서 썼다.
 */
class CancellationImpactsTest {

	private static final YearMonth NOV = YearMonth.of(2026, 11);

	private static final ApprovalDocument.Amount BENEFIT_2100 = new ApprovalDocument.Amount(2100L, "CONFIRMED");

	@Test
	void unresolvedAttributionCardMatchesTheContractExample() {
		CancellationDocument.Impact impact = CancellationImpacts.of("uc-1001", NOV, NOV, 4000,
				CancellationImpacts.PerformanceBasis.noAttribution(CancellationImpacts.ATTRIBUTION_UNRESOLVED),
				BENEFIT_2100, List.of("SVC-GREAT"), List.of(new LedgerUsageEffect("LB-GREAT", 2100L)));

		assertThat(impact.confirmed()).isEmpty();
		assertThat(impact.needsConfirmation()).containsExactly(
				new CancellationDocument.ImpactItem("PERFORMANCE", "uc-1001", "2026-11", null, null, null, null,
						"취소 귀속월이 약관에서 확정되지 않았습니다."),
				new CancellationDocument.ImpactItem("BENEFIT", "uc-1001", null, "SVC-GREAT", null, null, null,
						"혜택 되돌림 규칙이 약관에서 확정되지 않았습니다."),
				new CancellationDocument.ImpactItem("LIMIT_USAGE", "uc-1001", null, null, "LB-GREAT", null, null,
						"한도 복원 시점 근거가 확정되지 않았습니다."));
	}

	@Test
	void receiptMonthCardConfirmsPerformanceInTheCancellationMonth() {
		CancellationDocument.Impact impact = CancellationImpacts.of("uc-1002", NOV, NOV, 4000,
				CancellationImpacts.PerformanceBasis.receiptMonth(), new ApprovalDocument.Amount(0L, "CONFIRMED"),
				List.of(), List.of());

		assertThat(impact.confirmed()).containsExactly(new CancellationDocument.ImpactItem("PERFORMANCE", "uc-1002",
				"2026-11", null, null, -4000L, "RECEIPT_MONTH", null));
		// 확정 0원 혜택은 되돌릴 것이 없다
		assertThat(impact.needsConfirmation()).isEmpty();
	}

	@Test
	void otherMonthCancellationWithoutAttributionMarksBothMonths() {
		CancellationDocument.Impact impact = CancellationImpacts.of("uc-1001", YearMonth.of(2026, 10), NOV, 4000,
				CancellationImpacts.PerformanceBasis.noAttribution(CancellationImpacts.ATTRIBUTION_UNRESOLVED),
				new ApprovalDocument.Amount(0L, "CONFIRMED"), List.of(), List.of());

		assertThat(impact.needsConfirmation()).extracting(CancellationDocument.ImpactItem::month)
			.containsExactly("2026-10", "2026-11");
	}

	@Test
	void approvalBeforeTheSnapshotLeavesOnlyTheReceiptMonthUnconfirmed() {
		CancellationDocument.Impact impact = CancellationImpacts.of("uc-1002", YearMonth.of(2026, 10), NOV, 4000,
				CancellationImpacts.PerformanceBasis.snapshotUnknown(CancellationImpacts.BEFORE_SNAPSHOT),
				new ApprovalDocument.Amount(0L, "CONFIRMED"), List.of(), List.of());

		assertThat(impact.confirmed()).isEmpty();
		assertThat(impact.needsConfirmation()).containsExactly(new CancellationDocument.ImpactItem("PERFORMANCE",
				"uc-1002", "2026-11", null, null, null, null, CancellationImpacts.BEFORE_SNAPSHOT));
	}

	@Test
	void unknownBenefitWithoutServiceAndUnknownDeductionStayUnconfirmedButZeroDeductionIsSkipped() {
		CancellationDocument.Impact impact = CancellationImpacts.of("uc-1001", NOV, NOV, 1000,
				CancellationImpacts.PerformanceBasis.receiptMonth(), new ApprovalDocument.Amount(null, "NEEDS_CONFIRMATION"),
				List.of(), List.of(new LedgerUsageEffect("A", null), new LedgerUsageEffect("B", 0L)));

		assertThat(impact.needsConfirmation()).containsExactly(
				new CancellationDocument.ImpactItem("BENEFIT", "uc-1001", null, null, null, null, null,
						CancellationImpacts.BENEFIT_UNRESOLVED),
				new CancellationDocument.ImpactItem("LIMIT_USAGE", "uc-1001", null, null, "A", null, null,
						CancellationImpacts.LIMIT_UNRESOLVED));
	}

}
