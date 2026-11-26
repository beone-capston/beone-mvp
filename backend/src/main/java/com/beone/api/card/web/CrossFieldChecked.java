package com.beone.api.card.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * 필드 사이 규칙을 요청 DTO가 스스로 판정하게 하는 클래스 수준 검증. 위반마다 필드 이름(요청 전체면 {@code null})과 고정 문구를
 * 돌려주면 공통 처리기가 {@code errors}로 옮긴다(docs/api.md 응답 계약).
 */
@Documented
@Constraint(validatedBy = CrossFieldChecked.Validator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@interface CrossFieldChecked {

	String message() default "요청 값을 확인해 주세요.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	/** 필드 사이 규칙 위반 하나. 문구에 입력 원문을 넣지 않는다. */
	record Violation(String field, String message) {
	}

	/** 스스로 필드 사이 규칙을 판정하는 요청. */
	interface Checkable {

		/**
		 * 위반 목록. 없으면 빈 목록. 필드 하나의 형식 오류는 그 필드의 제약이 다루므로 여기서 다시 보지 않는다.
		 * @return 교차 필드 위반 목록. 없으면 빈 목록
		 */
		List<Violation> crossFieldViolations();

	}

	/** {@link Checkable}의 위반을 제약 위반으로 옮긴다. */
	class Validator implements ConstraintValidator<CrossFieldChecked, Checkable> {

		@Override
		public boolean isValid(Checkable value, ConstraintValidatorContext context) {
			if (value == null) {
				return true;
			}
			List<Violation> violations = value.crossFieldViolations();
			if (violations.isEmpty()) {
				return true;
			}
			context.disableDefaultConstraintViolation();
			for (Violation violation : violations) {
				// 고정 문구만 쓰므로 메시지 보간을 거쳐도 입력값이 들어가지 않는다.
				var builder = context.buildConstraintViolationWithTemplate(violation.message());
				if (violation.field() == null) {
					builder.addConstraintViolation();
				}
				else {
					builder.addPropertyNode(violation.field()).addConstraintViolation();
				}
			}
			return false;
		}

	}

}
