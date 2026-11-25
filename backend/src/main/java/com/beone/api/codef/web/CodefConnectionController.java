package com.beone.api.codef.web;

import com.beone.api.codef.application.LiveCodefGateway;
import com.beone.api.codef.application.CodefConnectResult;
import com.beone.api.codef.application.CodefConnectionService;
import com.beone.api.common.web.ApiResponse;
import com.beone.api.user.application.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * CODEF 연결 API(docs/api/codef.md). 현재 사용자는 {@link CurrentUser} 인자로만 받는다(D-09).
 *
 * <p>{@code local}·{@code demo}(가짜 게이트웨이)와 {@link LiveCodefGateway#PROFILE}(회원 세션, 실 게이트웨이, LP-1)에서만 등록한다. 공개
 * 프로필 {@code prod}를 포함한 그 밖의 환경에서는 경로가 없어 404 {@code NOT_FOUND}다. 최초 연결은 201,
 * 재연결은 200이고 본문 형식은 같다. 해제는 204이며 조회 데이터는 지우지 않는다(2026-11-18 사용자 직접 결정 P4).
 */
@RestController
@Profile({ "local", "demo", LiveCodefGateway.PROFILE })
@RequestMapping("/api/v1/me/codef/connection")
public class CodefConnectionController {

	private final CodefConnectionService codefConnectionService;

	public CodefConnectionController(CodefConnectionService codefConnectionService) {
		this.codefConnectionService = codefConnectionService;
	}

	@GetMapping
	public ApiResponse<CodefConnectionResponse> getConnection(CurrentUser currentUser) {
		return ApiResponse.of(CodefConnectionResponse.from(this.codefConnectionService.get(currentUser)));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<CodefConnectionResponse>> connect(CurrentUser currentUser,
			@Valid @RequestBody CodefConnectionRequest request) {
		CodefConnectResult result = this.codefConnectionService.connect(currentUser, request.loginId(),
				request.password());
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
			.body(ApiResponse.of(CodefConnectionResponse.from(result.connection())));
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void disconnect(CurrentUser currentUser) {
		this.codefConnectionService.disconnect(currentUser);
	}

}
