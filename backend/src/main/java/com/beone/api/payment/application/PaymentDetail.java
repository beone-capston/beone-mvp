package com.beone.api.payment.application;

import java.util.List;

/**
 * 모의 결제 상세 조회 본문(docs/api/mock-payments.md {@code GET simulated-payments/{id}}). 승인 때 정한 값은 {@link ApprovalDocument}
 * 그대로이고, 상태·남은 취소 가능 금액·취소 내역만 지금 값이다.
 *
 * @param simulatedPaymentId 결제 ID
 * @param orderId 주문 ID
 * @param status {@code APPROVED}·{@code FAILED}·{@code PARTIALLY_CANCELLED}·{@code CANCELLED}
 * @param approvedAt 승인 시각
 * @param amountWon 결제 금액
 * @param selectionMode 선택 방식
 * @param userCardId 승인 카드
 * @param cardProductId 규칙 상품 ID
 * @param conditional 조건부 추천이었는지
 * @param currentOrderBenefit 승인 시점 계산값
 * @param appliedRule 계산에 쓴 규칙
 * @param selectionBasis 선택 근거
 * @param mappingNote 사용자 확인 매핑 표시
 * @param recalculation 재계산 비교
 * @param ledgerTransactionId 원장 승인 행
 * @param cancellableAmountWon 남은 취소 가능 금액
 * @param cancellations 취소 내역(취소 순서)
 */
public record PaymentDetail(String simulatedPaymentId, String orderId, String status, String approvedAt,
		long amountWon, String selectionMode, String userCardId, String cardProductId, boolean conditional,
		ApprovalDocument.Amount currentOrderBenefit, ApprovalDocument.AppliedRule appliedRule,
		ApprovalDocument.SelectionBasis selectionBasis, String mappingNote,
		ApprovalDocument.Recalculation recalculation, String ledgerTransactionId, long cancellableAmountWon,
		List<CancellationLine> cancellations) {

	static PaymentDetail of(ApprovalDocument approval, PaymentStatus status, long cancellableAmountWon,
			List<CancellationLine> cancellations) {
		return new PaymentDetail(approval.simulatedPaymentId(), approval.orderId(), status.name(),
				approval.approvedAt(), approval.amountWon(), approval.selectionMode(), approval.userCardId(),
				approval.cardProductId(), approval.conditional(), approval.currentOrderBenefit(),
				approval.appliedRule(), approval.selectionBasis(), approval.mappingNote(), approval.recalculation(),
				approval.ledgerTransactionId(), cancellableAmountWon, List.copyOf(cancellations));
	}

	/**
	 * 취소 한 건.
	 *
	 * @param cancellationId 취소 ID
	 * @param amountWon 취소 금액(양수)
	 * @param cancelledAt 취소 시각
	 * @param ledgerTransactionId 원장 취소 행
	 */
	public record CancellationLine(String cancellationId, long amountWon, String cancelledAt,
			String ledgerTransactionId) {
	}

}
