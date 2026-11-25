package com.beone.api.user.web;

import com.beone.api.auth.web.AuthenticatedMember;
import com.beone.api.user.application.CurrentUser;
import com.beone.api.user.application.MemberUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 로컬 회원 프로필 {@code member}의 현재 사용자 결정 지점(D-09, 결정 37 D4). 이 프로필에서는 이 객체 하나만 {@link CurrentUser}를
 * 만든다.
 *
 * <p>세션 쿠키 검사는 auth의 업무 API 인터셉터가 먼저 끝내고 회원 ID를 요청 속성에 담는다. 이 객체는 그 회원 ID로 회원의 '내 카드'
 * 장부를 찾는다. {@code X-BeONE-Demo-User} 헤더는 읽지 않으므로 보내도 무시된다. 회원 ID가 없거나 장부가 없으면 서버 구성 오류이므로
 * {@link IllegalStateException}을 던진다(공통 처리기에서 500). 인증된 회원은 이메일 인증 때 장부가 생기기 때문이다.
 */
@Component
@Profile("member")
public class MemberCurrentUserResolver implements HandlerMethodArgumentResolver {

	private final MemberUserService memberUserService;

	public MemberCurrentUserResolver(MemberUserService memberUserService) {
		this.memberUserService = memberUserService;
	}

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return CurrentUser.class.equals(parameter.getParameterType());
	}

	@Override
	public CurrentUser resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
		String memberId = (request != null) ? AuthenticatedMember.memberIdOf(request).orElse(null) : null;
		if (memberId == null) {
			throw new IllegalStateException("회원 세션 검사를 거치지 않은 요청입니다.");
		}
		return this.memberUserService.currentUserOf(memberId)
			.orElseThrow(() -> new IllegalStateException("회원 장부가 없습니다."));
	}

}
