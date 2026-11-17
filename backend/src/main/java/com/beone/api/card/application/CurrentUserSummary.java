package com.beone.api.card.application;

import java.util.Objects;

import com.beone.api.user.application.DemoUserProfile;

/**
 * {@code GET /api/v1/me} 결과. 시연 사용자 표시 정보에 보유 카드 수를 더한다(docs/api/cards.md).
 *
 * @param profile 현재 시연 사용자 표시 정보
 * @param cardCount 해제하지 않은 보유 카드 수
 * @param codefConnected 2차 CODEF 연결 여부. 1차에서는 항상 {@code false}(계약)
 */
public record CurrentUserSummary(DemoUserProfile profile, long cardCount, boolean codefConnected) {

	public CurrentUserSummary {
		Objects.requireNonNull(profile, "profile");
	}

}
