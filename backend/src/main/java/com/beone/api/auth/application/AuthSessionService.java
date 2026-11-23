package com.beone.api.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.beone.api.auth.persistence.AuthSessionEntity;
import com.beone.api.auth.persistence.AuthSessionRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 서버 세션의 발급·조회(D-24, docs/api/auth.md). 세션은 MySQL {@code auth_sessions}에 해시로 저장한다.
 *
 * <p>세션은 미사용 만료에 끝나고, 절대 만료가 있으면 그 시각에도 끝난다. 익명 세션은 절대 만료가 없다(2026-11-18 사용자 직접 결정).
 * 유효한 세션을 쓸 때마다 미사용 만료를 늘리되 절대 만료가 있으면 넘기지 않는다. 만료한 세션은 찾을 때 지운다. {@code local}·{@code demo} 프로필 전용이다(관리자 승인, 결정 30).
 *
 * <p>회원 로그인 세션(U3)은 미사용 7일, 로그인 뒤 최대 30일이고 회원당 동시 5개다(D-24). 로그인하면 요청의 이전 세션(익명이든 회원이든)을
 * 지우고 새 세션 ID를 발급해 세션 고정을 막는다. 6번째 세션을 만들면 가장 오래 쓰지 않은 세션을 지운다(docs/api/auth.md).
 */
@Service
@Profile({ "local", "demo" })
public class AuthSessionService {

	/** 업무 API 기준 시간대(docs/api.md 값 형식). */
	static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	/** 회원 세션 미사용 만료(D-24). */
	public static final Duration MEMBER_IDLE_TIMEOUT = Duration.ofDays(7);

	/** 회원 세션 절대 만료: 로그인 뒤 30일(D-24). 로그인 응답 쿠키의 {@code Max-Age}이기도 하다(docs/api/auth.md). */
	public static final Duration MEMBER_ABSOLUTE_TIMEOUT = Duration.ofDays(30);

	/** 회원당 동시 세션 수 상한(D-24). 익명 세션은 세지 않는다. */
	public static final int MAX_MEMBER_SESSIONS = 5;

	private final AuthSessionRepository repository;

	private final AnonymousSessionProperties anonymousSession;

	private final Clock clock;

	/**
	 * {@link Clock} 빈이 하나 있으면 그것을, 없으면 시스템 시계를 쓴다.
	 * @param repository 세션 저장소
	 * @param anonymousSession 익명 세션 수명
	 * @param clock 시계 빈
	 */
	@Autowired
	public AuthSessionService(AuthSessionRepository repository, AnonymousSessionProperties anonymousSession,
			ObjectProvider<Clock> clock) {
		this(repository, anonymousSession, clock.getIfUnique(Clock::systemUTC));
	}

	AuthSessionService(AuthSessionRepository repository, AnonymousSessionProperties anonymousSession, Clock clock) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.anonymousSession = Objects.requireNonNull(anonymousSession, "anonymousSession");
		this.clock = Objects.requireNonNull(clock, "clock").withZone(ZONE);
	}

	/**
	 * 쿠키 값으로 유효한 세션을 찾고 사용 시각을 갱신한다.
	 * @param sessionId 요청 쿠키의 세션 ID, {@code null} 가능
	 * @return 유효한 세션과 새 쿠키 수명. 쿠키 없음·형식 오류·없는 세션·만료는 빈 값
	 */
	@Transactional
	public Optional<ActiveSession> use(String sessionId) {
		return use(sessionId, null, false);
	}

	/**
	 * 인증 POST용: 쿠키 값으로 유효한 세션을 찾고, CSRF 토큰이 맞을 때만 사용 시각을 갱신한다. 조회·검사·갱신을 한 트랜잭션(행 쓰기
	 * 잠금)에서 한다. 토큰이 틀리면 서버 만료를 늘리지 않는다.
	 * @param sessionId 요청 쿠키의 세션 ID, {@code null} 가능
	 * @param presentedToken 요청 헤더의 CSRF 토큰, {@code null} 가능
	 * @return 검사를 통과한 세션과 새 쿠키 수명. 쿠키 없음·형식 오류·없는 세션·만료·토큰 불일치는 빈 값
	 */
	@Transactional
	public Optional<ActiveSession> useWithCsrf(String sessionId, String presentedToken) {
		return use(sessionId, presentedToken, true);
	}

	private Optional<ActiveSession> use(String sessionId, String presentedToken, boolean checkCsrf) {
		if (!SessionTokens.isWellFormed(sessionId)) {
			return Optional.empty();
		}
		Optional<AuthSessionEntity> found = this.repository.findBySessionHash(SessionTokens.hash(sessionId));
		if (found.isEmpty()) {
			return Optional.empty();
		}
		AuthSessionEntity session = found.get();
		LocalDateTime now = now();
		if (isExpired(session, now)) {
			this.repository.delete(session);
			return Optional.empty();
		}
		if (checkCsrf && !SessionTokens.csrfMatches(sessionId, presentedToken)) {
			return Optional.empty();
		}
		LocalDateTime idleExpiry = idleExpiry(now, session);
		session.touch(now, idleExpiry);
		return Optional.of(new ActiveSession(sessionId, Duration.between(now, idleExpiry)));
	}

	/**
	 * 쿠키 값으로 유효한 회원 세션을 찾고 사용 시각을 갱신한다. 익명 세션은 회원 세션이 아니므로 갱신하지 않고 빈 값이다.
	 * @param sessionId 요청 쿠키의 세션 ID, {@code null} 가능
	 * @return 회원 세션. 쿠키 없음·형식 오류·없는 세션·만료·익명 세션은 빈 값
	 */
	@Transactional
	public Optional<MemberSession> useMember(String sessionId) {
		if (!SessionTokens.isWellFormed(sessionId)) {
			return Optional.empty();
		}
		Optional<AuthSessionEntity> found = this.repository.findBySessionHash(SessionTokens.hash(sessionId));
		if (found.isEmpty()) {
			return Optional.empty();
		}
		AuthSessionEntity session = found.get();
		LocalDateTime now = now();
		if (isExpired(session, now)) {
			this.repository.delete(session);
			return Optional.empty();
		}
		if (!session.isMemberSession()) {
			return Optional.empty();
		}
		session.touch(now, idleExpiry(now, session));
		return Optional.of(new MemberSession(session.getMemberId(), session.getCreatedAt(), session.getIdleExpiresAt(),
				session.getAbsoluteExpiresAt()));
	}

	/**
	 * 회원 로그인 세션을 새로 발급한다. 요청의 이전 세션은 지우고(세션 고정 방지), 그 회원의 만료된 세션도 지우며, 유효한 세션이 이미
	 * 상한이면 가장 오래 쓰지 않은 세션부터 지운다. 회원 행을 먼저 잠근 트랜잭션 안에서 불러야 동시 로그인이 상한을 넘지 않는다.
	 * @param memberId 회원 ID
	 * @param previousSessionId 요청 쿠키의 이전 세션 ID, {@code null} 가능
	 * @return 새 세션(쿠키 값·CSRF 토큰·쿠키 수명 30일)
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public IssuedSession issueMember(String memberId, String previousSessionId) {
		Objects.requireNonNull(memberId, "memberId");
		end(previousSessionId);
		LocalDateTime now = now();
		List<AuthSessionEntity> live = new ArrayList<>();
		for (AuthSessionEntity session : this.repository.findByMemberIdOrderByLastUsedAtAscAuthSessionIdAsc(memberId)) {
			if (isExpired(session, now)) {
				this.repository.delete(session);
			}
			else {
				live.add(session);
			}
		}
		while (live.size() >= MAX_MEMBER_SESSIONS) {
			this.repository.delete(live.remove(0));
		}
		String sessionId = SessionTokens.newSessionId();
		LocalDateTime absolute = now.plus(MEMBER_ABSOLUTE_TIMEOUT);
		this.repository.save(new AuthSessionEntity(SessionTokens.hash(sessionId), memberId, now,
				now.plus(MEMBER_IDLE_TIMEOUT), absolute));
		return new IssuedSession(sessionId, SessionTokens.csrfTokenOf(sessionId), MEMBER_ABSOLUTE_TIMEOUT);
	}

	/**
	 * 쿠키의 세션을 끝낸다(익명이든 회원이든). 없거나 형식이 틀리면 아무것도 하지 않는다.
	 * @param sessionId 요청 쿠키의 세션 ID, {@code null} 가능
	 */
	@Transactional
	public void end(String sessionId) {
		if (!SessionTokens.isWellFormed(sessionId)) {
			return;
		}
		this.repository.findBySessionHash(SessionTokens.hash(sessionId)).ifPresent(this.repository::delete);
	}

	/**
	 * 회원이 아닌 익명 세션을 새로 발급한다. CSRF 토큰을 받기 위한 세션이며 회원 로그인을 뜻하지 않는다.
	 * @return 발급한 세션(쿠키 값·CSRF 토큰·쿠키 수명)
	 */
	@Transactional
	public IssuedSession issueAnonymous() {
		LocalDateTime now = now();
		String sessionId = SessionTokens.newSessionId();
		this.repository.save(new AuthSessionEntity(SessionTokens.hash(sessionId), now,
				now.plus(this.anonymousSession.idleTimeout()), null));
		return new IssuedSession(sessionId, SessionTokens.csrfTokenOf(sessionId), this.anonymousSession.idleTimeout());
	}

	/** 미사용 만료나 (있으면) 절대 만료가 지났으면(같은 시각 포함) 만료다. */
	static boolean isExpired(AuthSessionEntity session, LocalDateTime now) {
		LocalDateTime absolute = session.getAbsoluteExpiresAt();
		return !now.isBefore(session.getIdleExpiresAt()) || (absolute != null && !now.isBefore(absolute));
	}

	/** 회원 세션은 7일, 익명 세션은 설정값만큼 늘리되 절대 만료가 있으면 넘기지 않는다. */
	private LocalDateTime idleExpiry(LocalDateTime now, AuthSessionEntity session) {
		LocalDateTime absoluteExpiry = session.getAbsoluteExpiresAt();
		Duration timeout = session.isMemberSession() ? MEMBER_IDLE_TIMEOUT : this.anonymousSession.idleTimeout();
		LocalDateTime idle = now.plus(timeout);
		return (absoluteExpiry != null && idle.isAfter(absoluteExpiry)) ? absoluteExpiry : idle;
	}

	private LocalDateTime now() {
		return LocalDateTime.now(this.clock).truncatedTo(ChronoUnit.SECONDS);
	}

}
