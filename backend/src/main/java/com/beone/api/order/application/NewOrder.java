package com.beone.api.order.application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * 검증을 마친 주문 생성 입력.
 *
 * @param amountWon 결제 금액(원, 1 이상)
 * @param orderDate 주문일(Asia/Seoul)
 * @param orderTime 주문 시각. 보내지 않았으면 {@code null}이며 00:00으로 채우지 않는다
 * @param merchantName 화면 표시용 가맹점 이름. 없으면 {@code null}
 * @param merchantClass 허용 목록의 가맹점·업종 분류 코드
 * @param paymentMethod 결제 방식
 */
public record NewOrder(long amountWon, LocalDate orderDate, LocalTime orderTime, String merchantName,
		String merchantClass, PaymentMethod paymentMethod) {

	public NewOrder {
		if (amountWon < 1) {
			throw new IllegalArgumentException("amountWon must be positive");
		}
		Objects.requireNonNull(orderDate, "orderDate");
		Objects.requireNonNull(merchantClass, "merchantClass");
		Objects.requireNonNull(paymentMethod, "paymentMethod");
	}

}
