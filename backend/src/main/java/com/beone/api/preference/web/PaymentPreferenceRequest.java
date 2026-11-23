package com.beone.api.preference.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.beone.api.preference.application.PaymentPreferenceChange;
import com.beone.api.preference.application.PaymentSelectionMode;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * {@code PUT /api/v1/me/payment-preference} 요청 본문(docs/api/preferences.md).
 *
 * <p>{@code representativeUserCardId}는 값이 {@code null}이어도 반드시 보낸다. 필드 누락과 {@code null}을 구분해야 하므로 레코드
 * 대신 setter로 받아 보냈는지를 기록한다(D-23). 모드는 문자열로 받아 허용 목록과 비교한다. 열거형으로 받으면 숫자 순번도 받기 때문이다.
 */
@PaymentPreferenceRequest.Combination
public class PaymentPreferenceRequest {

	static final String REQUIRED = "필수 값입니다.";

	static final String FIXED_REQUIRES_CARD = "대표 카드 고정 모드에는 대표 카드가 필요합니다.";

	private static final String MODES = "AUTO_RECOMMEND|FIXED_REPRESENTATIVE";

	@NotNull(message = REQUIRED)
	@Pattern(regexp = MODES, message = "허용되지 않는 값입니다.")
	private String selectionMode;

	private String representativeUserCardId;

	private boolean representativeUserCardIdPresent;

	@JsonProperty("selectionMode")
	@JsonDeserialize(using = StrictTextDeserializer.class)
	void setSelectionMode(String selectionMode) {
		this.selectionMode = selectionMode;
	}

	@JsonProperty("representativeUserCardId")
	@JsonDeserialize(using = StrictTextDeserializer.class)
	void setRepresentativeUserCardId(String representativeUserCardId) {
		this.representativeUserCardId = representativeUserCardId;
		this.representativeUserCardIdPresent = true;
	}

	PaymentPreferenceChange toChange() {
		return new PaymentPreferenceChange(PaymentSelectionMode.valueOf(this.selectionMode),
				this.representativeUserCardId);
	}

	/** 모드와 대표 카드의 조합 규칙. 위반은 {@code representativeUserCardId} 필드 오류다. */
	@Documented
	@Constraint(validatedBy = CombinationValidator.class)
	@Target(ElementType.TYPE)
	@Retention(RetentionPolicy.RUNTIME)
	@interface Combination {

		String message() default "요청 값을 확인해 주세요.";

		Class<?>[] groups() default {};

		Class<? extends Payload>[] payload() default {};

	}

	/**
	 * 대표 카드 필드 누락은 모드와 무관하게 위반이다. 고정 모드인데 대표 카드가 {@code null}이어도 위반이다. 모드 자체의 누락·허용 밖
	 * 값은 필드 제약이 따로 알린다.
	 */
	static class CombinationValidator implements ConstraintValidator<Combination, PaymentPreferenceRequest> {

		@Override
		public boolean isValid(PaymentPreferenceRequest request, ConstraintValidatorContext context) {
			if (request == null) {
				return true;
			}
			String message = null;
			if (!request.representativeUserCardIdPresent) {
				message = REQUIRED;
			}
			else if (PaymentSelectionMode.FIXED_REPRESENTATIVE.name().equals(request.selectionMode)
					&& request.representativeUserCardId == null) {
				message = FIXED_REQUIRES_CARD;
			}
			if (message == null) {
				return true;
			}
			context.disableDefaultConstraintViolation();
			// 고정 문구만 쓰므로 메시지 보간을 거쳐도 입력값이 들어가지 않는다.
			context.buildConstraintViolationWithTemplate(message)
				.addPropertyNode("representativeUserCardId")
				.addConstraintViolation();
			return false;
		}

	}

}
