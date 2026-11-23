package com.beone.api.payment.application;

/**
 * 모의 결제 상태(docs/api/mock-payments.md). 이름이 곧 응답 값이다.
 *
 * <p>{@code FAILED}는 서비스 내부 테스트 조건의 실패이고 카드사 거절이 아니다(PRD §4, D-23-4). 취소는 승인에서만 진행한다.
 */
public enum PaymentStatus {

	/** 모의 승인됨. 취소 없음. */
	APPROVED,

	/** 내부 테스트 실패. 원장 행·주문 상태 변경·취소 가능 금액이 없다. */
	FAILED,

	/** 일부 취소됨. 남은 금액을 더 취소할 수 있다. */
	PARTIALLY_CANCELLED,

	/** 전액 취소됨. 더 취소할 수 없다. */
	CANCELLED

}
