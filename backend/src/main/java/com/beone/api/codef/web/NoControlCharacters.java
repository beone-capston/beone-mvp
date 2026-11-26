package com.beone.api.codef.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * 비밀번호({@code char[]})에 제어 문자(Cc, 예: 줄바꿈·탭·NUL)가 있으면 위반이다. 앞뒤 공백은 실제 비밀번호일 수 있어 허용한다.
 * 값을 문자열로 만들거나 길이·위치를 남기지 않고, 문구는 고정이다. 거절된 값의 문자열 표현은 배열 주소뿐이라 내용이 드러나지 않는다.
 */
@Documented
@Constraint(validatedBy = NoControlCharacters.Validator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@interface NoControlCharacters {

	String message() default CodefConnectionRequest.INVALID_CHARACTER;

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	/** 제어 문자 포함 여부만 본다. */
	class Validator implements ConstraintValidator<NoControlCharacters, char[]> {

		@Override
		public boolean isValid(char[] value, ConstraintValidatorContext context) {
			if (value == null) {
				return true;
			}
			for (char c : value) {
				if (Character.isISOControl(c)) {
					return false;
				}
			}
			return true;
		}

	}

}
