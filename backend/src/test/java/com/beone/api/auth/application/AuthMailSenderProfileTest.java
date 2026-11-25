package com.beone.api.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.beone.api.auth.persistence.AuthTokenRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 메일 발송기의 프로필 범위를 확인한다(결정 38-6·39-6, G5 L2·L3, #207).
 *
 * <p>공개 프로필 {@code prod}에는 가짜 발송기가 없고 SMTP 발송기({@link SmtpMailConfiguration})만 있다. 메일 설정이 없으면 SMTP 발송기를 만들지
 * 못해 기동에 실패하고, 설정이 있으면 SMTP 발송기로 기동한다. 실제 애플리케이션에서는 {@code spring.profiles.group.prod: member}로
 * 두 프로필이 함께 켜지지만 {@link ApplicationContextRunner}는 그룹을 펼치지 않으므로 {@code prod,member}로 직접 켠다.
 */
class AuthMailSenderProfileTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(FakeAuthMailSender.class, AuthTokenMailer.class)
		.withBean(AuthTokenRepository.class, () -> mock(AuthTokenRepository.class));

	@Test
	void prodWithoutMailSettingsFailsStartupNamingTheMissingKeys() {
		this.runner.withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
			.withUserConfiguration(SmtpMailConfiguration.class)
			.withPropertyValues("spring.profiles.active=prod,member")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).rootCause()
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("prod 프로필에는 메일 발송 설정이 필요합니다")
					.hasMessageContaining("spring.mail.host")
					.hasMessageContaining("beone.mail.link-base-url");
			});
	}

	@Test
	void prodWithMailSettingsStartsWithTheSmtpSenderOnly() {
		this.runner.withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
			.withUserConfiguration(SmtpMailConfiguration.class)
			.withPropertyValues("spring.profiles.active=prod,member", "spring.mail.host=127.0.0.1", "spring.mail.port=2525",
					"spring.mail.username=synthetic-user", "spring.mail.password=synthetic-app-password",
					"beone.mail.from=no-reply@beone.example.test", "beone.mail.link-base-url=https://beone.example.test")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).doesNotHaveBean(FakeAuthMailSender.class).hasSingleBean(AuthTokenMailer.class);
				assertThat(context.getBean(AuthMailSender.class)).isInstanceOf(SmtpAuthMailSender.class);
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
