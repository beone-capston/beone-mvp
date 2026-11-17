package com.beone.api.auth.application;

/**
 * 인증 메일 발송 포트. 지금은 테스트 격리용 가짜 발송기({@link FakeAuthMailSender})만 있다. 실발송·외부 공급자·발신 주소는
 * 미결이다(docs/api/auth.md).
 */
public interface AuthMailSender {

	/**
	 * @param mail 보낼 메일
	 */
	void send(AuthMail mail);

}
