package com.beone.api.auth.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

import com.beone.api.auth.persistence.AuthSessionEntity;
import com.beone.api.auth.persistence.AuthSessionRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 서버 세션의 발급·조회(D-24, docs/api/auth.md). 세션은 MySQL {@code auth_sessions}에 해시로 저장한다.
 *
 * <p>세션은 미사용 만료와 절대 만료 중 먼저 오는 시각에 끝난다. 유효한 세션을 쓸 때마다 미사용 만료를 늘리되 절대 만료를 넘기지
 * 않는다. 만료한 세션은 찾을 때 지운다. {@code local}·{@code demo} 프로필 전용이다(관리자 승인, 결정 30).
 */
@Service
@Profile({ "local", "demo" })
public class AuthSessionService {

	/** 업무 API 기준 시간대(docs/api.md 값 형식). */
	static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

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
	 * @return 유효한 세션. 쿠키 없음·형식 오류·없는 세션·만료는 빈 값
	 */
	@Transactional
	public Optional<ActiveSession> use(String sessionId) {
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
		session.touch(now, idleExpiry(now, session.getAbsoluteExpiresAt()));
		return Optional.of(new ActiveSession(sessionId));
	}

	/**
	 * 회원이 아닌 익명 세션을 새로 발급한다. CSRF 토큰을 받기 위한 세션이며 회원 로그인을 뜻하지 않는다.
	 * @return 발급한 세션(쿠키 값·CSRF 토큰·쿠키 수명)
	 */
	@Transactional
	public IssuedSession issueAnonymous() {
		LocalDateTime now = now();
		LocalDateTime absoluteExpiry = now.plus(this.anonymousSession.absoluteTimeout());
		String sessionId = SessionTokens.newSessionId();
		this.repository.save(new AuthSessionEntity(SessionTokens.hash(sessionId), now, idleExpiry(now, absoluteExpiry),
				absoluteExpiry));
		return new IssuedSession(sessionId, SessionTokens.csrfTokenOf(sessionId), this.anonymousSession.absoluteTimeout());
	}

	/** 미사용 만료와 절대 만료 중 하나라도 지났으면(같은 시각 포함) 만료다. */
	static boolean isExpired(AuthSessionEntity session, LocalDateTime now) {
		return !now.isBefore(session.getIdleExpiresAt()) || !now.isBefore(session.getAbsoluteExpiresAt());
	}

	private LocalDateTime idleExpiry(LocalDateTime now, LocalDateTime absoluteExpiry) {
		LocalDateTime idle = now.plus(this.anonymousSession.idleTimeout());
		return idle.isAfter(absoluteExpiry) ? absoluteExpiry : idle;
	}

	private LocalDateTime now() {
		return LocalDateTime.now(this.clock).truncatedTo(ChronoUnit.SECONDS);
	}

}
