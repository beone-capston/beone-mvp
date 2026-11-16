package com.beone.api.order.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 저장된 주문 한 건.
 *
 * @param orderId 외부 주문 ID({@code ord-…})
 * @param amountWon 결제 금액(원)
 * @param orderDate 주문일
 * @param orderTime 주문 시각. 없으면 {@code null}
 * @param merchantName 가맹점 이름. 없으면 {@code null}
 * @param merchantClass 가맹점·업종 분류 코드
 * @param paymentMethod 결제 방식
 * @param status 주문 상태
 * @param createdAt 생성 시각(Asia/Seoul)
 */
public record OrderDetail(String orderId, long amountWon, LocalDate orderDate, LocalTime orderTime,
		String merchantName, String merchantClass, PaymentMethod paymentMethod, OrderStatus status,
		LocalDateTime createdAt) {

}
