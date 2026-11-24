package com.beone.api.order.web;

import java.util.List;

import com.beone.api.order.application.OrderDetail;
import com.beone.api.order.application.OrderPage;
import com.beone.api.order.application.OrderStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * {@code GET /api/v1/me/orders}의 {@code data}(docs/api/orders.md 내 주문 목록 조회, 결정 38-5). 전체 건수는 주지 않는다.
 *
 * @param items 주문 요약 목록
 * @param page 적용한 페이지 번호
 * @param size 적용한 페이지 크기
 * @param hasNext 다음 페이지에 주문이 더 있으면 {@code true}
 */
public record OrderListResponse(List<Item> items, int page, int size, boolean hasNext) {

	static OrderListResponse from(OrderPage page) {
		return new OrderListResponse(page.items().stream().map(Item::from).toList(), page.page(), page.size(),
				page.hasNext());
	}

	/**
	 * 목록용 주문 요약. 필드는 상세 조회 응답에서 골랐고 뜻·형식이 같다. {@code orderTime}·{@code paymentMethod}·
	 * {@code latestRecommendationId}는 상세 조회에서만 준다. 값이 없는 필드도 {@code null}로 쓴다.
	 *
	 * @param orderId 주문 ID({@code ord-…})
	 * @param amountWon 결제 금액(원)
	 * @param orderDate 사용자가 입력한 주문일 {@code YYYY-MM-DD}
	 * @param merchantName 가맹점 이름 또는 {@code null}
	 * @param merchantClass 가맹점·업종 분류 코드
	 * @param status 주문 상태
	 * @param createdAt 서버가 저장한 시각({@code +09:00}). 목록 정렬 기준
	 * @param simulatedPaymentId 모의 승인 결제 ID 또는 {@code null}
	 */
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record Item(String orderId, long amountWon, String orderDate, String merchantName, String merchantClass,
			OrderStatus status, String createdAt, String simulatedPaymentId) {

		static Item from(OrderDetail order) {
			return new Item(order.orderId(), order.amountWon(), OrderValueFormats.date(order.orderDate()),
					order.merchantName(), order.merchantClass(), order.status(),
					OrderValueFormats.seoulInstant(order.createdAt()), order.simulatedPaymentId());
		}

	}

}
