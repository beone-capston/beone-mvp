package com.beone.api.auth.web;

import java.util.Optional;

import com.beone.api.auth.application.ActiveSession;
import com.beone.api.auth.application.AuthSessionService;
import com.beone.api.auth.application.IssuedSession;
import com.beone.api.common.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CSRF 토큰 조회 API(docs/api/auth.md). 회원 로그인이 필요 없다.
 *
 * <p>유효한 세션 쿠키가 있으면 그 세션의 토큰을 주고, 늘어난 미사용 만료에 맞춰 같은 쿠키를 새 {@code Max-Age}로 다시 심는다. 쿠키가
 * 없거나 만료·무효이면 익명 세션을 새로 발급하고 {@code Set-Cookie}로 심는다. 익명 세션은 회원 로그인이 아니다. 시연 사용자 헤더는 읽지 않는다. {@code local}·{@code demo} 프로필
 * 전용이며 그 밖의 환경에서는 경로가 없어 404 {@code NOT_FOUND}다.
 */
@RestController
@Profile({ "local", "demo" })
@RequestMapping("/api/v1/auth/csrf")
public class AuthCsrfController {

	private final AuthSessionService authSessionService;

	public AuthCsrfController(AuthSessionService authSessionService) {
		this.authSessionService = authSessionService;
	}

	@GetMapping
	public ResponseEntity<ApiResponse<CsrfTokenResponse>> getCsrfToken(HttpServletRequest request) {
		Optional<ActiveSession> active = this.authSessionService.use(SessionCookie.read(request));
		if (active.isPresent()) {
			ActiveSession session = active.get();
			return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, SessionCookie.issue(session.sessionId(), session.cookieMaxAge()))
				.body(ApiResponse.of(CsrfTokenResponse.of(session.csrfToken())));
		}
		IssuedSession issued = this.authSessionService.issueAnonymous();
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, SessionCookie.issue(issued.sessionId(), issued.cookieMaxAge()))
			.body(ApiResponse.of(CsrfTokenResponse.of(issued.csrfToken())));
	}

}
