package com.beone.api.card.web;

import java.util.ArrayList;
import java.util.List;

import com.beone.api.card.application.ConfirmationMethod;
import com.beone.api.card.application.MappingChange;
import com.beone.api.card.application.MappingStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * {@code PATCH /api/v1/me/cards/{userCardId}/mapping} 요청 본문(docs/api/cards.md, 결정 27 A2-7).
 *
 * <p>열거 값은 문자열로 받아 허용 목록과 비교한다. Jackson 기본 열거형 변환은 숫자 순번도 받으므로 쓰지 않는다. {@code CANDIDATE}는
 * 서버만 만들므로 허용 목록에 없다.
 *
 * @param status 필수. {@code USER_CONFIRMED}·{@code NOT_SUPPORTED}·{@code UNMAPPED}
 * @param cardProductId {@code USER_CONFIRMED}면 필수, 그 밖에는 보내지 않는다
 * @param confirmationMethod {@code USER_CONFIRMED}면 필수. {@code NOT_SUPPORTED}는 생략하거나 {@code MANUAL_SELECTION},
 * {@code UNMAPPED}는 보내지 않는다
 */
@CrossFieldChecked
public record ChangeMappingRequest(
		@NotNull(message = "필수 값입니다.")
		@Pattern(regexp = "USER_CONFIRMED|NOT_SUPPORTED|UNMAPPED", message = "허용되지 않는 값입니다.") String status,
		String cardProductId,
		@Pattern(regexp = "MANUAL_SELECTION|CODEF_CANDIDATE",
				message = "허용되지 않는 값입니다.") String confirmationMethod)
		implements UnknownFieldRejecting, CrossFieldChecked.Checkable {

	private static final String REQUIRED = "필수 값입니다.";

	private static final String NOT_ALLOWED = "이 상태에서는 보낼 수 없습니다.";

	@Override
	public List<CrossFieldChecked.Violation> crossFieldViolations() {
		List<CrossFieldChecked.Violation> violations = new ArrayList<>();
		MappingStatus parsed = parsedStatus();
		if (parsed == null) {
			return violations;
		}
		switch (parsed) {
			case USER_CONFIRMED -> {
				if (this.cardProductId == null) {
					violations.add(new CrossFieldChecked.Violation("cardProductId", REQUIRED));
				}
				if (this.confirmationMethod == null) {
					violations.add(new CrossFieldChecked.Violation("confirmationMethod", REQUIRED));
				}
			}
			case NOT_SUPPORTED -> {
				if (this.cardProductId != null) {
					violations.add(new CrossFieldChecked.Violation("cardProductId", NOT_ALLOWED));
				}
				if (ConfirmationMethod.CODEF_CANDIDATE.name().equals(this.confirmationMethod)) {
					violations.add(new CrossFieldChecked.Violation("confirmationMethod", NOT_ALLOWED));
				}
			}
			case UNMAPPED -> {
				if (this.cardProductId != null) {
					violations.add(new CrossFieldChecked.Violation("cardProductId", NOT_ALLOWED));
				}
				if (this.confirmationMethod != null) {
					violations.add(new CrossFieldChecked.Violation("confirmationMethod", NOT_ALLOWED));
				}
			}
			case CANDIDATE -> throw new IllegalStateException("CANDIDATE is not an allowed request status");
		}
		return violations;
	}

	MappingChange toMappingChange() {
		MappingStatus parsed = parsedStatus();
		ConfirmationMethod method = switch (parsed) {
			case USER_CONFIRMED -> ConfirmationMethod.valueOf(this.confirmationMethod);
			case NOT_SUPPORTED -> ConfirmationMethod.MANUAL_SELECTION;
			case UNMAPPED, CANDIDATE -> null;
		};
		return new MappingChange(parsed, this.cardProductId, method);
	}

	// 허용 목록에 있는 값만 열거형으로 바꾼다. 형식 오류는 필드 제약이 따로 알린다.
	private MappingStatus parsedStatus() {
		if (this.status == null || !this.status.matches("USER_CONFIRMED|NOT_SUPPORTED|UNMAPPED")) {
			return null;
		}
		return MappingStatus.valueOf(this.status);
	}

}
