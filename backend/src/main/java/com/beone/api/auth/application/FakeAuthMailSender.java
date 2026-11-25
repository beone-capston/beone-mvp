package com.beone.api.auth.application;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 메일을 실제로 보내지 않는 가짜 발송기. 보낸 메일을 메모리에만 담고 로그를 남기지 않는다. 테스트가 내용을 확인한다.
 *
 * <p>오래 켜 둔 시연 서버에서 메모리가 늘지 않도록 최근 {@value #CAPACITY}통만 둔다. {@code local}·{@code demo}·{@code member} 프로필 전용이다.
 *
 * <p>공개 프로필 {@code prod}(프로필 그룹으로 {@code member}가 함께 켜짐)에서는 등록하지 않는다(결정 38-6). {@code prod}는 SMTP 발송기
 * ({@link SmtpMailConfiguration}, 결정 39-6)를 쓰며, 메일 설정이 없으면 기동하지 않는다.
 */
@Component
@Profile("(local | demo | member) & !prod")
public class FakeAuthMailSender implements AuthMailSender {

	static final int CAPACITY = 100;

	private final Deque<AuthMail> sent = new ArrayDeque<>();

	@Override
	public synchronized void send(AuthMail mail) {
		if (this.sent.size() == CAPACITY) {
			this.sent.removeFirst();
		}
		this.sent.addLast(mail);
	}

	/**
	 * @return 보낸 순서대로의 메일(최근 {@value #CAPACITY}통)
	 */
	public synchronized List<AuthMail> sentMails() {
		return List.copyOf(this.sent);
	}

	/** 담아 둔 메일을 비운다. 테스트 사이 격리용이다. */
	public synchronized void clear() {
		this.sent.clear();
	}

}
