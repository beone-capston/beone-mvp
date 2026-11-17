package com.beone.api.auth.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.regex.Pattern;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * 새 비밀번호 규칙(docs/api/auth.md 「값 검증」). 11~32자이고 영문·숫자·ASCII 특수기호를 각각 1개 이상 포함한다. 공백·한글 등 그
 * 밖의 문자는 받지 않는다. 대·소문자 동시 사용은 강제하지 않고, 공백 제거·대소문자 변환을 하지 않는다. {@code null}은 통과시키고
 * 필수 여부는 {@code @NotNull}이 본다. 오류 응답에 입력값을 담지 않는다.
 */
@Documented
@Constraint(validatedBy = NewPassword.Validator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
@interface NewPassword {

	String message() default "비밀번호는 11~32자이며 영문, 숫자, 특수기호를 각각 1개 이상 포함해야 합니다.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	/** 길이와 문자 종류를 검사한다. */
	class Validator implements ConstraintValidator<NewPassword, String> {

		static final int MIN_LENGTH = 11;

		static final int MAX_LENGTH = 32;

		/** 공백(0x20)을 뺀 출력 가능한 ASCII만. */
		private static final Pattern ALLOWED = Pattern.compile("[!-~]+");

		private static final Pattern LETTER = Pattern.compile("[A-Za-z]");

		private static final Pattern DIGIT = Pattern.compile("[0-9]");

		private static final Pattern SYMBOL = Pattern.compile("[!-/:-@\\[-`{-~]");

		@Override
		public boolean isValid(String value, ConstraintValidatorContext context) {
			if (value == null) {
				return true;
			}
			return value.length() >= MIN_LENGTH && value.length() <= MAX_LENGTH && ALLOWED.matcher(value).matches()
					&& LETTER.matcher(value).find() && DIGIT.matcher(value).find() && SYMBOL.matcher(value).find();
		}

	}

}
