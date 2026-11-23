package com.beone.api.order.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * 값이 {@code rules/merchant-classes.txt} 허용 목록에 있어야 한다(D-23). {@code null}은 이 제약이 아니라
 * {@code @NotNull}이 판정한다. 허용 밖이면 400 {@code INVALID_REQUEST}다(결정 27).
 */
@Documented
@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AllowedMerchantClassValidator.class)
public @interface AllowedMerchantClass {

	String message() default "허용된 가맹점 분류가 아닙니다.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
