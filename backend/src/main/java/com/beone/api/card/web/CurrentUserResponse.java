package com.beone.api.card.web;

import com.beone.api.card.application.CurrentUserSummary;

/**
 * {@code GET /api/v1/me} 응답 {@code data}(docs/api/cards.md).
 *
 * @param demoUser 시연 사용자 구분
 * @param displayName 화면 표시 이름
 * @param portfolioType 포트폴리오 종류
 * @param readOnly 보유 카드 쓰기 불가 여부
 * @param cardCount 해제하지 않은 보유 카드 수
 * @param codefConnected CODEF 연결 행이 있는지({@code RECONNECT_REQUIRED} 포함, #136)
 */
record CurrentUserResponse(String demoUser, String displayName, String portfolioType, boolean readOnly,
		long cardCount, boolean codefConnected) {

	static CurrentUserResponse from(CurrentUserSummary summary) {
		return new CurrentUserResponse(summary.profile().demoUser().name(), summary.profile().displayName(),
				summary.profile().portfolioType().name(), summary.profile().readOnly(), summary.cardCount(),
				summary.codefConnected());
	}

}
