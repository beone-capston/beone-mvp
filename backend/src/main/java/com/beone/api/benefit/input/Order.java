package com.beone.api.benefit.input;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.Optional;

import com.beone.api.benefit.card.MerchantClass;
import com.beone.api.benefit.card.PaymentMethod;
import com.beone.api.benefit.money.Won;

/**
 * 결제하려는 편집 가능한 주문(PRD FR-04).
 * 날짜는 Asia/Seoul 기준이며 혜택 산정 기간과 월 경계를 정한다. 시각은 입력된 경우에만 보관한다.
 */
public record Order(Optional<String> orderId, Optional<String> merchantName, Won amount, LocalDate date,
		Optional<LocalTime> time, MerchantClass merchantClass, PaymentMethod paymentMethod) {

	public Order {
		InputChecks.requireOptionalText(orderId, "order id");
		InputChecks.requireOptionalText(merchantName, "merchant name");
		InputChecks.requirePositive(amount, "order amount");
		Objects.requireNonNull(date, "date");
		Objects.requireNonNull(time, "time");
		Objects.requireNonNull(merchantClass, "merchantClass");
		Objects.requireNonNull(paymentMethod, "paymentMethod");
	}

}
