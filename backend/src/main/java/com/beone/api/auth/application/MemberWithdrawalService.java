package com.beone.api.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

import com.beone.api.auth.persistence.MemberEntity;
import com.beone.api.auth.persistence.MemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 회원 탈퇴(docs/api/auth.md {@code DELETE /api/v1/me}, 결정 D10). 회원 프로필 {@code member} 전용이다.
 *
 * <p>판정 순서: 로그인 실패 제한 잠금(429) → 비밀번호 재확인(불일치는 실패 1회 기록) → 삭제. 비밀번호 재확인 실패는 로그인 실패와 같은
 * 제한(15분 5회)에 더해, 세션을 가로챈 사람이 비밀번호를 무차별 대입하지 못하게 한다.
 *
 * <p>삭제는 한 트랜잭션이다. {@link MemberWithdrawnEvent}를 동기로 발행해 업무 장부(user 패키지)를 지운 뒤 회원 행을 지우고, 세션·인증
 * 토큰은 외래 키 {@code ON DELETE CASCADE}로 함께 지워진다. 그 이메일의 로그인 실패·잠금 기록도 지운다. 메일 요청 제한(60초)은 남겨 같은
 * 주소의 재가입 메일이 연달아 나가지 않게 한다. CODEF 연결 해제 요청은 받는 쪽이 커밋 뒤에 한다.
 *
 * <p>감사 기록은 로그 한 줄이다. 이메일·회원 ID 원문 대신 {@link MemberWithdrawnEvent#refOf} 참조값만 남긴다.
 */
@Service
@Profile("member")
public class MemberWithdrawalService {

	private static final Logger log = LoggerFactory.getLogger(MemberWithdrawalService.class);

	private final MemberRepository members;

	private final LoginAttemptThrottle throttle;

	private final PasswordHasher passwordHasher;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	private final TransactionOperations transaction;

	/**
	 * {@link Clock} 빈이 하나 있으면 그것을, 없으면 시스템 시계를 쓴다.
	 * @param members 회원 저장소
	 * @param throttle 로그인 실패 제한
	 * @param passwordHasher 비밀번호 해시
	 * @param events 이벤트 발행기
	 * @param clock 시계 빈
	 * @param transactionManager 트랜잭션 관리자
	 */
	@Autowired
	public MemberWithdrawalService(MemberRepository members, LoginAttemptThrottle throttle,
			PasswordHasher passwordHasher, ApplicationEventPublisher events, ObjectProvider<Clock> clock,
			PlatformTransactionManager transactionManager) {
		this(members, throttle, passwordHasher, events, clock.getIfUnique(Clock::systemUTC),
				readCommitted(transactionManager));
	}

	MemberWithdrawalService(MemberRepository members, LoginAttemptThrottle throttle, PasswordHasher passwordHasher,
			ApplicationEventPublisher events, Clock clock, TransactionOperations transaction) {
		this.members = Objects.requireNonNull(members, "members");
		this.throttle = Objects.requireNonNull(throttle, "throttle");
		this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
		this.events = Objects.requireNonNull(events, "events");
		this.clock = Objects.requireNonNull(clock, "clock").withZone(AuthSessionService.ZONE);
		this.transaction = Objects.requireNonNull(transaction, "transaction");
	}

	/**
	 * 비밀번호를 다시 확인하고 회원과 그 데이터를 지운다.
	 * @param memberId 세션·CSRF 검사를 통과한 회원 ID
	 * @param password 요청 비밀번호 원문
	 * @return 판정 결과
	 */
	public WithdrawalResult withdraw(String memberId, String password) {
		WithdrawalResult result = this.transaction.execute(status -> attempt(memberId, password));
		if (result instanceof WithdrawalResult.Withdrawn) {
			log.info("member withdrawn ref={}", MemberWithdrawnEvent.refOf(memberId));
		}
		return result;
	}

	private WithdrawalResult attempt(String memberId, String password) {
		// 잠금 순서는 로그인과 같게 로그인 잠금 행 → 회원 행이다. 이메일은 바뀌지 않으므로 잠금 없이 먼저 읽는다
		Optional<String> email = this.members.findById(memberId).map(MemberEntity::getEmail);
		if (email.isEmpty()) {
			return new WithdrawalResult.MemberGone();
		}
		this.throttle.lock(email.get());
		Optional<Duration> locked = this.throttle.lockedFor(email.get(), now());
		if (locked.isPresent()) {
			return new WithdrawalResult.RateLimited(locked.get());
		}
		Optional<MemberEntity> found = this.members.findLockedById(memberId);
		if (found.isEmpty()) {
			return new WithdrawalResult.MemberGone();
		}
		MemberEntity member = found.get();
		if (!this.passwordHasher.matches(password, member.getPasswordHash())) {
			this.throttle.recordFailure(email.get(), now());
			return new WithdrawalResult.PasswordMismatch();
		}
		this.events.publishEvent(MemberWithdrawnEvent.of(memberId));
		this.throttle.forget(email.get());
		this.members.delete(member);
		this.members.flush();
		return new WithdrawalResult.Withdrawn();
	}

	private static TransactionOperations readCommitted(PlatformTransactionManager transactionManager) {
		TransactionTemplate template = new TransactionTemplate(transactionManager);
		template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
		return template;
	}

	private LocalDateTime now() {
		return LocalDateTime.now(this.clock).truncatedTo(ChronoUnit.SECONDS);
	}

}
