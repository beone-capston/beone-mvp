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
 * <p>기대값: 익명 세션은 미사용 만료에만 끝나고 절대 만료가 없다(2026-11-18 사용자 직접 결정). 사용하면 미사용 만료가 늘고 쿠키 수명도
 * 그만큼 다시 정한다. 절대 만료가 있는 세션(회원 세션, U3)은 그 시각에도 끝나고 미사용 만료가 그것을 넘지 않는다(D-24). 시험 미사용
 * 수명은 1시간이며 시계는 서울 2026-11-18 10:00이다.
 */
class AuthSessionServiceTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 10, 0, 0);

	private static final AnonymousSessionProperties LIFETIME = new AnonymousSessionProperties(Duration.ofHours(1));

	private AuthSessionRepository repository;

	private AuthSessionService service;

	@BeforeEach
	void setUp() {
		this.repository = mock(AuthSessionRepository.class);
		this.service = new AuthSessionService(this.repository, LIFETIME, clockAt(NOW));
	}

	@Test
	void issueAnonymousStoresOnlyHashWithIdleExpiryAndNoAbsoluteExpiry() {
		IssuedSession issued = this.service.issueAnonymous();

		ArgumentCaptor<AuthSessionEntity> saved = ArgumentCaptor.forClass(AuthSessionEntity.class);
		verify(this.repository).save(saved.capture());
		AuthSessionEntity row = saved.getValue();
		assertThat(row.getSessionHash()).isEqualTo(SessionTokens.hash(issued.sessionId()))
			.isNotEqualTo(issued.sessionId());
		assertThat(row.getCreatedAt()).isEqualTo(NOW);
		assertThat(row.getLastUsedAt()).isEqualTo(NOW);
		assertThat(row.getIdleExpiresAt()).isEqualTo(NOW.plusHours(1));
		assertThat(row.getAbsoluteExpiresAt()).isNull();
		assertThat(issued.csrfToken()).isEqualTo(SessionTokens.csrfTokenOf(issued.sessionId()));
		assertThat(issued.cookieMaxAge()).isEqualTo(Duration.ofHours(1));
		assertThat(issued.toString()).doesNotContain(issued.sessionId()).doesNotContain(issued.csrfToken());
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
	void validUseExtendsIdleExpiryAndReturnsMatchingCookieMaxAge() {
		String id = SessionTokens.newSessionId();
		AuthSessionEntity row = row(id, NOW.minusMinutes(30), NOW.plusMinutes(30), null);
		given(this.repository.findBySessionHash(SessionTokens.hash(id))).willReturn(Optional.of(row));

		Optional<ActiveSession> active = this.service.use(id);

		assertThat(active).isPresent();
		assertThat(active.get().csrfToken()).isEqualTo(SessionTokens.csrfTokenOf(id));
		assertThat(row.getLastUsedAt()).isEqualTo(NOW);
		assertThat(row.getIdleExpiresAt()).isEqualTo(NOW.plusHours(1));
		// 쿠키 수명 = 이번 사용 시각부터 서버 미사용 만료까지.
		assertThat(active.get().cookieMaxAge()).isEqualTo(Duration.between(NOW, row.getIdleExpiresAt()));
		verify(this.repository, never()).delete(any());
	}

	@Test
	void useWithCsrfExtendsOnlyWhenTokenMatches() {
		String id = SessionTokens.newSessionId();
		AuthSessionEntity row = row(id, NOW.minusMinutes(30), NOW.plusMinutes(30), null);
		given(this.repository.findBySessionHash(SessionTokens.hash(id))).willReturn(Optional.of(row));

		// 토큰이 틀리거나 없으면 빈 값이고, 마지막 사용·미사용 만료가 그대로다.
		assertThat(this.service.useWithCsrf(id, SessionTokens.csrfTokenOf(SessionTokens.newSessionId()))).isEmpty();
		assertThat(this.service.useWithCsrf(id, null)).isEmpty();
		assertThat(row.getLastUsedAt()).isEqualTo(NOW.minusMinutes(30));
		assertThat(row.getIdleExpiresAt()).isEqualTo(NOW.plusMinutes(30));
		verify(this.repository, never()).delete(any());

		// 맞는 토큰이면 갱신하고 쿠키 수명을 돌려준다.
		Optional<ActiveSession> active = this.service.useWithCsrf(id, SessionTokens.csrfTokenOf(id));
		assertThat(active).isPresent();
		assertThat(row.getLastUsedAt()).isEqualTo(NOW);
		assertThat(row.getIdleExpiresAt()).isEqualTo(NOW.plusHours(1));
		assertThat(active.get().cookieMaxAge()).isEqualTo(Duration.ofHours(1));
	}

	@Test
	void useWithCsrfOnExpiredSessionDeletesItEvenWithMatchingToken() {
		String id = SessionTokens.newSessionId();
		AuthSessionEntity row = row(id, NOW.minusHours(1), NOW, null);
		given(this.repository.findBySessionHash(SessionTokens.hash(id))).willReturn(Optional.of(row));

		assertThat(this.service.useWithCsrf(id, SessionTokens.csrfTokenOf(id))).isEmpty();
		verify(this.repository).delete(row);
	}

	@Test
	void anonymousSessionCreatedLongAgoStaysValidWhileUsed() {
		String id = SessionTokens.newSessionId();
		// 발급 40일 전, 마지막 사용 30분 전. 절대 만료가 없으므로 계속 유효하다.
		AuthSessionEntity row = new AuthSessionEntity(SessionTokens.hash(id), NOW.minusDays(40), NOW.minusDays(40).plusHours(1),
				null);
		row.touch(NOW.minusMinutes(30), NOW.plusMinutes(30));
		given(this.repository.findBySessionHash(SessionTokens.hash(id))).willReturn(Optional.of(row));

		assertThat(this.service.use(id)).isPresent();
		assertThat(row.getIdleExpiresAt()).isEqualTo(NOW.plusHours(1));
	}

	@Test
	void sessionAtIdleExpiryIsExpiredAndDeleted() {
		String id = SessionTokens.newSessionId();
		AuthSessionEntity row = row(id, NOW.minusHours(1), NOW, null);
		given(this.repository.findBySessionHash(SessionTokens.hash(id))).willReturn(Optional.of(row));

		assertThat(this.service.use(id)).isEmpty();
		verify(this.repository).delete(row);
	}

	@Test
	void sessionOneSecondBeforeIdleExpiryIsStillValid() {
		String id = SessionTokens.newSessionId();
		AuthSessionEntity row = row(id, NOW.minusHours(1), NOW.plusSeconds(1), null);
		given(this.repository.findBySessionHash(SessionTokens.hash(id))).willReturn(Optional.of(row));

		assertThat(this.service.use(id)).isPresent();
	}

	@Test
	void sessionWithAbsoluteExpiryEndsThereAndCapsIdleExpiry() {
		String capped = SessionTokens.newSessionId();
		AuthSessionEntity cappedRow = row(capped, NOW.minusMinutes(10), NOW.plusMinutes(10), NOW.plusMinutes(30));
		given(this.repository.findBySessionHash(SessionTokens.hash(capped))).willReturn(Optional.of(cappedRow));

		Optional<ActiveSession> active = this.service.use(capped);

		assertThat(active).isPresent();
		assertThat(cappedRow.getIdleExpiresAt()).isEqualTo(NOW.plusMinutes(30));
		assertThat(active.get().cookieMaxAge()).isEqualTo(Duration.ofMinutes(30));

		String ended = SessionTokens.newSessionId();
		AuthSessionEntity endedRow = row(ended, NOW.minusMinutes(10), NOW, NOW);
		given(this.repository.findBySessionHash(SessionTokens.hash(ended))).willReturn(Optional.of(endedRow));

		assertThat(this.service.use(ended)).isEmpty();
		verify(this.repository).delete(endedRow);
	}

	@Test
	void sessionToStringHidesSessionId() {
		String id = SessionTokens.newSessionId();

		assertThat(new ActiveSession(id, Duration.ofHours(1)).toString()).doesNotContain(id);
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

	private static Clock clockAt(LocalDateTime seoulTime) {
		return Clock.fixed(seoulTime.atZone(SEOUL).toInstant(), ZoneOffset.UTC);
	}

	private static AuthSessionEntity row(String id, LocalDateTime lastUsed, LocalDateTime idleExpiry,
			LocalDateTime absoluteExpiry) {
		return new AuthSessionEntity(SessionTokens.hash(id), lastUsed, idleExpiry, absoluteExpiry);
	}

}
