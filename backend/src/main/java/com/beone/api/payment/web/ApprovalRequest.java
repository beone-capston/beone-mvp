package com.beone.api.payment.web;

import com.beone.api.payment.application.ApprovalCommand;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * {@code POST /api/v1/me/orders/{orderId}/simulated-payments} 요청 본문(docs/api/mock-payments.md). 필수 필드가 없고 본문을 생략해도
 * 된다. 카드 ID는 받지 않는다.
 *
 * @param seenRecommendationId 선택. 사용자가 본 추천 ID(JSON 문자열)
 * @param simulateFailure 선택. JSON 불리언. {@code true}면 내부 테스트 실패(D-23-4)
 */
public record ApprovalRequest(@JsonDeserialize(using = StrictTextDeserializer.class) String seenRecommendationId,
		@JsonDeserialize(using = StrictBooleanDeserializer.class) Boolean simulateFailure) {

	static final ApprovalRequest EMPTY = new ApprovalRequest(null, null);

	ApprovalCommand toCommand() {
		return new ApprovalCommand(this.seenRecommendationId, Boolean.TRUE.equals(this.simulateFailure));
	}

}
