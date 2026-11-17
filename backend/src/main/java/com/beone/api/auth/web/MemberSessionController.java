package com.beone.api.auth.web;

import com.beone.api.auth.application.LoginResult;
import com.beone.api.auth.application.MemberLoginService;
import com.beone.api.common.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 로그인·로그아웃·현재 회원 세션 조회 API(docs/api/auth.md U3). {@code local}·{@code demo} 프로필 전용이다.
 *
 * <p>로그인·로그아웃은 CSRF 검사를 통과한 뒤 처리하며, 로그인 판정 순서는 403 {@code CSRF_INVALID} → 400 {@code INVALID_REQUEST} →
 * 429 {@code RATE_LIMITED} → 401 {@code INVALID_CREDENTIALS} → 403 {@code EMAIL_NOT_VERIFIED}다. 로그인 성공·로그아웃 응답의 세션
 * 쿠키는 인터셉터가 심은 갱신 쿠키를 {@link HttpServletResponse#setHeader}로 덮어써 한 개만 보낸다. 세션 조회는 시연 사용자 헤더를 쓰지
 * 않고 쿠키를 다시 심지 않는다.
 */
@RestController
@Profile({ "local", "demo" })
class MemberSessionController {

	private final MemberLoginService loginService;

	MemberSessionController(MemberLoginService loginService) {
		this.loginService = loginService;
	}

	@PostMapping("/api/v1/auth/login")
	ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
			HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
		LoginResult result = this.loginService.login(request.email(), request.password(),
				SessionCookie.read(servletRequest));
		return switch (result) {
			case LoginResult.Succeeded ok -> {
				servletResponse.setHeader(HttpHeaders.SET_COOKIE,
						SessionCookie.issue(ok.session().sessionId(), ok.session().cookieMaxAge()));
				yield ResponseEntity.ok(ApiResponse.of(new LoginResponse(ok.memberId(), ok.email())));
			}
			case LoginResult.InvalidCredentials ignored -> throw new InvalidCredentialsException();
			case LoginResult.EmailNotVerified ignored -> throw new EmailNotVerifiedException();
			case LoginResult.RateLimited limited -> throw new LoginRateLimitedException(limited.retryAfter());
		};
	}

	@PostMapping("/api/v1/auth/logout")
	ResponseEntity<Void> logout(HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
		this.loginService.logout(SessionCookie.read(servletRequest));
		servletResponse.setHeader(HttpHeaders.SET_COOKIE, SessionCookie.clear());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/api/v1/auth/session")
	ResponseEntity<ApiResponse<MemberSessionResponse>> session(HttpServletRequest servletRequest) {
		return this.loginService.currentSession(SessionCookie.read(servletRequest))
			.map(session -> ResponseEntity.ok(ApiResponse.of(MemberSessionResponse.from(session))))
			.orElseThrow(UnauthenticatedException::new);
	}

}
