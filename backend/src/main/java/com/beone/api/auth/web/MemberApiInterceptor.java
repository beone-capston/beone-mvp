package com.beone.api.auth.web;

import com.beone.api.auth.application.AuthSessionService;
import com.beone.api.auth.application.MemberRequestCheck;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 회원 프로필 업무 API({@code /api/v1/me/**})의 세션·CSRF 검사(결정 37 D3·D5·D6).
 *
 * <p>순서는 401 {@code UNAUTHENTICATED} → 쓰기(POST·PUT·PATCH·DELETE)의 403 {@code CSRF_INVALID}이며 둘 다 본문 검증(400)과
 * 자원 조회(404)보다 먼저다. 통과하면 미사용 만료를 늘리고 세션 쿠키를 새 {@code Max-Age}로 다시 심으며, 회원 ID를
 * {@link AuthenticatedMember}에 담는다. 시연 사용자 헤더는 보지 않는다. 컨트롤러 메서드가 정해진 요청만 검사하므로 등록하지 않은 경로는 인증 여부와 관계없이 404다.
 */
class MemberApiInterceptor implements HandlerInterceptor {

	private final ObjectProvider<AuthSessionService> authSessionService;

	MemberApiInterceptor(ObjectProvider<AuthSessionService> authSessionService) {
		this.authSessionService = authSessionService;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		// 컨트롤러가 없는 경로(정적 리소스 처리기로 떨어진 요청)는 검사하지 않아 인증 여부와 관계없이 404가 된다
		if (!(handler instanceof HandlerMethod)) {
			return true;
		}
		boolean write = isWrite(request.getMethod());
		MemberRequestCheck check = this.authSessionService.getObject()
			.checkMemberRequest(SessionCookie.read(request), request.getHeader(CsrfTokenResponse.HEADER_NAME), write);
		return switch (check) {
			case MemberRequestCheck.Unauthenticated ignored -> throw new UnauthenticatedException();
			case MemberRequestCheck.CsrfInvalid ignored -> throw new CsrfInvalidException();
			case MemberRequestCheck.Authenticated ok -> {
				response.setHeader(HttpHeaders.SET_COOKIE, SessionCookie.issue(ok.sessionId(), ok.cookieMaxAge()));
				AuthenticatedMember.set(request, ok.memberId());
				yield true;
			}
		};
	}

	private static boolean isWrite(String method) {
		return HttpMethod.POST.matches(method) || HttpMethod.PUT.matches(method) || HttpMethod.PATCH.matches(method)
				|| HttpMethod.DELETE.matches(method);
	}

}
