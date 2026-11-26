package com.beone.api.user.web;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * 현재 사용자 결정 방식은 프로필로 하나만 고른다(결정 37 D4). 시연 헤더 방식({@code local}·{@code demo})과 회원 세션 방식
 * ({@code member})을 함께 켜면 빈을 만들기 전에 기동을 멈춘다. 두 방식이 섞이면 한 요청의 사용자 결정 규칙이 둘이 되기 때문이다.
 */
@Component
public class CurrentUserModeGuard implements BeanFactoryPostProcessor, EnvironmentAware {

	private Environment environment;

	@Override
	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		check(this.environment);
	}

	/**
	 * 활성 프로필 조합을 검사한다.
	 * @param environment 실행 환경
	 * @throws IllegalStateException {@code member}와 {@code local}·{@code demo}가 함께 켜진 경우
	 */
	static void check(Environment environment) {
		if (environment.acceptsProfiles(Profiles.of("member"))
				&& environment.acceptsProfiles(Profiles.of("local | demo"))) {
			throw new IllegalStateException(
					"member 프로필은 local·demo 프로필과 함께 켤 수 없습니다(현재 사용자 결정 방식은 하나만, 결정 37 D4).");
		}
	}

}
