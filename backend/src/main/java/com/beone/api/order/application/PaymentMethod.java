package com.beone.api.order.application;

/**
 * 주문·예정 소비의 결제 방식(docs/api/orders.md). 이름이 곧 요청·응답 {@code paymentMethod} 값이다.
 *
 * <p>엔진의 {@code benefit.card.PaymentMethod}와 값은 같지만 업무 API 입력 모델로 따로 둔다. 추천 작업이 엔진 입력으로 옮긴다.
 */
public enum PaymentMethod {

	/** 카드 직접 결제. */
	DIRECT,

	/** 혜택 대상으로 확인된 간편결제. */
	ELIGIBLE_SIMPLE_PAY,

	/** 자동납부. */
	AUTOPAY

}
