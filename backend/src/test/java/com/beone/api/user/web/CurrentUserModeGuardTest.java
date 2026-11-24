package com.beone.api.user.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * 현재 사용자 결정 방식이 프로필로 하나만 켜지는지 확인한다(결정 37 D4). member와 local·demo를 함께 켜면 기동이 실패해야 한다.
 */
class CurrentUserModeGuardTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withBean(CurrentUserModeGuard.class);

	@ParameterizedTest
	@ValueSource(strings = { "member,local", "member,demo", "local,member,demo" })
	void memberWithLocalOrDemoFailsStartup(String profiles) {
		this.runner.withPropertyValues("spring.profiles.active=" + profiles).run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("member 프로필은 local·demo 프로필과 함께 켤 수 없습니다");
		});
	}

	@ParameterizedTest
	@ValueSource(strings = { "member", "local", "demo", "local,demo" })
	void singleModeStarts(String profiles) {
		this.runner.withPropertyValues("spring.profiles.active=" + profiles)
			.run(context -> assertThat(context).hasNotFailed());
	}

	@Test
	void checkReadsActiveProfiles() {
		MockEnvironment both = new MockEnvironment();
		both.setActiveProfiles("member", "local");
		assertThatIllegalStateException().isThrownBy(() -> CurrentUserModeGuard.check(both));

		MockEnvironment none = new MockEnvironment();
		assertThatNoException().isThrownBy(() -> CurrentUserModeGuard.check(none));
	}

}
