package com.beone.api.user.application;

import java.util.Objects;

/**
 * 현재 요청의 사용자. {@code CurrentUserResolver} 한 곳에서만 만들고 컨트롤러는 인자로 받아 쓴다(D-09).
 *
 * <p>사용자 데이터는 {@code userId}로 나눠 저장·조회한다.
 *
 * @param userId 저장소의 사용자 ID
 * @param demoUser 시연 사용자 구분
 */
public record CurrentUser(long userId, DemoUser demoUser) {

	public CurrentUser {
		Objects.requireNonNull(demoUser, "demoUser");
	}

}
