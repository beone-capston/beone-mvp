package com.beone.api.auth.web;

import com.beone.api.auth.application.MemberRegistrationService;
import com.beone.api.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원가입·인증메일 재요청·이메일 인증 완료 API(docs/api/auth.md U2).
 *
 * <p>CSRF 검사는 인터셉터가 본문 검증보다 먼저 한다. 판정 순서는 403 {@code CSRF_INVALID} → 400 {@code INVALID_REQUEST} → 429
 * {@code RATE_LIMITED}다. 가입과 재요청은 계정 유무와 관계없이 같은 202다. {@code local}·{@code demo}·{@code member} 프로필 전용이다.
 */
@RestController
@Profile({ "local", "demo", "member" })
class MemberRegistrationController {

	private final MemberRegistrationService registrationService;

	MemberRegistrationController(MemberRegistrationService registrationService) {
		this.registrationService = registrationService;
	}

	@PostMapping("/api/v1/auth/sign-up")
	ResponseEntity<ApiResponse<AcceptedResponse>> signUp(@Valid @RequestBody SignUpRequest request) {
		this.registrationService.signUp(request.email(), request.password());
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.of(AcceptedResponse.MAIL_REQUEST));
	}

	@PostMapping("/api/v1/auth/email-verification-requests")
	ResponseEntity<ApiResponse<AcceptedResponse>> requestVerificationEmail(
			@Valid @RequestBody EmailVerificationRequestRequest request) {
		this.registrationService.requestVerificationEmail(request.email());
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.of(AcceptedResponse.MAIL_REQUEST));
	}

	@PostMapping("/api/v1/auth/email-verifications")
	ResponseEntity<Void> verifyEmail(@Valid @RequestBody EmailVerificationRequest request) {
		this.registrationService.verifyEmail(request.token());
		return ResponseEntity.noContent().build();
	}

}
