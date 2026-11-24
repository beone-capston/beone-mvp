package com.beone.api.auth.application;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

import com.beone.api.auth.persistence.AuthTokenEntity;
import com.beone.api.auth.persistence.AuthTokenPurpose;
import com.beone.api.auth.persistence.AuthTokenRepository;
import com.beone.api.auth.persistence.MemberEntity;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 메일로 보내는 1회용 토큰의 발급(이메일 인증 U2, 비밀번호 재설정 U4).
 *
 * <p>같은 회원의 같은 목적 토큰을 먼저 지워 이전 토큰을 무효로 하고, 새 토큰의 SHA-256 해시만 저장한다. 메일은 토큰 저장이 커밋된
 * 뒤에만 보낸다. 커밋에 실패하면 쓸 수 없는 토큰이 메일로 나가지 않는다. 회원 행을 잠근 트랜잭션 안에서만 부른다.
 */
@Component
@Profile({ "local", "demo", "member" })
public class AuthTokenMailer {

	private final AuthTokenRepository tokens;

	private final AuthMailSender mailSender;

	AuthTokenMailer(AuthTokenRepository tokens, AuthMailSender mailSender) {
		this.tokens = Objects.requireNonNull(tokens, "tokens");
		this.mailSender = Objects.requireNonNull(mailSender, "mailSender");
	}

	/**
	 * 새 토큰을 발급하고 커밋 뒤 메일로 보낸다. 같은 목적의 이전 토큰은 무효가 된다.
	 * @param member 받는 회원
	 * @param purpose 토큰 목적
	 * @param ttl 유효 시간
	 * @param now 발급 시각(초 단위)
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void issue(MemberEntity member, AuthTokenPurpose purpose, Duration ttl, LocalDateTime now) {
		this.tokens.deleteByMemberIdAndPurpose(member.getMemberId(), purpose);
		String token = SessionTokens.newMailToken();
		this.tokens.save(new AuthTokenEntity(member.getMemberId(), purpose, SessionTokens.hash(token), now,
				now.plus(ttl)));
		AuthMail mail = new AuthMail(member.getEmail(), purpose, token);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				AuthTokenMailer.this.mailSender.send(mail);
			}
		});
	}

}
