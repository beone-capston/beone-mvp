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
 * CODEF로 보내는 카드사 아이디 문자 규칙. 제어 문자(Cc), 서식 문자(Cf, 예: U+200B·U+FEFF), 짝 없는 서로게이트(Cs), 미할당(Cn), 줄·문단
 * 구분자(Zl·Zp), 대체 문자 U+FFFD가 있거나 앞뒤에 공백(U+00A0·U+3000 포함)이 있으면 위반이다. CODEF 호출 전에 400으로 거절해 잘못된
 * 아이디로 호출 횟수를 쓰지 않게 한다. null은 {@code @NotNull}이 다룬다. 문구는 고정이며 입력값을 넣지 않는다.
 */
@Documented
@Constraint(validatedBy = CodefLoginText.Validator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@interface CodefLoginText {

	String message() default CodefConnectionRequest.INVALID_CHARACTER;

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	/** 아이디 문자 규칙 판정. */
	class Validator implements ConstraintValidator<CodefLoginText, String> {

		@Override
		public boolean isValid(String value, ConstraintValidatorContext context) {
			if (value == null || value.isEmpty()) {
				return true;
			}
			if (isSpace(value.codePointAt(0)) || isSpace(value.codePointBefore(value.length()))) {
				return false;
			}
			return value.codePoints().noneMatch(Validator::isForbidden);
		}

		static boolean isSpace(int codePoint) {
			return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
		}

		static boolean isForbidden(int codePoint) {
			if (codePoint == 0xFFFD) {
				return true;
			}
			return switch (Character.getType(codePoint)) {
				case Character.CONTROL, Character.FORMAT, Character.SURROGATE, Character.UNASSIGNED,
						Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> true;
				default -> false;
			};
		}

	}

}
