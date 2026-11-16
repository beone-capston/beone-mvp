package com.beone.api.order.web;

import java.time.LocalDate;
import java.time.YearMonth;

import com.beone.api.order.application.OrderClock;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link InCurrentMonth} 판정. Spring이 만들 때 {@link OrderClock}을 주입한다.
 */
public class InCurrentMonthValidator implements ConstraintValidator<InCurrentMonth, LocalDate> {

	private final OrderClock orderClock;

	public InCurrentMonthValidator(OrderClock orderClock) {
		this.orderClock = orderClock;
	}

	@Override
	public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
		return value == null || YearMonth.from(value).equals(this.orderClock.currentMonth());
	}

}
