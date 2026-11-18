package com.beone.api.auth.web;

import com.beone.api.auth.application.PasswordResetService;
import com.beone.api.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 비밀번호 재설정 요청·완료 API(docs/api/auth.md U4). 요청은 계정 유무와 관계없이 같은 202, 완료는 204다. CSRF·{@code no-store}는
 * 인증 경로 공통 처리가 맡는다. {@code local}·{@code demo} 프로필 전용이다.
 */
@RestController
@Profile({ "local", "demo" })
class PasswordResetController {

	private final PasswordResetService resetService;

	PasswordResetController(PasswordResetService resetService) {
		this.resetService = resetService;
	}

	@PostMapping("/api/v1/auth/password-reset-requests")
	ResponseEntity<ApiResponse<AcceptedResponse>> requestReset(@Valid @RequestBody PasswordResetRequestRequest request) {
		this.resetService.requestReset(request.email());
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.of(AcceptedResponse.MAIL_REQUEST));
	}

	@PostMapping("/api/v1/auth/password-resets")
	ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
		this.resetService.resetPassword(request.token(), request.newPassword());
		return ResponseEntity.noContent().build();
	}

}
