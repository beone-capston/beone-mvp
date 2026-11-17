package com.beone.api.payment.application;

import java.util.List;

/**
 * 모의 승인 응답 본문(docs/api/mock-payments.md {@code POST simulated-payments}). 승인할 때 한 번 만들어 저장하고, 같은 키·같은 지문의
 * 재요청에는 이 값을 그대로 돌려준다.
 *
 * <p>금액 {@code null}·{@code NEEDS_CONFIRMATION}을 0으로 바꾸지 않는다(D-19, D-22). 시점은 {@code +09:00}이 붙은 서울 시각 문자열이다.
 *
 * @param simulatedPaymentId 결제 ID
 * @param orderId 주문 ID
 * @param status {@code APPROVED}·{@code FAILED}
 * @param approvedAt 승인 시각. {@code FAILED}면 실패를 기록한 시각
 * @param amountWon 결제 금액(주문 금액)
 * @param selectionMode 결제 전 설정의 선택 방식
 * @param userCardId 승인 카드
 * @param cardProductId 승인 카드의 규칙 상품 ID
 * @param conditional 조건부 추천이었는지(D-18 K2)
 * @param currentOrderBenefit 승인 시점 계산값. 미확정·실패면 {@code amountWon=null}
 * @param appliedRule 계산에 쓴 규칙. 판정일에 규칙이 없으면 {@code null}
 * @param selectionBasis D-12 선택 근거. 대표 카드 고정이면 설정으로 정한 카드라 {@code null}
 * @param mappingNote 사용자 확인 매핑 표시. 가상 포트폴리오 카드는 {@code null}
 * @param recalculation 승인 직전 재계산과 사용자가 본 추천의 비교
 * @param ledgerTransactionId 원장 승인 행. {@code FAILED}면 {@code null}
 * @param cancellableAmountWon 남은 취소 가능 금액. {@code FAILED}면 0
 */
public record ApprovalDocument(String simulatedPaymentId, String orderId, String status, String approvedAt,
		long amountWon, String selectionMode, String userCardId, String cardProductId, boolean conditional,
		Amount currentOrderBenefit, AppliedRule appliedRule, SelectionBasis selectionBasis, String mappingNote,
		Recalculation recalculation, String ledgerTransactionId, long cancellableAmountWon) {

	/**
	 * 금액과 상태. 미확정이면 {@code amountWon}이 {@code null}이다.
	 *
	 * @param amountWon 원 단위 금액
	 * @param status {@code CONFIRMED}·{@code NEEDS_CONFIRMATION}
	 */
	public record Amount(Long amountWon, String status) {

		static final String CONFIRMED = "CONFIRMED";

		static final String NEEDS_CONFIRMATION = "NEEDS_CONFIRMATION";

		static Amount needsConfirmation() {
			return new Amount(null, NEEDS_CONFIRMATION);
		}

		boolean isConfirmedZero() {
			return CONFIRMED.equals(this.status) && Long.valueOf(0).equals(this.amountWon);
		}

	}

	/**
	 * 규칙 버전·검증 상태·공식 근거.
	 *
	 * @param ruleVersion 규칙 버전
	 * @param verificationStatus 검증 상태
	 * @param officialSources 공식 근거
	 */
	public record AppliedRule(String ruleVersion, String verificationStatus, List<OfficialSource> officialSources) {
	}

	/**
	 * 공식 근거 하나.
	 *
	 * @param uri 문서 주소
	 * @param locator 문서 안 위치
	 */
	public record OfficialSource(String uri, String locator) {
	}

	/**
	 * D-12 선택 근거.
	 *
	 * @param decidedBy 결정 기준. 비교 카드가 1장이면 {@code null}
	 * @param arbitraryTieBreak 임의 규칙으로 정했으면 참
	 * @param tiedUserCardIds 동률 카드
	 */
	public record SelectionBasis(String decidedBy, boolean arbitraryTieBreak, List<String> tiedUserCardIds) {
	}

	/**
	 * 재계산 비교.
	 *
	 * @param recommendationId 승인 직전 재계산으로 저장한 추천 ID
	 * @param seenRecommendationId 사용자가 본 추천 ID. 보내지 않았으면 {@code null}
	 * @param changed 선택 카드·혜택이 달라졌는지. 비교하지 않았으면 {@code null}
	 * @param differences 달라진 값
	 */
	public record Recalculation(String recommendationId, String seenRecommendationId, Boolean changed,
			List<Difference> differences) {
	}

	/**
	 * 달라진 값 하나.
	 *
	 * @param field 값 위치(예: {@code selected.userCardId})
	 * @param before 사용자가 본 추천의 값
	 * @param after 재계산 값
	 * @param reason 본 추천이 무효가 된 사유. 무효가 아니면 {@code null}
	 */
	public record Difference(String field, Object before, Object after, String reason) {
	}

}
