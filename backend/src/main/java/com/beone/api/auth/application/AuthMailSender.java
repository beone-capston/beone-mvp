package com.beone.api.auth.application;

/**
 * 인증 메일 발송 포트. local·demo·member는 가짜 발송기({@link FakeAuthMailSender}), 공개 프로필 prod는 SMTP 발송기
 * ({@link SmtpAuthMailSender}, 결정 39-6)를 쓴다(docs/api/auth.md).
 */
public interface AuthMailSender {

	/**
	 * @param mail 보낼 메일
	 */
	void send(AuthMail mail);

}
