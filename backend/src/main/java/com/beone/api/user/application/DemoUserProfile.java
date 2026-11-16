package com.beone.api.user.application;

import java.util.Objects;

/**
 * 시연 사용자 한 명의 표시 정보.
 *
 * @param demoUser 시연 사용자 구분
 * @param displayName 화면 표시 이름
 * @param portfolioType 포트폴리오 종류
 * @param readOnly 보유 카드·CODEF 쓰기 불가 여부(D-19)
 */
public record DemoUserProfile(DemoUser demoUser, String displayName, PortfolioType portfolioType, boolean readOnly) {

	public DemoUserProfile {
		Objects.requireNonNull(demoUser, "demoUser");
		Objects.requireNonNull(displayName, "displayName");
		Objects.requireNonNull(portfolioType, "portfolioType");
	}

}
