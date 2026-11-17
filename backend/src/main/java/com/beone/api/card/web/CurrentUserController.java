package com.beone.api.card.web;

import com.beone.api.card.application.UserCardService;
import com.beone.api.common.web.ApiResponse;
import com.beone.api.user.application.CurrentUser;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 현재 사용자 조회 API(docs/api/cards.md {@code GET /api/v1/me}). 보유 카드 수가 필요해 {@code card} 패키지에 둔다. 사용자 정보는
 * {@code user} 패키지의 공개 부품으로만 읽는다.
 *
 * <p>{@code local}·{@code demo} 프로필에서만 등록한다. 그 밖의 환경에서는 경로가 없어 404 {@code NOT_FOUND}다.
 */
@RestController
@Profile({ "local", "demo" })
public class CurrentUserController {

	private final UserCardService userCardService;

	public CurrentUserController(UserCardService userCardService) {
		this.userCardService = userCardService;
	}

	@GetMapping("/api/v1/me")
	public ApiResponse<CurrentUserResponse> getCurrentUser(CurrentUser currentUser) {
		return ApiResponse.of(CurrentUserResponse.from(this.userCardService.summarize(currentUser)));
	}

}
