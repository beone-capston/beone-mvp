package com.beone.api.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.beone.api.auth.persistence.AuthTokenRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 가짜 메일 발송기의 프로필 범위를 확인한다(결정 38-6, G5 L2·L3).
 *
 * <p>공개 프로필 {@code prod}에는 가짜 발송기가 없고 실메일 발송기도 아직 없으므로(N1 결정 대기), 메일 발송기를 쓰는 {@link AuthTokenMailer}가
 * 의존성을 찾지 못해 기동에 실패한다. 이 실패를 의도한 동작으로 고정한다. 실제 애플리케이션에서는 {@code spring.profiles.group.prod: member}로
 * 두 프로필이 함께 켜지지만 {@link ApplicationContextRunner}는 그룹을 펼치지 않으므로 {@code prod,member}로 직접 켠다.
 */
class AuthMailSenderProfileTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(FakeAuthMailSender.class, AuthTokenMailer.class)
		.withBean(AuthTokenRepository.class, () -> mock(AuthTokenRepository.class));

	@Test
	void prodWithoutAMailSenderFailsStartupNamingTheMissingMailSender() {
		this.runner.withPropertyValues("spring.profiles.active=prod,member").run(context -> {
			assertThat(context).hasFailed();
			Throwable failure = context.getStartupFailure();
			assertThat(failure).hasMessageContaining("authTokenMailer")
				.hasRootCauseInstanceOf(NoSuchBeanDefinitionException.class)
				.rootCause()
				.hasMessageContaining(AuthMailSender.class.getName());
		});
	}

	@Test
	void prodDoesNotRegisterTheFakeMailSenderEvenWhenAnotherSenderExists() {
		this.runner.withPropertyValues("spring.profiles.active=prod,member")
			.withBean(AuthMailSender.class, () -> mail -> {
			})
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).doesNotHaveBean(FakeAuthMailSender.class);
				assertThat(context).hasSingleBean(AuthMailSender.class);
			});
	}

	@ParameterizedTest
	@ValueSource(strings = { "member", "local", "demo" })
	void nonProdProfilesKeepTheFakeMailSender(String profiles) {
		this.runner.withPropertyValues("spring.profiles.active=" + profiles).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(FakeAuthMailSender.class);
		});
	}

}
