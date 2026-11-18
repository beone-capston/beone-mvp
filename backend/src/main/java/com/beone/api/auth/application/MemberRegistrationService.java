package com.beone.api.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

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
 * 회원가입·인증메일 재요청·이메일 인증 완료(docs/api/auth.md U2, D-24).
 *
 * <p>계정 존재·인증 상태를 결과로 드러내지 않는다. 가입과 재요청은 어떤 경우든 반환값이 없고(컨트롤러가 같은 202로 답함), 실제 발송
 * 여부만 다르다. 가입은 기존 계정일 때도 비밀번호 해시를 계산해 응답 시간 차이를 줄인다. 원문 비밀번호·토큰은 로그에 남기지 않는다.
 * {@code local}·{@code demo} 프로필 전용이다(관리자 승인, 결정 30).
 */
@Service
@Profile({ "local", "demo" })
public class MemberRegistrationService {

	/** 이메일 인증 토큰 유효 시간(사본). */
	static final Duration EMAIL_VERIFICATION_TTL = Duration.ofMinutes(30);

	private final MemberRepository members;

	private final AuthTokenRepository tokens;

	private final MailRequestThrottle throttle;

	private final PasswordHasher passwordHasher;

	private final AuthTokenMailer tokenMailer;

	private final Clock clock;

	@Autowired
	public MemberRegistrationService(MemberRepository members, AuthTokenRepository tokens, MailRequestThrottle throttle,
			PasswordHasher passwordHasher, AuthTokenMailer tokenMailer, ObjectProvider<Clock> clock) {
		this(members, tokens, throttle, passwordHasher, tokenMailer, clock.getIfUnique(Clock::systemUTC));
	}

	MemberRegistrationService(MemberRepository members, AuthTokenRepository tokens, MailRequestThrottle throttle,
			PasswordHasher passwordHasher, AuthTokenMailer tokenMailer, Clock clock) {
		this.members = Objects.requireNonNull(members, "members");
		this.tokens = Objects.requireNonNull(tokens, "tokens");
		this.throttle = Objects.requireNonNull(throttle, "throttle");
		this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
		this.tokenMailer = Objects.requireNonNull(tokenMailer, "tokenMailer");
		this.clock = Objects.requireNonNull(clock, "clock").withZone(AuthSessionService.ZONE);
	}

	/**
	 * 가입을 접수한다. 신규면 미인증 회원을 만들고 인증메일을 보낸다. 미인증 기존 계정이면 비밀번호를 바꾸지 않고 인증메일만 다시
	 * 보낸다(이전 토큰 무효). 인증된 계정이면 아무것도 보내지 않는다.
	 * @param email 요청 이메일(형식 검사를 마친 값)
	 * @param password 원문 비밀번호(규칙 검사를 마친 값)
	 * @throws MailRateLimitedException 같은 주소 60초 안 재요청
	 */
	@Transactional
	public void signUp(String email, String password) {
		String normalized = EmailAddresses.normalize(email);
		LocalDateTime now = now();
		this.throttle.acquire(normalized, now);
		String passwordHash = this.passwordHasher.hash(password);
		Optional<MemberEntity> existing = this.members.findByEmail(normalized);
		if (existing.isEmpty()) {
			MemberEntity member = this.members.save(new MemberEntity(MemberIds.newId(), normalized, passwordHash, now));
			sendVerificationMail(member, now);
			return;
		}
		if (!existing.get().isEmailVerified()) {
			sendVerificationMail(existing.get(), now);
		}
	}

	/**
	 * 인증메일 재발송을 접수한다. 미인증 계정이면 새 토큰을 보내고 이전 토큰을 무효로 한다. 계정 없음·이미 인증됨이면 보내지 않는다.
	 * @param email 요청 이메일(형식 검사를 마친 값)
	 * @throws MailRateLimitedException 같은 주소 60초 안 재요청
	 */
	@Transactional
	public void requestVerificationEmail(String email) {
		String normalized = EmailAddresses.normalize(email);
		LocalDateTime now = now();
		this.throttle.acquire(normalized, now);
		this.members.findByEmail(normalized)
			.filter(member -> !member.isEmailVerified())
			.ifPresent(member -> sendVerificationMail(member, now));
	}

	/**
	 * 이메일 인증 토큰으로 이메일 소유를 확인한다. 인증 상태만 바꾸고 비밀번호·세션은 건드리지 않는다. 그 회원의 인증 토큰은 모두
	 * 무효가 된다.
	 * @param token 원문 토큰(43자 형식 검사를 마친 값)
	 * @throws AuthTokenInvalidException 없음·만료·사용됨·목적 불일치
	 */
	@Transactional
	public void verifyEmail(String token) {
		LocalDateTime now = now();
		String tokenHash = SessionTokens.hash(token);
		// 잠금 순서는 회원 → 토큰이다(재발급과 같은 순서라 교착하지 않음). 회원 ID는 잠금 없이 읽고, 잠근 뒤 토큰을 다시 확인한다.
		String memberId = this.tokens.findMemberIdByTokenHash(tokenHash).orElseThrow(AuthTokenInvalidException::new);
		MemberEntity member = this.members.findLockedById(memberId).orElseThrow(AuthTokenInvalidException::new);
		this.tokens.findByTokenHash(tokenHash)
			.filter(t -> t.getMemberId().equals(memberId))
			.filter(t -> t.getPurpose() == AuthTokenPurpose.EMAIL_VERIFICATION)
			.filter(t -> !t.isExpiredAt(now))
			.orElseThrow(AuthTokenInvalidException::new);
		member.markEmailVerified(now);
		this.tokens.deleteByMemberIdAndPurpose(member.getMemberId(), AuthTokenPurpose.EMAIL_VERIFICATION);
	}

	private void sendVerificationMail(MemberEntity member, LocalDateTime now) {
		this.tokenMailer.issue(member, AuthTokenPurpose.EMAIL_VERIFICATION, EMAIL_VERIFICATION_TTL, now);
	}

	private LocalDateTime now() {
		return LocalDateTime.now(this.clock).truncatedTo(ChronoUnit.SECONDS);
	}

}
