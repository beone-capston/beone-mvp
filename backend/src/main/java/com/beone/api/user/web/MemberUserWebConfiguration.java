package com.beone.api.user.web;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * {@link MemberCurrentUserResolver}를 Spring MVC 인자 해석기로 등록한다. 로컬 회원 프로필 {@code member} 전용이다.
 */
@Configuration(proxyBeanMethods = false)
@Profile("member")
public class MemberUserWebConfiguration implements WebMvcConfigurer {

	private final MemberCurrentUserResolver memberCurrentUserResolver;

	public MemberUserWebConfiguration(MemberCurrentUserResolver memberCurrentUserResolver) {
		this.memberCurrentUserResolver = memberCurrentUserResolver;
	}

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(this.memberCurrentUserResolver);
	}

}
