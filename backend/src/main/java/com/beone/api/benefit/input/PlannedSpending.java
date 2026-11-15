package com.beone.api.benefit.input;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.Optional;

import com.beone.api.benefit.card.MerchantClass;
import com.beone.api.benefit.card.PaymentMethod;
import com.beone.api.benefit.money.Won;

/**
 * 사용자가 확인한 예정 소비(PRD FR-04). 날짜·시각 규칙은 {@link Order}와 같다.
 */
public record PlannedSpending(LocalDate date, Optional<LocalTime> time, Won amount, MerchantClass merchantClass,
		Optional<String> merchantName, PaymentMethod paymentMethod) {

	public PlannedSpending {
		Objects.requireNonNull(date, "date");
		Objects.requireNonNull(time, "time");
		InputChecks.requirePositive(amount, "planned spending amount");
		Objects.requireNonNull(merchantClass, "merchantClass");
		InputChecks.requireOptionalText(merchantName, "merchant name");
		Objects.requireNonNull(paymentMethod, "paymentMethod");
	}

}
