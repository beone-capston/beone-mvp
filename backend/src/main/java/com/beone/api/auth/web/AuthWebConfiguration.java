package com.beone.api.auth.web;

import com.beone.api.auth.application.AuthSessionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * {@link AuthRequestInterceptor}를 {@code /api/v1/auth/**}에 등록한다. {@code local}·{@code demo} 프로필 전용이다.
 */
@Configuration(proxyBeanMethods = false)
@Profile({ "local", "demo" })
public class AuthWebConfiguration implements WebMvcConfigurer {

	/** 인증 API 경로 패턴. */
	static final String AUTH_PATH_PATTERN = "/api/v1/auth/**";

	private final ObjectProvider<AuthSessionService> authSessionService;

	public AuthWebConfiguration(ObjectProvider<AuthSessionService> authSessionService) {
		this.authSessionService = authSessionService;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new AuthRequestInterceptor(this.authSessionService)).addPathPatterns(AUTH_PATH_PATTERN);
	}

}
