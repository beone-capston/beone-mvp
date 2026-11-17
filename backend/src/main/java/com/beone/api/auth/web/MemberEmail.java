package com.beone.api.auth.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.beone.api.auth.application.EmailAddresses;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * 회원 이메일 형식 검사(docs/api/auth.md 「값 검증」). 앞뒤 공백을 지우고 소문자로 바꾼 값이 이메일 형식이고 254자 이하여야 한다.
 * {@code null}은 통과시키고 필수 여부는 {@code @NotNull}이 본다.
 */
@Documented
@Constraint(validatedBy = MemberEmail.Validator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
@interface MemberEmail {

	String message() default "이메일 형식이 아니거나 254자를 넘습니다.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	/** {@link EmailAddresses#isValid(String)}로 검사한다. */
	class Validator implements ConstraintValidator<MemberEmail, String> {

		@Override
		public boolean isValid(String value, ConstraintValidatorContext context) {
			return value == null || EmailAddresses.isValid(value);
		}

	}

}
