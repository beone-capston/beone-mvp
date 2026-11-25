package com.beone.api.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import com.beone.api.auth.persistence.AuthSessionRepository;
import com.beone.api.auth.persistence.AuthTokenPurpose;
import com.beone.api.auth.persistence.AuthTokenRepository;
import com.beone.api.auth.persistence.MemberEntity;
import com.beone.api.auth.persistence.MemberRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 재설정 요청·완료(docs/api/auth.md U4, D-24).
 *
 * <p>요청은 계정 유무와 관계없이 반환값이 없고(컨트롤러가 같은 202로 답함), 계정이 있으면 인증 여부와 관계없이 재설정 메일을
 * 보낸다(관리자 판단). 메일 60초 제한은 가입·인증메일 재요청과 같은 주소 기준으로 합산한다. 완료는 회원 행을 잠근 한 트랜잭션에서
 * 비밀번호를 바꾸고 그 회원의 모든 세션과 재설정 토큰을 지운다. 로그인도 같은 회원 행을 잠그므로, 동시에 이전 비밀번호로 만든
 * 세션이 남지 않는다. 이메일 인증 상태·로그인 실패 잠금은 바꾸지 않고 자동 로그인하지 않는다. {@code local}·{@code demo}·{@code member} 프로필
 * 전용이다(결정 30).
 */
@Service
@Profile({ "local", "demo", "member" })
public class PasswordResetService {

	/** 비밀번호 재설정 토큰 유효 시간(사본). */
	static final Duration PASSWORD_RESET_TTL = Duration.ofMinutes(15);

	private final MemberRepository members;

	private final AuthTokenRepository tokens;

	private final AuthSessionRepository sessions;

	private final MailRequestThrottle throttle;

	private final PasswordHasher passwordHasher;

	private final AuthTokenMailer tokenMailer;

	private final Clock clock;

	@Autowired
	public PasswordResetService(MemberRepository members, AuthTokenRepository tokens, AuthSessionRepository sessions,
			MailRequestThrottle throttle, PasswordHasher passwordHasher, AuthTokenMailer tokenMailer,
			ObjectProvider<Clock> clock) {
		this(members, tokens, sessions, throttle, passwordHasher, tokenMailer, clock.getIfUnique(Clock::systemUTC));
	}

	PasswordResetService(MemberRepository members, AuthTokenRepository tokens, AuthSessionRepository sessions,
			MailRequestThrottle throttle, PasswordHasher passwordHasher, AuthTokenMailer tokenMailer, Clock clock) {
		this.members = Objects.requireNonNull(members, "members");
		this.tokens = Objects.requireNonNull(tokens, "tokens");
		this.sessions = Objects.requireNonNull(sessions, "sessions");
		this.throttle = Objects.requireNonNull(throttle, "throttle");
		this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
		this.tokenMailer = Objects.requireNonNull(tokenMailer, "tokenMailer");
		this.clock = Objects.requireNonNull(clock, "clock").withZone(AuthSessionService.ZONE);
	}

	/**
	 * 재설정 메일 발송을 접수한다. 계정이 있으면 새 재설정 토큰을 보내고 이전 재설정 토큰을 무효로 한다. 계정이 없으면 보내지 않는다.
	 * @param email 요청 이메일(형식 검사를 마친 값)
	 * @throws MailRateLimitedException 같은 주소 60초 안 재요청(가입·인증메일 재요청과 합산)
	 */
	@Transactional
	public void requestReset(String email) {
		String normalized = EmailAddresses.normalize(email);
		LocalDateTime now = now();
		this.throttle.acquire(normalized, now);
		this.members.findByEmail(normalized)
			.ifPresent(member -> this.tokenMailer.issue(member, AuthTokenPurpose.PASSWORD_RESET, PASSWORD_RESET_TTL,
					now));
	}

	/**
	 * 재설정 토큰으로 비밀번호를 바꾸고 그 회원의 모든 세션과 재설정 토큰을 무효로 한다.
	 * @param token 원문 토큰(43자 형식 검사를 마친 값)
	 * @param newPassword 원문 새 비밀번호(규칙 검사를 마친 값)
	 * @throws AuthTokenInvalidException 없음·만료·사용됨·목적 불일치
	 */
	@Transactional
	public void resetPassword(String token, String newPassword) {
		String tokenHash = SessionTokens.hash(token);
		// 잠금 순서는 회원 → 세션·토큰이다(로그인·이메일 인증과 같은 순서라 교착하지 않음). 회원 ID는 잠금 없이 읽고, 잠근 뒤 토큰을 다시 확인한다.
		String memberId = this.tokens.findMemberIdByTokenHash(tokenHash).orElseThrow(AuthTokenInvalidException::new);
		// 느린 해시 계산은 회원 행을 잠그기 전에 끝내 같은 회원의 로그인 대기를 줄인다.
		String newPasswordHash = this.passwordHasher.hash(newPassword);
		MemberEntity member = this.members.findLockedById(memberId).orElseThrow(AuthTokenInvalidException::new);
		LocalDateTime now = now();
		this.tokens.findByTokenHash(tokenHash)
			.filter(t -> t.getMemberId().equals(memberId))
			.filter(t -> t.getPurpose() == AuthTokenPurpose.PASSWORD_RESET)
			.filter(t -> !t.isExpiredAt(now))
			.orElseThrow(AuthTokenInvalidException::new);
		member.changePassword(newPasswordHash);
		this.members.flush();
		this.sessions.deleteAllByMemberId(memberId);
		this.tokens.deleteByMemberIdAndPurpose(memberId, AuthTokenPurpose.PASSWORD_RESET);
	}

	private LocalDateTime now() {
		return LocalDateTime.now(this.clock).truncatedTo(ChronoUnit.SECONDS);
	}

}
