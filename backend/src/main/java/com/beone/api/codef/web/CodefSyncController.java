package com.beone.api.codef.web;

import com.beone.api.codef.application.CodefSyncService;
import com.beone.api.common.web.ApiResponse;
import com.beone.api.user.application.CurrentUser;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CODEF 조회·동기화 API(docs/api/codef.md POST /api/v1/me/codef/syncs). {@code local}·{@code demo} 프로필에서만 등록한다. CODEF 조회는
 * 요청 안에서 동기로 끝난다(관리자 판단 P11).
 */
@RestController
@Profile({ "local", "demo" })
@RequestMapping("/api/v1/me/codef/syncs")
public class CodefSyncController {

	private final CodefSyncService codefSyncService;

	public CodefSyncController(CodefSyncService codefSyncService) {
		this.codefSyncService = codefSyncService;
	}

	@PostMapping
	public ApiResponse<CodefSyncResponse> sync(CurrentUser currentUser,
			@RequestBody(required = false) CodefSyncRequest request) {
		CodefSyncRequest body = (request == null) ? new CodefSyncRequest() : request;
		return ApiResponse.of(CodefSyncResponse.from(this.codefSyncService.sync(currentUser, body.from(), body.to())));
	}

}
