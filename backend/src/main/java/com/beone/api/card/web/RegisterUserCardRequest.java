package com.beone.api.card.web;

import java.util.ArrayList;
import java.util.List;

import com.beone.api.card.application.NewUserCard;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * {@code POST /api/v1/me/cards} 요청 본문(docs/api/cards.md). 카드번호·끝 4자리·CVV·유효기간·카드 비밀번호 필드는 없고, 보내면
 * 알 수 없는 필드로 400이다.
 *
 * @param displayName 필수. 앞뒤 공백을 뗀 뒤 1~30자
 * @param cardProductId 선택. 보내면 {@code USER_CONFIRMED}({@code MANUAL_SELECTION})
 * @param notSupported 선택. {@code true}면 {@code NOT_SUPPORTED}. {@code cardProductId}와 함께 보낼 수 없다
 */
@CrossFieldChecked
public record RegisterUserCardRequest(@NotNull(message = "필수 값입니다.") @CardDisplayName String displayName,
		String cardProductId, @JsonDeserialize(using = StrictBooleanDeserializer.class) Boolean notSupported)
		implements UnknownFieldRejecting, CrossFieldChecked.Checkable {

	@Override
	public List<CrossFieldChecked.Violation> crossFieldViolations() {
		List<CrossFieldChecked.Violation> violations = new ArrayList<>();
		if (this.cardProductId != null && Boolean.TRUE.equals(this.notSupported)) {
			violations.add(new CrossFieldChecked.Violation(null, "cardProductId와 notSupported=true를 함께 보낼 수 없습니다."));
		}
		return violations;
	}

	NewUserCard toNewUserCard() {
		return new NewUserCard(CardDisplayName.Validator.normalize(this.displayName), this.cardProductId,
				Boolean.TRUE.equals(this.notSupported));
	}

}
