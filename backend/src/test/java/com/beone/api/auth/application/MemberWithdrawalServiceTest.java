package com.beone.api.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import com.beone.api.auth.persistence.MemberEntity;
import com.beone.api.auth.persistence.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionOperations;

/**
 * 회원 탈퇴 판정 순서(결정 D10, 관리자 T5 지시)를 확인한다. 잠금 순서는 로그인과 같게 로그인 잠금 행 → 회원 행이고, 삭제는 업무 장부 이벤트
 * → 로그인 실패 기록 삭제 → 회원 행 삭제 순서다.
 */
class MemberWithdrawalServiceTest {

	private static final String MEMBER = "usr-0123456789abcdef0123456789abcdef";

	private static final String EMAIL = "user@example.com";

	// 서울 2026-11-18 10:00
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-18T01:00:00Z"), ZoneOffset.UTC);

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 10, 0);

	private MemberRepository members;

	private LoginAttemptThrottle throttle;

	private PasswordHasher hasher;

	private ApplicationEventPublisher events;

	private MemberWithdrawalService service;

	private MemberEntity member;

	@BeforeEach
	void setUp() {
		this.members = mock(MemberRepository.class);
		this.throttle = mock(LoginAttemptThrottle.class);
		this.hasher = mock(PasswordHasher.class);
		this.events = mock(ApplicationEventPublisher.class);
		this.service = new MemberWithdrawalService(this.members, this.throttle, this.hasher, this.events, CLOCK,
				TransactionOperations.withoutTransaction());
		this.member = new MemberEntity(MEMBER, EMAIL, "stored-hash", NOW);
		given(this.members.findById(MEMBER)).willReturn(Optional.of(this.member));
		given(this.members.findLockedById(MEMBER)).willReturn(Optional.of(this.member));
		given(this.throttle.lockedFor(EMAIL, NOW)).willReturn(Optional.empty());
	}

	@Test
	void correctPasswordErasesInOrder() {
		given(this.hasher.matches("pw", "stored-hash")).willReturn(true);

		assertThat(this.service.withdraw(MEMBER, "pw")).isEqualTo(new WithdrawalResult.Withdrawn());

		InOrder order = inOrder(this.throttle, this.members, this.events);
		order.verify(this.throttle).lock(EMAIL);
		order.verify(this.throttle).lockedFor(EMAIL, NOW);
		order.verify(this.members).findLockedById(MEMBER);
		order.verify(this.events).publishEvent(MemberWithdrawnEvent.of(MEMBER));
		order.verify(this.throttle).forget(EMAIL);
		order.verify(this.members).delete(this.member);
	}

	@Test
	void wrongPasswordRecordsAFailureAndDeletesNothing() {
		given(this.hasher.matches("pw", "stored-hash")).willReturn(false);

		assertThat(this.service.withdraw(MEMBER, "pw")).isEqualTo(new WithdrawalResult.PasswordMismatch());

		verify(this.throttle).recordFailure(EMAIL, NOW);
		verify(this.events, never()).publishEvent(any());
		verify(this.members, never()).delete(any());
	}

	@Test
	void lockedAccountIsNotCheckedAtAll() {
		given(this.throttle.lockedFor(EMAIL, NOW)).willReturn(Optional.of(Duration.ofSeconds(30)));

		assertThat(this.service.withdraw(MEMBER, "pw"))
			.isEqualTo(new WithdrawalResult.RateLimited(Duration.ofSeconds(30)));

		verify(this.hasher, never()).matches(anyString(), anyString());
		verify(this.throttle, never()).recordFailure(any(), any());
		verify(this.members, never()).delete(any());
	}

	@Test
	void missingMemberIsGone() {
		given(this.members.findById(MEMBER)).willReturn(Optional.empty());

		assertThat(this.service.withdraw(MEMBER, "pw")).isEqualTo(new WithdrawalResult.MemberGone());

		verify(this.throttle, never()).lock(any());
	}

	@Test
	void memberRefIsAShortHashNotTheId() {
		String ref = MemberWithdrawnEvent.refOf(MEMBER);

		assertThat(ref).hasSize(12).matches("[0-9a-f]{12}").isNotEqualTo(MEMBER.substring(0, 12));
		assertThat(MemberWithdrawnEvent.of(MEMBER).memberRef()).isEqualTo(ref);
	}

}
