package com.beone.api.order.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * 날짜가 서울 기준 이번 달이어야 한다(docs/api/orders.md, D-23 14항, 결정 27). {@code null}은 {@code @NotNull}이 판정한다.
 */
@Documented
@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = InCurrentMonthValidator.class)
public @interface InCurrentMonth {

	String message() default "이번 달 날짜여야 합니다.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
