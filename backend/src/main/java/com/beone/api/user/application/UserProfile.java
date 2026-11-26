package com.beone.api.user.application;

import java.util.Objects;

/**
 * 현재 사용자(장부)의 표시 정보. 시연 사용자와 회원 장부 모두 쓴다({@code GET /api/v1/me}).
 *
 * @param demoUser 시연 사용자 구분. 회원 장부면 {@code null}(결정 37 D7)
 * @param displayName 화면 표시 이름
 * @param portfolioType 포트폴리오 종류
 * @param readOnly 보유 카드·CODEF 쓰기 불가 여부(D-19)
 */
public record UserProfile(DemoUser demoUser, String displayName, PortfolioType portfolioType, boolean readOnly) {

	public UserProfile {
		Objects.requireNonNull(displayName, "displayName");
		Objects.requireNonNull(portfolioType, "portfolioType");
	}

}
