package com.beone.api.user.web;

import com.beone.api.user.application.CurrentUser;
import com.beone.api.user.application.DemoUser;
import com.beone.api.user.application.DemoUserService;
import org.springframework.context.annotation.Profile;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 현재 요청의 사용자를 정하는 유일한 지점(D-09).
 *
 * <p>컨트롤러 메서드에 {@link CurrentUser} 인자를 두면 이 객체가 채운다. 컨트롤러·서비스는 요청에서 사용자 ID나
 * 헤더를 직접 읽지 않는다. {@code local}·{@code demo} 프로필에서만 등록하며 {@code X-BeONE-Demo-User} 헤더로
 * 사용자를 정한다. 세션 로그인을 붙이면 이 클래스의 구현만 바꾼다.
 */
@Component
@Profile({ "local", "demo" })
public class CurrentUserResolver implements HandlerMethodArgumentResolver {

	private final DemoUserService demoUserService;

	public CurrentUserResolver(DemoUserService demoUserService) {
		this.demoUserService = demoUserService;
	}

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return CurrentUser.class.equals(parameter.getParameterType());
	}

	@Override
	public CurrentUser resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		DemoUser demoUser = DemoUserHeader.parse(webRequest.getHeaderValues(DemoUserHeader.NAME));
		return this.demoUserService.currentUserOf(demoUser);
	}

}
