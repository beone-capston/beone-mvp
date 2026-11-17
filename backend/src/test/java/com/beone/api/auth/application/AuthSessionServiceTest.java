package com.beone.api.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import com.beone.api.auth.persistence.AuthSessionEntity;
import com.beone.api.auth.persistence.AuthSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

/**
 * 세션 발급·조회의 만료 판정과 해시 저장을 확인한다.
 *
 * <p>기대값: 미사용 만료와 절대 만료 중 먼저 오는 시각에 끝나고, 사용하면 미사용 만료가 늘어나되 절대 만료를 넘지 않는다(D-24,
 * docs/api/auth.md 세션 조회의 {@code expiresAt} 설명). 시험 수명은 미사용 1시간·최대 3시간이며 시계는 서울 2026-11-18 10:00이다.
 */
class AuthSessionServiceTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 10, 0, 0);

	private static final AnonymousSessionProperties LIFETIME = new AnonymousSessionProperties(Duration.ofHours(1),
			Duration.ofHours(3));

	private AuthSessionRepository repository;

	private AuthSessionService service;

	@BeforeEach
	void setUp() {
		this.repository = mock(AuthSessionRepository.class);
		Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), ZoneOffset.UTC);
		this.service = new AuthSessionService(this.repository, LIFETIME, clock);
	}

	@Test
	void issueAnonymousStoresOnlyHashWithLifetimes() {
		IssuedSession issued = this.service.issueAnonymous();

		ArgumentCaptor<AuthSessionEntity> saved = ArgumentCaptor.forClass(AuthSessionEntity.class);
		verify(this.repository).save(saved.capture());
		AuthSessionEntity row = saved.getValue();
		assertThat(row.getSessionHash()).isEqualTo(SessionTokens.hash(issued.sessionId()))
			.isNotEqualTo(issued.sessionId());
		assertThat(row.getCreatedAt()).isEqualTo(NOW);
		assertThat(row.getLastUsedAt()).isEqualTo(NOW);
		assertThat(row.getIdleExpiresAt()).isEqualTo(NOW.plusHours(1));
		assertThat(row.getAbsoluteExpiresAt()).isEqualTo(NOW.plusHours(3));
		assertThat(issued.csrfToken()).isEqualTo(SessionTokens.csrfTokenOf(issued.sessionId()));
		assertThat(issued.cookieMaxAge()).isEqualTo(Duration.ofHours(3));
		assertThat(issued.toString()).doesNotContain(issued.sessionId()).doesNotContain(issued.csrfToken());
	}

	@Test
	void idleExpiryNeverExceedsAbsoluteExpiryOnIssue() {
		AuthSessionService equalLifetimes = new AuthSessionService(this.repository,
				new AnonymousSessionProperties(Duration.ofHours(2), Duration.ofHours(2)),
				Clock.fixed(NOW.atZone(SEOUL).toInstant(), ZoneOffset.UTC));

		equalLifetimes.issueAnonymous();

		ArgumentCaptor<AuthSessionEntity> saved = ArgumentCaptor.forClass(AuthSessionEntity.class);
		verify(this.repository).save(saved.capture());
		assertThat(saved.getValue().getIdleExpiresAt()).isEqualTo(saved.getValue().getAbsoluteExpiresAt());
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "short", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa=", "한글한글" })
	void malformedCookieIsRejectedWithoutLookup(String cookie) {
		assertThat(this.service.use(cookie)).isEmpty();
		verifyNoInteractions(this.repository);
	}

	@Test
	void unknownSessionIsEmpty() {
		given(this.repository.findBySessionHash(anyString())).willReturn(Optional.empty());

		assertThat(this.service.use(SessionTokens.newSessionId())).isEmpty();
	}

	@Test
	void validSessionIsTouchedAndIdleExpiryExtended() {
		String id = SessionTokens.newSessionId();
		AuthSessionEntity row = row(id, NOW.minusMinutes(30), NOW.plusMinutes(30), NOW.plusHours(2));
		given(this.repository.findBySessionHash(SessionTokens.hash(id))).willReturn(Optional.of(row));

		Optional<ActiveSession> active = this.service.use(id);

		assertThat(active).isPresent();
		assertThat(active.get().csrfToken()).isEqualTo(SessionTokens.csrfTokenOf(id));
		assertThat(row.getLastUsedAt()).isEqualTo(NOW);
		assertThat(row.getIdleExpiresAt()).isEqualTo(NOW.plusHours(1));
		verify(this.repository, never()).delete(any());
	}

	@Test
	void touchCapsIdleExpiryAtAbsoluteExpiry() {
		String id = SessionTokens.newSessionId();
		AuthSessionEntity row = row(id, NOW.minusHours(2).minusMinutes(30), NOW.plusMinutes(10), NOW.plusMinutes(30));
		given(this.repository.findBySessionHash(SessionTokens.hash(id))).willReturn(Optional.of(row));

		assertThat(this.service.use(id)).isPresent();
		assertThat(row.getIdleExpiresAt()).isEqualTo(NOW.plusMinutes(30));
	}

	@Test
	void sessionAtIdleExpiryIsExpiredAndDeleted() {
		String id = SessionTokens.newSessionId();
		AuthSessionEntity row = row(id, NOW.minusHours(1), NOW, NOW.plusHours(2));
		given(this.repository.findBySessionHash(SessionTokens.hash(id))).willReturn(Optional.of(row));

		assertThat(this.service.use(id)).isEmpty();
		verify(this.repository).delete(row);
	}

	@Test
	void sessionAtAbsoluteExpiryIsExpiredEvenIfRecentlyUsed() {
		String id = SessionTokens.newSessionId();
		AuthSessionEntity row = row(id, NOW.minusHours(3), NOW, NOW);
		given(this.repository.findBySessionHash(SessionTokens.hash(id))).willReturn(Optional.of(row));

		assertThat(this.service.use(id)).isEmpty();
		verify(this.repository).delete(row);
	}

	@Test
	void sessionOneSecondBeforeExpiryIsStillValid() {
		String id = SessionTokens.newSessionId();
		AuthSessionEntity row = row(id, NOW.minusHours(1), NOW.plusSeconds(1), NOW.plusSeconds(1));
		given(this.repository.findBySessionHash(SessionTokens.hash(id))).willReturn(Optional.of(row));

		assertThat(this.service.use(id)).isPresent();
	}

	@Test
	void activeSessionToStringHidesSessionId() {
		String id = SessionTokens.newSessionId();

		assertThat(new ActiveSession(id).toString()).doesNotContain(id);
	}

	@Test
	void storedTimesAreSeoulLocalTime() {
		AuthSessionService utcClock = new AuthSessionService(this.repository, LIFETIME,
				Clock.fixed(Instant.parse("2026-11-18T01:00:00Z"), ZoneOffset.UTC));
		utcClock.issueAnonymous();

		ArgumentCaptor<AuthSessionEntity> saved = ArgumentCaptor.forClass(AuthSessionEntity.class);
		verify(this.repository).save(saved.capture());
		// UTC 01:00은 서울 10:00이다(시간 값은 서울 기준 시각으로 저장).
		assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
	}

	private static AuthSessionEntity row(String id, LocalDateTime lastUsed, LocalDateTime idleExpiry,
			LocalDateTime absoluteExpiry) {
		AuthSessionEntity row = new AuthSessionEntity(SessionTokens.hash(id), lastUsed, idleExpiry, absoluteExpiry);
		return row;
	}

}
