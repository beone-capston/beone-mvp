package com.beone.api.auth.web;

import java.util.Optional;

import com.beone.api.auth.application.ActiveSession;
import com.beone.api.auth.application.AuthSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * {@code /api/v1/auth/**} 요청의 공통 처리(docs/api/auth.md).
 *
 * <ul>
 * <li>모든 응답(실패 포함)에 {@code Cache-Control: no-store}를 붙인다.</li>
 * <li>POST 요청은 같은 세션의 쿠키와 {@code X-CSRF-TOKEN} 헤더를 함께 요구한다. 쿠키가 없거나 세션이 만료·무효이거나 토큰이
 * 다르면 본문 검증보다 먼저 403 {@code CSRF_INVALID}다.</li>
 * </ul>
 * 핸들러가 정해진 요청에만 실행되므로 없는 경로(404)·지원하지 않는 메서드(405)는 공통 처리를 따른다. 세션 서비스는 요청 때 꺼내 쓴다.
 * 웹 슬라이스 테스트가 다른 기능의 컨트롤러만 올릴 때 이 설정이 함께 올라와도 세션 서비스 빈이 필요 없게 하기 위해서다.
 */
class AuthRequestInterceptor implements HandlerInterceptor {

	private static final String NO_STORE = CacheControl.noStore().getHeaderValue();

	private final ObjectProvider<AuthSessionService> authSessionService;

	AuthRequestInterceptor(ObjectProvider<AuthSessionService> authSessionService) {
		this.authSessionService = authSessionService;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		response.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE);
		if (HttpMethod.POST.matches(request.getMethod())) {
			Optional<ActiveSession> session = this.authSessionService.getObject().use(SessionCookie.read(request));
			if (session.isEmpty() || !session.get().csrfMatches(request.getHeader(CsrfTokenResponse.HEADER_NAME))) {
				throw new CsrfInvalidException();
			}
		}
		return true;
	}

}
