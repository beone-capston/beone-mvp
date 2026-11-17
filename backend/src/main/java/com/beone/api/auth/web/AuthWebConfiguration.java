package com.beone.api.auth.web;

import com.beone.api.auth.application.AuthSessionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * {@code /api/v1/auth/**}에 CSRF 검사 인터셉터({@link AuthRequestInterceptor})와 {@code no-store} 필터({@link AuthNoStoreFilter})를
 * 등록한다. {@code local}·{@code demo} 프로필 전용이다.
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

	/**
	 * 인증 경로의 {@code no-store} 필터. 서블릿 URL 패턴 {@code /api/v1/auth/*}는 그 아래 모든 경로에 맞는다.
	 * @return 필터 등록
	 */
	@Bean
	FilterRegistrationBean<AuthNoStoreFilter> authNoStoreFilter() {
		FilterRegistrationBean<AuthNoStoreFilter> registration = new FilterRegistrationBean<>(new AuthNoStoreFilter());
		registration.addUrlPatterns("/api/v1/auth/*");
		return registration;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new AuthRequestInterceptor(this.authSessionService)).addPathPatterns(AUTH_PATH_PATTERN);
	}

}
