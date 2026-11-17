package com.beone.api.payment.application;

/**
 * 모의 승인 요청 본문의 값(docs/api/mock-payments.md). 카드 ID는 받지 않는다. 서버가 결제 전 설정으로 정한다.
 *
 * @param seenRecommendationId 사용자가 본 추천 ID. 없으면 {@code null}이고 비교하지 않는다
 * @param simulateFailure 내부 테스트 실패를 일으킬지. 생략은 {@code false}와 같다
 */
public record ApprovalCommand(String seenRecommendationId, boolean simulateFailure) {

}
