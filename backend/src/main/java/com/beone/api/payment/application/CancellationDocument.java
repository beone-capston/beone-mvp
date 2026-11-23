package com.beone.api.payment.application;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 모의 취소 응답 본문(docs/api/mock-payments.md {@code POST cancellations}). 같은 키·같은 지문의 재요청에는 저장한 이 값을 돌려준다.
 *
 * @param cancellationId 취소 ID
 * @param simulatedPaymentId 결제 ID
 * @param amountWon 취소 금액(양수)
 * @param cancelledAt 취소 시각
 * @param ledgerTransactionId 원장 취소 행
 * @param paymentStatus 취소 뒤 결제 상태
 * @param cancellableAmountWon 취소 뒤 남은 취소 가능 금액
 * @param impact 확정 규칙으로 바뀐 값과 {@code 확인 필요}가 된 값(D-17)
 */
public record CancellationDocument(String cancellationId, String simulatedPaymentId, long amountWon,
		String cancelledAt, String ledgerTransactionId, String paymentStatus, long cancellableAmountWon,
		Impact impact) {

	/**
	 * 취소 영향.
	 *
	 * @param confirmed 확정 규칙으로 바뀐 값
	 * @param needsConfirmation {@code 확인 필요}가 된 값과 이유
	 */
	public record Impact(List<ImpactItem> confirmed, List<ImpactItem> needsConfirmation) {
	}

	/**
	 * 영향 한 줄. 대상마다 쓰는 필드가 달라 값이 없는 필드는 내보내지 않는다(계약 예시 모양).
	 *
	 * <ul>
	 * <li>확정 {@code PERFORMANCE}: {@code userCardId}·{@code month}·{@code deltaWon}·{@code basis}</li>
	 * <li>확인 필요 {@code PERFORMANCE}: {@code userCardId}·{@code month}·{@code reason}</li>
	 * <li>확인 필요 {@code BENEFIT}: {@code userCardId}·{@code serviceId}·{@code reason}</li>
	 * <li>확인 필요 {@code LIMIT_USAGE}: {@code userCardId}·{@code limitBucketId}·{@code reason}</li>
	 * </ul>
	 *
	 * @param target {@code PERFORMANCE}·{@code BENEFIT}·{@code LIMIT_USAGE}
	 * @param userCardId 보유 카드
	 * @param month 영향받는 달
	 * @param serviceId 영향받는 서비스
	 * @param limitBucketId 영향받는 한도 버킷
	 * @param deltaWon 확정 변화량
	 * @param basis 확정 근거(취소 귀속 규칙)
	 * @param reason 확인 필요 이유
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ImpactItem(String target, String userCardId, String month, String serviceId, String limitBucketId,
			Long deltaWon, String basis, String reason) {
	}

}
