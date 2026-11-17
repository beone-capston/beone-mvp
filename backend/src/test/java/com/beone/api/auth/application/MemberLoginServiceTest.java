package com.beone.api.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import com.beone.api.auth.persistence.MemberEntity;
import com.beone.api.auth.persistence.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.support.TransactionOperations;

/**
 * 로그인 판정 순서와 계정 존재 비공개를 확인한다.
 *
 * <p>기대값(docs/api/auth.md): 잠겨 있으면 비밀번호를 보지 않고 429, 계정 없음과 비밀번호 불일치는 같은 401이며 둘 다 실패로 센다,
 * 미인증 오류는 비밀번호가 맞은 뒤에만 주고 실패로 세지 않는다, 성공하면 실패 기록을 지우고 이전 세션을 넘겨 새 세션을 만든다. 계정이
 * 없어도 Argon2id 비교를 한 번 한다(응답 시간 차이 줄이기). 시계는 서울 2026-11-18 10:00이다.
 */
class MemberLoginServiceTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 10, 0, 0);

	private static final String PREVIOUS = SessionTokens.newSessionId();

	private MemberRepository members;

	private LoginAttemptThrottle throttle;

	private PasswordHasher hasher;

	private AuthSessionService sessions;

	private MemberLoginService service;

	@BeforeEach
	void setUp() {
		this.members = mock(MemberRepository.class);
		this.throttle = mock(LoginAttemptThrottle.class);
		this.hasher = mock(PasswordHasher.class);
		this.sessions = mock(AuthSessionService.class);
		given(this.hasher.hash(anyString())).willReturn("dummy-hash");
		given(this.throttle.lockedFor(anyString(), any())).willReturn(Optional.empty());
		Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneOffset.UTC);
		this.service = new MemberLoginService(this.members, this.throttle, this.hasher, this.sessions, clock,
				TransactionOperations.withoutTransaction());
	}

	@Test
	void lockedEmailIsRateLimitedWithoutCheckingPassword() {
		given(this.throttle.lockedFor("user@example.com", NOW)).willReturn(Optional.of(Duration.ofSeconds(300)));

		LoginResult result = this.service.login(" User@Example.com ", "pw", PREVIOUS);

		assertThat(result).isEqualTo(new LoginResult.RateLimited(Duration.ofSeconds(300)));
		verify(this.hasher, never()).matches(any(), any());
		verify(this.throttle, never()).recordFailure(any(), any());
	}

	@Test
	void missingAccountStillComparesAgainstDummyHashAndCountsFailure() {
		given(this.members.findByEmail("nobody@example.com")).willReturn(Optional.empty());

		LoginResult result = this.service.login("nobody@example.com", "pw", PREVIOUS);

		assertThat(result).isEqualTo(new LoginResult.InvalidCredentials());
		verify(this.hasher).matches("pw", "dummy-hash");
		verify(this.throttle).recordFailure("nobody@example.com", NOW);
		verify(this.sessions, never()).issueMember(any(), any());
	}

	@Test
	void wrongPasswordIsTheSameResultAndCountsFailure() {
		given(this.members.findByEmail("user@example.com")).willReturn(Optional.of(member(true)));
		given(this.hasher.matches("wrong", "stored-hash")).willReturn(false);

		assertThat(this.service.login("user@example.com", "wrong", PREVIOUS))
			.isEqualTo(new LoginResult.InvalidCredentials());
		verify(this.throttle).recordFailure("user@example.com", NOW);
	}

	@Test
	void unverifiedEmailIsReportedOnlyAfterCorrectPasswordAndIsNotAFailure() {
		given(this.members.findByEmail("user@example.com")).willReturn(Optional.of(member(false)));
		given(this.hasher.matches("right", "stored-hash")).willReturn(true);

		assertThat(this.service.login("user@example.com", "right", PREVIOUS)).isEqualTo(new LoginResult.EmailNotVerified());
		verify(this.throttle, never()).recordFailure(any(), any());
		verify(this.throttle, never()).recordSuccess(any());
		verify(this.sessions, never()).issueMember(any(), any());
	}

	@Test
	void successClearsFailuresAndIssuesMemberSessionReplacingPrevious() {
		MemberEntity member = member(true);
		given(this.members.findByEmail("user@example.com")).willReturn(Optional.of(member));
		given(this.hasher.matches("right", "stored-hash")).willReturn(true);
		IssuedSession issued = new IssuedSession(SessionTokens.newSessionId(), "csrf", Duration.ofDays(30));
		given(this.sessions.issueMember(member.getMemberId(), PREVIOUS)).willReturn(issued);

		LoginResult result = this.service.login("USER@example.com", "right", PREVIOUS);

		assertThat(result).isEqualTo(new LoginResult.Succeeded(member.getMemberId(), "user@example.com", issued));
		verify(this.throttle).recordSuccess("user@example.com");
		assertThat(result.toString()).doesNotContain("user@example.com").doesNotContain(issued.sessionId());
	}

	@Test
	void lockConflictIsRetriedInANewAttemptUpToThreeTimes() {
		MemberEntity member = member(true);
		given(this.members.findByEmail("user@example.com"))
			.willThrow(new CannotAcquireLockException("deadlock"))
			.willThrow(new CannotAcquireLockException("deadlock"))
			.willReturn(Optional.of(member));
		given(this.hasher.matches("right", "stored-hash")).willReturn(true);
		IssuedSession issued = new IssuedSession(SessionTokens.newSessionId(), "csrf", Duration.ofDays(30));
		given(this.sessions.issueMember(member.getMemberId(), PREVIOUS)).willReturn(issued);

		assertThat(this.service.login("user@example.com", "right", PREVIOUS)).isInstanceOf(LoginResult.Succeeded.class);
		verify(this.throttle, times(3)).lock("user@example.com");
	}

	@Test
	void lockConflictOnTheLastAttemptIsRethrown() {
		given(this.members.findByEmail("user@example.com")).willThrow(new CannotAcquireLockException("deadlock"));

		assertThatThrownBy(() -> this.service.login("user@example.com", "pw", PREVIOUS))
			.isInstanceOf(CannotAcquireLockException.class);
		verify(this.throttle, times(MemberLoginService.MAX_ATTEMPTS)).lock("user@example.com");
	}

	@Test
	void timesAreReadAfterTheThrottleLockAndAfterPasswordComparison() {
		// 시계를 읽을 때마다 1초씩 간다. 잠금 판정은 잠금 행을 잡은 뒤의 시각, 실패 기록은 비교 뒤의 시각을 받아야 한다.
		Clock ticking = new Clock() {
			private int reads;

			@Override
			public ZoneId getZone() {
				return ZoneId.of("Asia/Seoul");
			}

			@Override
			public Clock withZone(ZoneId zone) {
				return this;
			}

			@Override
			public Instant instant() {
				return NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant().plusSeconds(this.reads++);
			}
		};
		MemberLoginService service = new MemberLoginService(this.members, this.throttle, this.hasher, this.sessions,
				ticking, TransactionOperations.withoutTransaction());
		given(this.members.findByEmail("nobody@example.com")).willReturn(Optional.empty());

		service.login("nobody@example.com", "pw", PREVIOUS);

		InOrder order = inOrder(this.throttle, this.hasher);
		order.verify(this.throttle).lock("nobody@example.com");
		order.verify(this.throttle).lockedFor("nobody@example.com", NOW);
		order.verify(this.hasher).matches("pw", "dummy-hash");
		order.verify(this.throttle).recordFailure("nobody@example.com", NOW.plusSeconds(1));
	}

	@Test
	void dummyHashIsMadeFromARandomValueAtStartup() {
		ArgumentCaptor<String> secret = ArgumentCaptor.forClass(String.class);
		verify(this.hasher).hash(secret.capture());
		assertThat(secret.getValue()).matches("[A-Za-z0-9_-]{43}");
	}

	private static MemberEntity member(boolean verified) {
		MemberEntity member = new MemberEntity("usr-0123456789abcdef0123456789abcdef", "user@example.com",
				"stored-hash", NOW.minusDays(1));
		if (verified) {
			member.markEmailVerified(NOW.minusDays(1));
		}
		return member;
	}

}
