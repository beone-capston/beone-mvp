package com.beone.api.card.web;

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
 * 보유 카드 표시 이름: 앞뒤 공백을 뗀 뒤 1~30자(유니코드 문자 기준)다(docs/api/cards.md, 결정 27 A2-9). {@code null}은 이 검사에서
 * 통과하므로 필수 여부는 {@code @NotNull}로 따로 정한다.
 */
@Documented
@Constraint(validatedBy = CardDisplayName.Validator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
@interface CardDisplayName {

	/** 앞뒤 공백을 뗀 뒤 최대 글자 수. */
	int MAX_LENGTH = 30;

	/** 위반 메시지. 입력 원문을 넣지 않는다. */
	String message() default "앞뒤 공백을 빼고 1~30자여야 합니다.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	/** 표시 이름 길이 검사. */
	class Validator implements ConstraintValidator<CardDisplayName, String> {

		@Override
		public boolean isValid(String value, ConstraintValidatorContext context) {
			if (value == null) {
				return true;
			}
			String stripped = value.strip();
			int length = stripped.codePointCount(0, stripped.length());
			return length >= 1 && length <= MAX_LENGTH;
		}

		/** 앞뒤 공백을 뗀다. 값이 없으면 {@code null}. */
		static String normalize(String value) {
			return (value == null) ? null : value.strip();
		}

	}

}
