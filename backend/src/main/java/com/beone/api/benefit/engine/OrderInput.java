package com.beone.api.benefit.engine;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

import com.beone.api.benefit.card.MerchantClass;
import com.beone.api.benefit.card.PaymentMethod;

/**
 * 계산할 주문.
 *
 * @param orderId 주문 ID. 없을 수 있다
 * @param merchantName 가맹점 이름. 없을 수 있다
 * @param amountWon 결제 금액. 1원 이상
 * @param date 주문일
 * @param time 주문 시각. 없을 수 있다
 * @param merchantClass 업종 코드. 허용 목록 검사는 업무 API의 입력 검증에서 한다
 * @param paymentMethod {@code DIRECT}·{@code ELIGIBLE_SIMPLE_PAY}·{@code AUTOPAY}
 */
public record OrderInput(String orderId, String merchantName, long amountWon, LocalDate date, LocalTime time,
		String merchantClass, String paymentMethod) {

	public OrderInput {
		EngineChecks.requireOptionalText(orderId, "orderId");
		EngineChecks.requireOptionalText(merchantName, "merchantName");
		if (amountWon <= 0) {
			throw new IllegalArgumentException("amountWon must be positive: " + amountWon);
		}
		Objects.requireNonNull(date, "date");
		MerchantClass.of(merchantClass);
		EngineChecks.requireEnumName(PaymentMethod.class, paymentMethod, "paymentMethod");
	}

}
