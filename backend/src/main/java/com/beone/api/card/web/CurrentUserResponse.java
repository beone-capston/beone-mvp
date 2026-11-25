package com.beone.api.card.web;

import com.beone.api.card.application.CurrentUserSummary;
import com.beone.api.user.application.DemoUser;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * {@code GET /api/v1/me} 응답 {@code data}(docs/api/cards.md).
 *
 * @param demoUser 시연 사용자 구분. 회원 장부면 {@code null}(결정 37 D7)
 * @param displayName 화면 표시 이름
 * @param portfolioType 포트폴리오 종류
 * @param readOnly 보유 카드 쓰기 불가 여부
 * @param cardCount 해제하지 않은 보유 카드 수
 * @param codefConnected CODEF 연결 행이 있는지({@code RECONNECT_REQUIRED} 포함, #136)
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
record CurrentUserResponse(String demoUser, String displayName, String portfolioType, boolean readOnly,
		long cardCount, boolean codefConnected) {

	static CurrentUserResponse from(CurrentUserSummary summary) {
		DemoUser demoUser = summary.profile().demoUser();
		return new CurrentUserResponse((demoUser != null) ? demoUser.name() : null, summary.profile().displayName(),
				summary.profile().portfolioType().name(), summary.profile().readOnly(), summary.cardCount(),
				summary.codefConnected());
	}

}
