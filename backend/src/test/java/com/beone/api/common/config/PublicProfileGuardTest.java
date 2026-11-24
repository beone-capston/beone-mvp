package com.beone.api.common.config;

import com.beone.api.user.web.CurrentUserModeGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 공개 프로필 prod의 조합 검사와 설정 파일을 확인한다(결정 38-6·7).
 *
 * <p>{@link ApplicationContextRunner}는 프로필 그룹을 펼치지 않으므로 그룹이 풀린 상태를 흉내 낸다. 실제 그룹 동작과
 * {@code application-prod.yml} 값은 {@link SpringApplication}으로 설정 파일을 읽어 확인한다.
 */
class PublicProfileGuardTest {

	private static final String PROD_WITH_DEMO_MESSAGE = "prod 프로필은 local·demo 프로필과 함께 켤 수 없습니다";

	private static final String PROD_WITHOUT_MEMBER_MESSAGE = "prod 프로필은 member 프로필과 함께 켜져야 합니다";

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withBean(PublicProfileGuard.class)
		.withBean(CurrentUserModeGuard.class);

	@ParameterizedTest
	@ValueSource(strings = { "prod,local", "prod,demo", "prod,member,local", "prod,member,demo", "prod,local,demo" })
	void prodWithLocalOrDemoFailsStartup(String profiles) {
		this.runner.withPropertyValues("spring.profiles.active=" + profiles).run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining(PROD_WITH_DEMO_MESSAGE);
		});
	}

	@Test
	void prodWithMemberStarts() {
		this.runner.withPropertyValues("spring.profiles.active=prod,member")
			.run(context -> assertThat(context).hasNotFailed());
	}

	@Test
	void prodWithoutMemberFailsStartup() {
		// 그룹 설정이 지워져 prod만 켜진 상태
		this.runner.withPropertyValues("spring.profiles.active=prod").run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining(PROD_WITHOUT_MEMBER_MESSAGE);
		});
	}

	@ParameterizedTest
	@ValueSource(strings = { "member", "local", "demo", "local,demo" })
	void nonProdProfilesAreNotChecked(String profiles) {
		this.runner.withPropertyValues("spring.profiles.active=" + profiles)
			.run(context -> assertThat(context).hasNotFailed());
	}

	@Test
	void noActiveProfileIsNotChecked() {
		this.runner.run(context -> assertThat(context).hasNotFailed());
	}

	@Test
	void checkReadsActiveProfiles() {
		MockEnvironment prodOnly = new MockEnvironment();
		prodOnly.setActiveProfiles("prod");
		assertThatIllegalStateException().isThrownBy(() -> PublicProfileGuard.check(prodOnly))
			.withMessageContaining(PROD_WITHOUT_MEMBER_MESSAGE);

		MockEnvironment prodMember = new MockEnvironment();
		prodMember.setActiveProfiles("prod", "member");
		assertThatNoException().isThrownBy(() -> PublicProfileGuard.check(prodMember));
	}

	@Test
	void prodGroupActivatesMemberAndLoadsProdSettings() {
		try (ConfigurableApplicationContext context = guardedApplication().profiles("prod").run()) {
			ConfigurableEnvironment environment = context.getEnvironment();
			assertThat(environment.getActiveProfiles()).contains("prod", "member")
				.doesNotContain("local", "demo");
			assertThat(environment.getProperty("server.forward-headers-strategy")).isEqualTo("none");
			assertThat(environment.getProperty("spring.flyway.clean-disabled")).isEqualTo("true");
			assertThat(environment.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
			assertThat(environment.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
		}
	}

	@Test
	void memberAloneDoesNotLoadProdSettings() {
		try (ConfigurableApplicationContext context = guardedApplication().profiles("member").run()) {
			ConfigurableEnvironment environment = context.getEnvironment();
			assertThat(environment.getActiveProfiles()).containsExactly("member");
			assertThat(environment.getProperty("server.forward-headers-strategy")).isNull();
		}
	}

	@Test
	void prodGroupWithLocalFailsWithPublicProfileMessageFirst() {
		assertThatThrownBy(() -> guardedApplication().profiles("prod", "local").run())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining(PROD_WITH_DEMO_MESSAGE);
	}

	/** 두 가드만 등록하고 실제 application.yml·application-prod.yml을 읽는 최소 애플리케이션. */
	private static SpringApplicationBuilder guardedApplication() {
		return new SpringApplicationBuilder(PublicProfileGuard.class, CurrentUserModeGuard.class)
			.web(WebApplicationType.NONE)
			.logStartupInfo(false)
			.properties("spring.main.banner-mode=off");
	}

}
