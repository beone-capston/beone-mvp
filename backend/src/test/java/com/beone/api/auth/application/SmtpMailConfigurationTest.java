package com.beone.api.auth.application;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * prod 메일 설정 검사(N1, 결정 39-6). 필수 설정이 없으면 기동을 멈추고 메시지에는 키 이름만 넣는다. 값은 모두 가짜다.
 */
class SmtpMailConfigurationTest {

	private static final String FAKE_PASSWORD = "fake-app-password-should-not-appear";

	private static final List<String> COMPLETE = List.of("spring.mail.host=127.0.0.1", "spring.mail.port=2525",
			"spring.mail.username=synthetic-user", "spring.mail.password=" + FAKE_PASSWORD,
			"beone.mail.from=no-reply@beone.example.test", "beone.mail.link-base-url=https://beone.example.test/");

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
		.withUserConfiguration(SmtpMailConfiguration.class);

	@Test
	void prodWithAllSettingsRegistersTheSmtpSender() {
		this.runner.withPropertyValues(with("spring.profiles.active=prod,member").toArray(String[]::new)).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(AuthMailSender.class)).isInstanceOf(SmtpAuthMailSender.class);
		});
	}

	@ParameterizedTest
	@ValueSource(strings = { "spring.mail.host", "spring.mail.port", "spring.mail.username", "spring.mail.password",
			"beone.mail.from", "beone.mail.link-base-url" })
	void eachMissingSettingFailsStartupNamingOnlyTheKey(String key) {
		List<String> properties = new ArrayList<>(with("spring.profiles.active=prod,member"));
		properties.removeIf(p -> p.startsWith(key + "="));
		this.runner.withPropertyValues(properties.toArray(String[]::new)).run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).rootCause()
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("prod 프로필에는 메일 발송 설정이 필요합니다")
				.hasMessageContaining(key)
				.message()
				.doesNotContain(FAKE_PASSWORD)
				.doesNotContain("synthetic-user");
		});
	}

	@Test
	void plainHttpLinkBaseUrlFailsStartup() {
		List<String> properties = new ArrayList<>(with("spring.profiles.active=prod,member"));
		properties.replaceAll(p -> p.startsWith("beone.mail.link-base-url=") ? "beone.mail.link-base-url=http://x.test" : p);
		this.runner.withPropertyValues(properties.toArray(String[]::new)).run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).rootCause()
				.hasMessageContaining("beone.mail.link-base-url는 https 주소여야 합니다")
				.message()
				.doesNotContain("http://x.test");
		});
	}

	@ParameterizedTest
	@ValueSource(strings = { "member", "local", "demo" })
	void nonProdProfilesDoNotUseSmtpEvenWithSettings(String profiles) {
		this.runner.withPropertyValues(with("spring.profiles.active=" + profiles).toArray(String[]::new)).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean(SmtpAuthMailSender.class).doesNotHaveBean(AuthMailSender.class);
		});
	}

	private static List<String> with(String profile) {
		List<String> properties = new ArrayList<>(COMPLETE);
		properties.add(profile);
		return properties;
	}

}
