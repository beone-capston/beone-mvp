package com.beone.api.payment.application;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.beone.api.ledger.application.LedgerUsageEffect;

/**
 * 모의 취소의 실적·혜택·한도 영향을 확정({@code confirmed})과 {@code 확인 필요}({@code needsConfirmation})로 나눈다(D-16·D-17).
 *
 * <ul>
 * <li>{@code PERFORMANCE}: 카드의 취소 귀속 규칙이 확정({@code RECEIPT_MONTH})이고 스냅샷 관계가 분명할 때만 취소 달에
 * {@code -취소 금액}을 확정한다. 귀속 규칙이 없거나 미확정이면 원 승인 달(취소 달이 다르면 두 달 모두)을 확인 필요로 둔다.</li>
 * <li>{@code BENEFIT}: 4종 규칙 어디에도 혜택 되돌림 조항이 없어 혜택 기여가 확정 0원이 아닌 서비스마다 확인 필요다.</li>
 * <li>{@code LIMIT_USAGE}: 한도 복원 조항이 없어 승인 때 차감액이 0이 아닌(모름 포함) 버킷마다 확인 필요다.</li>
 * </ul>
 * 미확정 값을 0으로 바꾸지 않는다. 부분취소 뒤 혜택 재계산(D-03 절사)은 되돌림 규칙이 확정된 카드에만 쓰므로 지금은 생기지 않는다.
 */
final class CancellationImpacts {

	static final String PERFORMANCE = "PERFORMANCE";

	static final String BENEFIT = "BENEFIT";

	static final String LIMIT_USAGE = "LIMIT_USAGE";

	static final String RECEIPT_MONTH = "RECEIPT_MONTH";

	static final String ATTRIBUTION_UNRESOLVED = "취소 귀속월이 약관에서 확정되지 않았습니다.";

	static final String NO_RULE = "판정일에 쓸 수 있는 검증 규칙이 없어 취소 귀속월을 정할 수 없습니다.";

	static final String BEFORE_SNAPSHOT = "원 승인이 월간 상태 입력 시각보다 앞서 입력값에 포함됐는지 알 수 없습니다.";

	static final String STATE_UNAVAILABLE = "해제한 카드라 월간 상태 입력 시각을 확인할 수 없습니다.";

	static final String BENEFIT_UNRESOLVED = "혜택 되돌림 규칙이 약관에서 확정되지 않았습니다.";

	static final String LIMIT_UNRESOLVED = "한도 복원 시점 근거가 확정되지 않았습니다.";

	private CancellationImpacts() {
	}

	/**
	 * @param userCardId 승인 카드
	 * @param approvalMonth 원 승인 달(원장 승인 행의 거래일 달)
	 * @param cancellationMonth 취소 달
	 * @param amountWon 이번 취소 금액
	 * @param performance 실적 차감 판단
	 * @param approvedBenefit 승인 시점 혜택
	 * @param benefitServiceIds 혜택 기여가 확정 0원이 아닌 서비스
	 * @param usageEffects 승인 행의 버킷 차감액
	 */
	static CancellationDocument.Impact of(String userCardId, YearMonth approvalMonth, YearMonth cancellationMonth,
			long amountWon, PerformanceBasis performance, ApprovalDocument.Amount approvedBenefit,
			List<String> benefitServiceIds, List<LedgerUsageEffect> usageEffects) {
		List<CancellationDocument.ImpactItem> confirmed = new ArrayList<>();
		List<CancellationDocument.ImpactItem> pending = new ArrayList<>();
		if (performance.confirmed()) {
			confirmed.add(new CancellationDocument.ImpactItem(PERFORMANCE, userCardId, cancellationMonth.toString(),
					null, null, -amountWon, RECEIPT_MONTH, null));
		}
		else {
			List<YearMonth> months = new ArrayList<>();
			months.add(performance.onlyCancellationMonth() ? cancellationMonth : approvalMonth);
			if (!performance.onlyCancellationMonth() && !cancellationMonth.equals(approvalMonth)) {
				months.add(cancellationMonth);
			}
			months.forEach(month -> pending.add(new CancellationDocument.ImpactItem(PERFORMANCE, userCardId,
					month.toString(), null, null, null, null, performance.reason())));
		}
		if (!approvedBenefit.isConfirmedZero()) {
			if (benefitServiceIds.isEmpty()) {
				pending.add(new CancellationDocument.ImpactItem(BENEFIT, userCardId, null, null, null, null, null,
						BENEFIT_UNRESOLVED));
			}
			benefitServiceIds.forEach(serviceId -> pending.add(new CancellationDocument.ImpactItem(BENEFIT, userCardId,
					null, serviceId, null, null, null, BENEFIT_UNRESOLVED)));
		}
		usageEffects.stream()
			.filter(effect -> !Long.valueOf(0).equals(effect.deductionWon()))
			.forEach(effect -> pending.add(new CancellationDocument.ImpactItem(LIMIT_USAGE, userCardId, null, null,
					effect.limitBucketId(), null, null, LIMIT_UNRESOLVED)));
		return new CancellationDocument.Impact(List.copyOf(confirmed), List.copyOf(pending));
	}

	/**
	 * 실적 차감 판단.
	 *
	 * @param confirmed {@code RECEIPT_MONTH}로 확정했는지
	 * @param reason 확정하지 못한 이유. 확정이면 {@code null}
	 * @param onlyCancellationMonth 귀속 규칙은 있으나 스냅샷 관계만 모를 때 참. 영향 달은 귀속월(취소 달) 하나다
	 */
	record PerformanceBasis(boolean confirmed, String reason, boolean onlyCancellationMonth) {

		PerformanceBasis {
			if (confirmed != (reason == null)) {
				throw new IllegalArgumentException("reason is present exactly when not confirmed");
			}
		}

		static PerformanceBasis receiptMonth() {
			return new PerformanceBasis(true, null, false);
		}

		static PerformanceBasis noAttribution(String reason) {
			return new PerformanceBasis(false, Objects.requireNonNull(reason), false);
		}

		static PerformanceBasis snapshotUnknown(String reason) {
			return new PerformanceBasis(false, Objects.requireNonNull(reason), true);
		}

	}

}
