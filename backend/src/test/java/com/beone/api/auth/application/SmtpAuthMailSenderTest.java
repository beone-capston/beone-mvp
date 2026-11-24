package com.beone.api.auth.application;

import java.util.List;
import java.util.Properties;

import com.beone.api.auth.persistence.AuthTokenPurpose;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * SMTP 발송기(N1, 결정 39-6)를 루프백 스텁 SMTP로만 확인한다. 실제 메일 서버로는 나가지 않는다. 스텁은 STARTTLS를 지원하지 않으므로 이 시험의
 * 발송기 설정에서만 STARTTLS를 끈다(prod 기본값은 STARTTLS 필수).
 */
class SmtpAuthMailSenderTest {

	private static final String TOKEN = "SyntheticToken_0123456789abcdefghijklmnopqrstu".substring(0, 43);

	private static final String TO = "member@example.com";

	private static final String FROM = "BeONE <no-reply@beone.example.test>";

	private static final String BASE = "https://beone.example.test";

	private final Logger logger = (Logger) LoggerFactory.getLogger(SmtpAuthMailSender.class);

	private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

	private SmtpStubServer smtp;

	@BeforeEach
	void start() {
		this.smtp = new SmtpStubServer();
		this.logs.start();
		this.logger.addAppender(this.logs);
		this.logger.setLevel(Level.DEBUG);
	}

	@AfterEach
	void stop() {
		this.logger.detachAppender(this.logs);
		this.logger.setLevel(null);
		this.smtp.close();
	}

	@Test
	void verificationMailCarriesHeadersAndLinkWithTokenInFragment() throws Exception {
		sender(this.smtp.port()).send(new AuthMail(TO, AuthTokenPurpose.EMAIL_VERIFICATION, TOKEN));

		SmtpStubServer.Received mail = this.smtp.received().getFirst();
		assertThat(mail.recipients()).containsExactly(TO);
		assertThat(mail.from()).isEqualTo("no-reply@beone.example.test");
		MimeMessage message = mail.message();
		assertThat(((InternetAddress) message.getFrom()[0]).getAddress()).isEqualTo("no-reply@beone.example.test");
		assertThat(((InternetAddress) message.getAllRecipients()[0]).getAddress()).isEqualTo(TO);
		assertThat(message.getSubject()).isEqualTo("[BeONE] 이메일 인증을 완료해 주세요");
		assertThat(mail.text()).contains(BASE + "/verify-email#token=" + TOKEN).contains("30분");
	}

	@Test
	void passwordResetMailUsesItsOwnPathAndLifetime() throws Exception {
		sender(this.smtp.port()).send(new AuthMail(TO, AuthTokenPurpose.PASSWORD_RESET, TOKEN));

		SmtpStubServer.Received mail = this.smtp.received().getFirst();
		assertThat(mail.message().getSubject()).isEqualTo("[BeONE] 비밀번호 재설정 안내");
		assertThat(mail.text()).contains(BASE + "/reset-password#token=" + TOKEN).contains("15분")
			.doesNotContain("/verify-email");
	}

	@Test
	void successfulSendLogsNothingWithTheToken() {
		sender(this.smtp.port()).send(new AuthMail(TO, AuthTokenPurpose.EMAIL_VERIFICATION, TOKEN));

		assertThat(messages()).noneMatch(m -> m.contains(TOKEN) || m.contains(TO));
	}

	@Test
	void failureIsNotThrownNotRetriedAndLogsOnlyMaskedRecipientAndReason() {
		int closedPort = this.smtp.port();
		this.smtp.close();

		assertThatNoException()
			.isThrownBy(() -> sender(closedPort).send(new AuthMail(TO, AuthTokenPurpose.PASSWORD_RESET, TOKEN)));

		List<String> messages = messages();
		assertThat(messages).singleElement()
			.satisfies(m -> assertThat(m).contains("purpose=PASSWORD_RESET")
				.contains("to=m***@example.com")
				.contains("reason=")
				.doesNotContain(TOKEN)
				.doesNotContain(TO)
				.doesNotContain(BASE));
	}

	@Test
	void maskKeepsOnlyFirstCharacterAndDomain() {
		assertThat(SmtpAuthMailSender.mask("member@example.com")).isEqualTo("m***@example.com");
		assertThat(SmtpAuthMailSender.mask("a@b.c")).isEqualTo("a***@b.c");
		assertThat(SmtpAuthMailSender.mask("not-an-address")).isEqualTo("***");
	}

	private List<String> messages() {
		return this.logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
	}

	private static SmtpAuthMailSender sender(int port) {
		JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
		mailSender.setHost("127.0.0.1");
		mailSender.setPort(port);
		mailSender.setUsername("synthetic-user");
		mailSender.setPassword("synthetic-app-password");
		Properties properties = new Properties();
		properties.setProperty("mail.smtp.auth", "true");
		properties.setProperty("mail.smtp.starttls.enable", "false");
		properties.setProperty("mail.smtp.connectiontimeout", "2000");
		properties.setProperty("mail.smtp.timeout", "2000");
		mailSender.setJavaMailProperties(properties);
		return new SmtpAuthMailSender(mailSender, FROM, BASE);
	}

}
