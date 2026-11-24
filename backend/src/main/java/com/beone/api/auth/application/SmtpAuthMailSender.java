package com.beone.api.auth.application;

import java.time.Duration;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 인증 메일을 SMTP로 보낸다(N1, 결정 39-6). 공개 프로필 {@code prod}에서만 {@link SmtpMailConfiguration}이 만든다.
 *
 * <p>본문 링크는 설정한 기준 주소(N2 권장안: 요청의 Host 헤더를 쓰지 않음)에 화면 경로와 토큰을 붙인다. 토큰은 URL 조각({@code #token=})에 두어
 * 웹 서버 접근 로그·Referer에 실리지 않게 한다. 화면 경로와 토큰 위치는 결정 28의 미결 항목이라 바뀔 수 있다.
 *
 * <p>발송이 실패해도 예외를 밖으로 던지지 않고 다시 보내지 않는다. 가입·재요청·재설정 요청은 실제 발송 여부와 관계없이 같은 202이고 메일 도착을
 * 보장하지 않는다는 기존 계약(docs/api/auth.md)을 그대로 따른다. 실패 로그에는 목적, 마스킹한 수신자, 예외 종류만 남기고 토큰·링크·예외 문구는
 * 남기지 않는다.
 */
public class SmtpAuthMailSender implements AuthMailSender {

	/** 이메일 인증 화면 경로. 결정 28의 미결 항목이다. */
	static final String VERIFICATION_PATH = "/verify-email#token=";

	/** 비밀번호 재설정 화면 경로. 결정 28의 미결 항목이다. */
	static final String PASSWORD_RESET_PATH = "/reset-password#token=";

	private static final Logger log = LoggerFactory.getLogger(SmtpAuthMailSender.class);

	private final JavaMailSender mailSender;

	private final String from;

	private final String linkBaseUrl;

	/**
	 * @param mailSender Spring의 SMTP 발송기
	 * @param from 발신 주소
	 * @param linkBaseUrl 링크 기준 주소(https, 끝 슬래시 없음)
	 */
	public SmtpAuthMailSender(JavaMailSender mailSender, String from, String linkBaseUrl) {
		this.mailSender = Objects.requireNonNull(mailSender, "mailSender");
		this.from = Objects.requireNonNull(from, "from");
		this.linkBaseUrl = Objects.requireNonNull(linkBaseUrl, "linkBaseUrl");
	}

	@Override
	public void send(AuthMail mail) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(this.from);
		message.setTo(mail.to());
		switch (mail.purpose()) {
			case EMAIL_VERIFICATION -> {
				message.setSubject("[BeONE] 이메일 인증을 완료해 주세요");
				message.setText(body("이메일 인증", MemberRegistrationService.EMAIL_VERIFICATION_TTL,
						this.linkBaseUrl + VERIFICATION_PATH + mail.token()));
			}
			case PASSWORD_RESET -> {
				message.setSubject("[BeONE] 비밀번호 재설정 안내");
				message.setText(body("비밀번호 재설정", PasswordResetService.PASSWORD_RESET_TTL,
						this.linkBaseUrl + PASSWORD_RESET_PATH + mail.token()));
			}
		}
		try {
			this.mailSender.send(message);
		}
		catch (RuntimeException ex) {
			// 예외 문구에 주소·서버 응답이 들어갈 수 있어 종류만 남긴다
			log.warn("auth mail send failed purpose={} to={} reason={}", mail.purpose(), mask(mail.to()),
					ex.getClass().getSimpleName());
		}
	}

	private static String body(String action, Duration ttl, String link) {
		return """
				아래 링크를 열어 %s을(를) 완료해 주세요. 링크는 %d분 동안 한 번만 쓸 수 있습니다.

				%s

				본인이 요청하지 않았다면 이 메일을 무시해 주세요.
				""".formatted(action, ttl.toMinutes(), link);
	}

	/**
	 * 로그용 수신자 마스킹. 앞 한 글자와 도메인만 남긴다.
	 * @param address 정규화한 이메일
	 * @return 마스킹한 주소
	 */
	static String mask(String address) {
		int at = address.indexOf('@');
		if (at <= 0) {
			return "***";
		}
		return address.charAt(0) + "***" + address.substring(at);
	}

}
