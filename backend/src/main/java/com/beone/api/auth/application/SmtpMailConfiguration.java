package com.beone.api.auth.application;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 공개 프로필 {@code prod}의 인증 메일 발송기를 만든다(N1, 결정 39-6).
 *
 * <p>설정은 Spring 표준 {@code spring.mail.*}와 {@value #FROM}, {@value #LINK_BASE_URL}이며 값은 환경 변수로만 넣는다(앱 비밀번호는 서버
 * 환경에만). 필수 설정이 하나라도 없으면 기동을 멈추고, 메시지에는 키 이름만 넣는다. TLS(STARTTLS 필수)·시간 제한 기본값은
 * {@code application-prod.yml}에 있다. 다른 프로필은 가짜 발송기({@link FakeAuthMailSender})를 쓴다.
 */
@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class SmtpMailConfiguration {

	/** 발신 주소. 환경 변수 {@code BEONE_MAIL_FROM}. */
	public static final String FROM = "beone.mail.from";

	/** 메일 링크 기준 주소(https). 환경 변수 {@code BEONE_MAIL_LINK_BASE_URL}. 값은 N2 결정 대기. */
	public static final String LINK_BASE_URL = "beone.mail.link-base-url";

	/** prod에서 반드시 있어야 하는 설정. */
	static final List<String> REQUIRED = List.of("spring.mail.host", "spring.mail.port", "spring.mail.username",
			"spring.mail.password", FROM, LINK_BASE_URL);

	@Bean
	AuthMailSender smtpAuthMailSender(Environment environment, ObjectProvider<JavaMailSender> mailSender) {
		List<String> missing = REQUIRED.stream().filter(key -> isBlank(environment.getProperty(key))).toList();
		if (!missing.isEmpty()) {
			throw new IllegalStateException("prod 프로필에는 메일 발송 설정이 필요합니다: " + String.join(", ", missing)
					+ " (실메일 발송기, 결정 39-6).");
		}
		String linkBaseUrl = environment.getProperty(LINK_BASE_URL).strip();
		if (!linkBaseUrl.startsWith("https://")) {
			throw new IllegalStateException(LINK_BASE_URL + "는 https 주소여야 합니다(메일 링크 기준 주소, N2 권장안).");
		}
		JavaMailSender sender = mailSender.getIfAvailable();
		if (sender == null) {
			throw new IllegalStateException("메일 발송기(JavaMailSender)를 만들 수 없습니다. spring.mail.* 설정을 확인하세요.");
		}
		return new SmtpAuthMailSender(sender, environment.getProperty(FROM).strip(), stripTrailingSlash(linkBaseUrl));
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static String stripTrailingSlash(String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

}
