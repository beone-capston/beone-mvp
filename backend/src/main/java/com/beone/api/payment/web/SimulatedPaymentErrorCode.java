package com.beone.api.payment.web;

import com.beone.api.common.web.ApiErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 모의 결제 API가 쓰는 업무 오류 코드(docs/api.md 업무 오류 코드 표의 주문·추천·결제 전 설정·모의 결제 행). 표에 있는 코드만 쓴다.
 */
enum SimulatedPaymentErrorCode implements ApiErrorCode {

	ORDER_NOT_FOUND("ORDER_NOT_FOUND", HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),

	ORDER_ALREADY_PAID("ORDER_ALREADY_PAID", HttpStatus.CONFLICT, "이미 모의 승인된 주문입니다."),

	RECOMMENDATION_NOT_FOUND("RECOMMENDATION_NOT_FOUND", HttpStatus.NOT_FOUND, "추천 결과를 찾을 수 없습니다."),

	RECOMMENDATION_NOT_DETERMINED("RECOMMENDATION_NOT_DETERMINED", HttpStatus.CONFLICT,
			"추천을 확정할 수 없어 자동 추천으로 승인할 수 없습니다."),

	REPRESENTATIVE_CARD_NOT_ELIGIBLE("REPRESENTATIVE_CARD_NOT_ELIGIBLE", HttpStatus.CONFLICT,
			"대표 카드를 승인에 쓸 수 없습니다."),

	SIMULATED_PAYMENT_NOT_FOUND("SIMULATED_PAYMENT_NOT_FOUND", HttpStatus.NOT_FOUND, "모의 결제를 찾을 수 없습니다."),

	CANCELLATION_AMOUNT_EXCEEDED("CANCELLATION_AMOUNT_EXCEEDED", HttpStatus.CONFLICT, "취소 금액이 남은 취소 가능 금액보다 큽니다."),

	PAYMENT_ALREADY_CANCELLED("PAYMENT_ALREADY_CANCELLED", HttpStatus.CONFLICT, "이미 전액 취소된 결제입니다."),

	IDEMPOTENCY_KEY_REUSED("IDEMPOTENCY_KEY_REUSED", HttpStatus.CONFLICT, "이 Idempotency-Key는 다른 요청에 이미 사용했습니다.");

	private final String code;

	private final HttpStatus status;

	private final String message;

	SimulatedPaymentErrorCode(String code, HttpStatus status, String message) {
		this.code = code;
		this.status = status;
		this.message = message;
	}

	@Override
	public String code() {
		return this.code;
	}

	@Override
	public HttpStatus status() {
		return this.status;
	}

	@Override
	public String message() {
		return this.message;
	}

}
