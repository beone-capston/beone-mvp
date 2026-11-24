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
 * 로컬 회원 프로필 {@code member}에서 업무 API({@code /api/v1/me}, {@code /api/v1/me/**})에 세션·CSRF 검사 인터셉터
 * ({@link MemberApiInterceptor})와 {@code no-store} 필터({@link AuthNoStoreFilter})를 등록한다(결정 37, G1 D5 권장 4항).
 * {@code local}·{@code demo}의 업무 API에는 걸지 않는다(기존 동작 유지).
 *
 * <p>{@code no-store}는 인증 API와 같은 규칙으로 성공·실패(401·403·404·405 포함) 모든 응답에 붙인다. 업무 응답은 회원의 개인 데이터와
 * 세션 쿠키 재발급({@code Set-Cookie})을 담기 때문이다.
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

	/**
	 * 회원 업무 경로의 {@code no-store} 필터. 서블릿 URL 패턴 {@code /api/v1/me/*}는 {@code /api/v1/me}와 그 아래 모든 경로에 맞는다.
	 * 핸들러 조회 전에 돌기 때문에 등록하지 않은 경로의 404에도 적용된다.
	 * @return 필터 등록
	 */
	@Bean
	FilterRegistrationBean<AuthNoStoreFilter> memberNoStoreFilter() {
		FilterRegistrationBean<AuthNoStoreFilter> registration = new FilterRegistrationBean<>(new AuthNoStoreFilter());
		registration.addUrlPatterns("/api/v1/me", "/api/v1/me/*");
		return registration;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new MemberApiInterceptor(this.authSessionService)).addPathPatterns(MEMBER_API_PATHS);
	}

}
