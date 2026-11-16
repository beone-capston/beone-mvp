package com.beone.api.order.web;

import com.beone.api.order.application.OrderDetail;
import com.beone.api.order.application.OrderStatus;
import com.beone.api.order.application.PaymentMethod;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * {@code POST /api/v1/me/orders}의 {@code data}(docs/api/orders.md). 값이 없는 필드도 {@code null}로 쓴다.
 *
 * @param orderId 주문 ID({@code ord-…})
 * @param amountWon 결제 금액(원)
 * @param orderDate {@code YYYY-MM-DD}
 * @param orderTime {@code HH:mm:ss} 또는 {@code null}
 * @param merchantName 가맹점 이름 또는 {@code null}
 * @param merchantClass 가맹점·업종 분류 코드
 * @param paymentMethod 결제 방식
 * @param status 주문 상태. 생성 직후 {@code CREATED}
 * @param createdAt 생성 시각({@code +09:00})
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CreatedOrderResponse(String orderId, long amountWon, String orderDate, String orderTime,
		String merchantName, String merchantClass, PaymentMethod paymentMethod, OrderStatus status, String createdAt) {

	static CreatedOrderResponse from(OrderDetail order) {
		return new CreatedOrderResponse(order.orderId(), order.amountWon(), OrderValueFormats.date(order.orderDate()),
				OrderValueFormats.time(order.orderTime()), order.merchantName(), order.merchantClass(),
				order.paymentMethod(), order.status(), OrderValueFormats.seoulInstant(order.createdAt()));
	}

}
