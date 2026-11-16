package com.beone.api.user.web;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * {@link CurrentUserResolver}를 Spring MVC 인자 해석기로 등록한다. {@code local}·{@code demo} 프로필 전용.
 */
@Configuration(proxyBeanMethods = false)
@Profile({ "local", "demo" })
public class UserWebConfiguration implements WebMvcConfigurer {

	private final CurrentUserResolver currentUserResolver;

	public UserWebConfiguration(CurrentUserResolver currentUserResolver) {
		this.currentUserResolver = currentUserResolver;
	}

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(this.currentUserResolver);
	}

}
