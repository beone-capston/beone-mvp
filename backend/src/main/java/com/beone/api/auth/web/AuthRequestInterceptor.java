package com.beone.api.auth.web;

import java.util.Optional;

import com.beone.api.auth.application.ActiveSession;
import com.beone.api.auth.application.AuthSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * {@code /api/v1/auth/**} POST 요청의 CSRF 검사(docs/api/auth.md).
 *
 * <p>POST 요청은 같은 세션의 쿠키와 {@code X-CSRF-TOKEN} 헤더를 함께 요구한다. 쿠키가 없거나 세션이 만료·무효이거나 토큰이 다르면
 * 본문 검증보다 먼저 403 {@code CSRF_INVALID}다. 통과하면 늘어난 미사용 만료에 맞춰 세션 쿠키를 새 {@code Max-Age}로 다시 심는다
 * (사본의 POST 응답 헤더 예시에 덧붙인 것, 2026-11-18 사용자 직접 결정). 핸들러가 정해진 요청에만 실행되므로 없는 경로(404)·지원하지 않는 메서드(405)는
 * 공통 처리를 따른다. {@code Cache-Control: no-store}는 404·405에도 붙도록 {@link AuthNoStoreFilter}가 맡는다. 세션 서비스는 요청 때 꺼내 쓴다.
 * 웹 슬라이스 테스트가 다른 기능의 컨트롤러만 올릴 때 이 설정이 함께 올라와도 세션 서비스 빈이 필요 없게 하기 위해서다.
 */
class AuthRequestInterceptor implements HandlerInterceptor {

	private final ObjectProvider<AuthSessionService> authSessionService;

	AuthRequestInterceptor(ObjectProvider<AuthSessionService> authSessionService) {
		this.authSessionService = authSessionService;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (HttpMethod.POST.matches(request.getMethod())) {
			Optional<ActiveSession> session = this.authSessionService.getObject().use(SessionCookie.read(request));
			if (session.isEmpty() || !session.get().csrfMatches(request.getHeader(CsrfTokenResponse.HEADER_NAME))) {
				throw new CsrfInvalidException();
			}
			response.setHeader(HttpHeaders.SET_COOKIE,
					SessionCookie.issue(session.get().sessionId(), session.get().cookieMaxAge()));
		}
		return true;
	}

}
