package com.beone.api.auth.web;

import com.beone.api.auth.application.AuthSessionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 로컬 회원 프로필 {@code member}에서 업무 API({@code /api/v1/me}, {@code /api/v1/me/**})에 세션·CSRF 검사 인터셉터
 * ({@link MemberApiInterceptor})를 등록한다(결정 37). {@code local}·{@code demo}의 업무 API에는 걸지 않는다(기존 동작 유지).
 *
 * <p>{@code member}는 로컬 개발·테스트용 회원 프로필이다. 공개 배포 프로필 구성(O1)은 미결이다.
 */
@Configuration(proxyBeanMethods = false)
@Profile("member")
public class MemberApiWebConfiguration implements WebMvcConfigurer {

	/** 회원 업무 API 경로 패턴. */
	static final String[] MEMBER_API_PATHS = { "/api/v1/me", "/api/v1/me/**" };

	private final ObjectProvider<AuthSessionService> authSessionService;

	public MemberApiWebConfiguration(ObjectProvider<AuthSessionService> authSessionService) {
		this.authSessionService = authSessionService;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new MemberApiInterceptor(this.authSessionService)).addPathPatterns(MEMBER_API_PATHS);
	}

}
