package com.beone.api.common.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * {@code local}·{@code demo} 프로필의 CORS 허용 목록(D-23 11항).
 *
 * <p>프런트 개발 서버 origin 하나와 계약된 메서드·헤더만 허용하고 자격 증명(쿠키)은 허용하지 않는다.
 * 그 밖의 프로필에는 CORS 허용을 두지 않는다(공개 환경의 CORS는 D-24 기준 미정).
 */
@Configuration(proxyBeanMethods = false)
@Profile({ "local", "demo" })
public class LocalDemoCorsConfiguration implements WebMvcConfigurer {

	static final String ALLOWED_ORIGIN = "http://localhost:5173";

	static final String[] ALLOWED_METHODS = { "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS" };

	static final String[] ALLOWED_HEADERS = { "Content-Type", "X-BeONE-Demo-User", "Idempotency-Key" };

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
			.allowedOrigins(ALLOWED_ORIGIN)
			.allowedMethods(ALLOWED_METHODS)
			.allowedHeaders(ALLOWED_HEADERS)
			.allowCredentials(false);
	}

}
