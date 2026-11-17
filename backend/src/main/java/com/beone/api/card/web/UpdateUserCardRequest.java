package com.beone.api.card.web;

import java.util.ArrayList;
import java.util.List;

import com.beone.api.card.application.UserCardChange;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * {@code PATCH /api/v1/me/cards/{userCardId}} 요청 본문(docs/api/cards.md). 보낸 필드만 바꾸며 매핑은 바꾸지 않는다.
 *
 * <p>값이 {@code null}인 필드는 보내지 않은 것으로 본다. 둘 다 없으면(빈 객체) 400이다.
 *
 * @param displayName 선택. 앞뒤 공백을 뗀 뒤 1~30자
 * @param excludedFromRecommendation 선택. 사용자가 추천에서 빼면 {@code true}
 */
@CrossFieldChecked
public record UpdateUserCardRequest(@CardDisplayName String displayName,
		@JsonDeserialize(using = StrictBooleanDeserializer.class) Boolean excludedFromRecommendation)
		implements UnknownFieldRejecting, CrossFieldChecked.Checkable {

	@Override
	public List<CrossFieldChecked.Violation> crossFieldViolations() {
		List<CrossFieldChecked.Violation> violations = new ArrayList<>();
		if (this.displayName == null && this.excludedFromRecommendation == null) {
			violations.add(new CrossFieldChecked.Violation(null, "변경할 값이 없습니다."));
		}
		return violations;
	}

	UserCardChange toUserCardChange() {
		return new UserCardChange(CardDisplayName.Validator.normalize(this.displayName), this.excludedFromRecommendation);
	}

}
