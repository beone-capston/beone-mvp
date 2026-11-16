package com.beone.api.user.web;

import com.beone.api.common.web.ApiResponse;
import com.beone.api.user.application.DemoUserService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시연 사용자 목록 API(docs/api/cards.md). 사용자 자원이 아니므로 시연 사용자 헤더가 필요 없다.
 *
 * <p>{@code local}·{@code demo} 프로필에서만 등록한다. 그 밖의 환경에서는 경로가 없어 404 {@code NOT_FOUND}다.
 */
@RestController
@Profile({ "local", "demo" })
@RequestMapping("/api/v1/demo-users")
public class DemoUserController {

	private final DemoUserService demoUserService;

	public DemoUserController(DemoUserService demoUserService) {
		this.demoUserService = demoUserService;
	}

	@GetMapping
	public ApiResponse<DemoUsersResponse> listDemoUsers() {
		return ApiResponse.of(DemoUsersResponse.from(this.demoUserService.listDemoUsers()));
	}

}
