package com.beone.api.card.application;

import java.util.Objects;

import com.beone.api.user.application.UserProfile;

/**
 * {@code GET /api/v1/me} 결과. 현재 사용자(시연 사용자 또는 회원 장부) 표시 정보에 보유 카드 수를 더한다(docs/api/cards.md).
 *
 * @param profile 현재 사용자 표시 정보
 * @param cardCount 해제하지 않은 보유 카드 수
 * @param codefConnected CODEF 연결 행이 있는지({@code RECONNECT_REQUIRED} 포함, #136)
 */
public record CurrentUserSummary(UserProfile profile, long cardCount, boolean codefConnected) {

	public CurrentUserSummary {
		Objects.requireNonNull(profile, "profile");
	}

}
