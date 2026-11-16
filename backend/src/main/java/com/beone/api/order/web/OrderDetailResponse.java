package com.beone.api.order.web;

import com.beone.api.order.application.OrderDetail;
import com.beone.api.order.application.OrderStatus;
import com.beone.api.order.application.PaymentMethod;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * {@code GET /api/v1/me/orders/{orderId}}의 {@code data}(docs/api/orders.md). 값이 없는 필드도 {@code null}로 쓴다.
 *
 * <p>{@code simulatedPaymentId}·{@code latestRecommendationId}는 모의 승인·추천이 구현되기 전이라 항상 {@code null}이다.
 * 계약상 "모의 승인 전"·"계산한 추천 없음"의 값이며, 해당 기능이 생기면 그 작업이 채운다.
 *
 * @param orderId 주문 ID({@code ord-…})
 * @param amountWon 결제 금액(원)
 * @param orderDate {@code YYYY-MM-DD}
 * @param orderTime {@code HH:mm:ss} 또는 {@code null}
 * @param merchantName 가맹점 이름 또는 {@code null}
 * @param merchantClass 가맹점·업종 분류 코드
 * @param paymentMethod 결제 방식
 * @param status 주문 상태
 * @param createdAt 생성 시각({@code +09:00})
 * @param simulatedPaymentId 모의 승인 결제 ID 또는 {@code null}
 * @param latestRecommendationId 이 주문으로 마지막에 계산한 추천 ID 또는 {@code null}
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record OrderDetailResponse(String orderId, long amountWon, String orderDate, String orderTime,
		String merchantName, String merchantClass, PaymentMethod paymentMethod, OrderStatus status, String createdAt,
		String simulatedPaymentId, String latestRecommendationId) {

	static OrderDetailResponse from(OrderDetail order) {
		return new OrderDetailResponse(order.orderId(), order.amountWon(), OrderValueFormats.date(order.orderDate()),
				OrderValueFormats.time(order.orderTime()), order.merchantName(), order.merchantClass(),
				order.paymentMethod(), order.status(), OrderValueFormats.seoulInstant(order.createdAt()), null, null);
	}

}
