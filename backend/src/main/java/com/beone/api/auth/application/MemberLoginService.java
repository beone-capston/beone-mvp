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
import org.springframework.context.annotation.Profile;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 회원 로그인·로그아웃·현재 세션 조회(docs/api/auth.md U3). {@code local}·{@code demo}·{@code member} 프로필 전용이다.
 *
 * <p>로그인 판정 순서: 실패 제한 잠금(429) → 비밀번호 확인(계정 없음과 불일치는 같은 401) → 이메일 인증(403, 비밀번호가 맞은 뒤에만)
 * → 새 회원 세션. 계정이 없어도 미리 만든 더미 해시와 비교해 Argon2id 계산을 똑같이 하므로 응답 시간으로 계정 존재가 드러나지 않게
 * 한다. 잠금 순서는 로그인 잠금 행 → 회원 행 → 세션 행이다.
 *
 * <p>로그인 트랜잭션은 READ COMMITTED로 돈다. MySQL 기본(REPEATABLE READ)에서는 회원별 세션 잠금 조회가 인덱스 간격까지 잠가, 세션이 없던
 * 서로 다른 회원이 동시에 로그인하면 새 세션 삽입끼리 교착했다(동시 시험에서 재현). READ COMMITTED는 간격 잠금 없이 찾은 행만 잠그고, 같은
 * 회원의 동시 로그인은 회원 행 잠금으로 차례로 처리되므로 동시 세션 상한은 그대로 지킨다.
 *
 * <p>서로 다른 두 계정의 세션으로 상대 계정에 동시에 로그인하면(계정 전환) 각 로그인이 이전 세션 행과 대상 회원의 세션 행을 엇갈려 잠가
 * 교착할 수 있다(동시 시험에서 재현). 교착·잠금 대기 실패로 진 트랜잭션은 전부 롤백되므로 새 트랜잭션에서 처음부터 다시 판정한다(최대
 * {@value #MAX_ATTEMPTS}번). 실패 기록도 함께 롤백돼 두 번 세지 않는다.
 *
 * <p>현재 시각은 잠금 상태 행을 잡은 뒤에 읽고, 실패 시각은 비밀번호 비교가 끝난 뒤에 읽는다. 잠금 대기·Argon2id 계산 시간만큼 잠금
 * 판정이 늦거나 잠금 기간이 짧아지지 않게 하기 위해서다.
 */
@Service
@Profile({ "local", "demo", "member" })
public class MemberLoginService {

	/** 교착으로 진 로그인의 최대 시도 횟수(첫 시도 포함). */
	static final int MAX_ATTEMPTS = 3;

	private static final Logger log = LoggerFactory.getLogger(MemberLoginService.class);

	private final MemberRepository members;

	private final LoginAttemptThrottle throttle;

	private final PasswordHasher passwordHasher;

	private final AuthSessionService sessions;

	private final Clock clock;

	/** 로그인 한 번을 READ COMMITTED 트랜잭션으로 실행한다. */
	private final TransactionOperations loginTransaction;

	/** 계정이 없을 때 비교하는 해시. 어떤 비밀번호와도 맞지 않는 무작위 값의 해시다. */
	private final String dummyHash;

	/**
	 * {@link Clock} 빈이 하나 있으면 그것을, 없으면 시스템 시계를 쓴다.
	 * @param members 회원 저장소
	 * @param throttle 로그인 실패 제한
	 * @param passwordHasher 비밀번호 해시
	 * @param sessions 세션 서비스
	 * @param clock 시계 빈
	 * @param transactionManager 트랜잭션 관리자
	 */
	@Autowired
	public MemberLoginService(MemberRepository members, LoginAttemptThrottle throttle, PasswordHasher passwordHasher,
			AuthSessionService sessions, ObjectProvider<Clock> clock, PlatformTransactionManager transactionManager) {
		this(members, throttle, passwordHasher, sessions, clock.getIfUnique(Clock::systemUTC),
				readCommitted(transactionManager));
	}

	MemberLoginService(MemberRepository members, LoginAttemptThrottle throttle, PasswordHasher passwordHasher,
			AuthSessionService sessions, Clock clock, TransactionOperations loginTransaction) {
		this.members = Objects.requireNonNull(members, "members");
		this.throttle = Objects.requireNonNull(throttle, "throttle");
		this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
		this.sessions = Objects.requireNonNull(sessions, "sessions");
		this.clock = Objects.requireNonNull(clock, "clock").withZone(AuthSessionService.ZONE);
		this.loginTransaction = Objects.requireNonNull(loginTransaction, "loginTransaction");
		this.dummyHash = passwordHasher.hash(SessionTokens.newSessionId());
	}

	/**
	 * 로그인한다. 실패 기록이 커밋되도록 실패도 예외가 아니라 결과 값으로 돌려준다. 교착으로 진 시도는 새 트랜잭션에서 다시 한다.
	 * @param email 요청 이메일(형식 검증을 마친 값)
	 * @param password 요청 비밀번호 원문
	 * @param previousSessionId 요청 쿠키의 세션 ID(CSRF 검사를 통과한 세션), {@code null} 가능
	 * @return 판정 결과
	 */
	public LoginResult login(String email, String password, String previousSessionId) {
		for (int attempt = 1;; attempt++) {
			try {
				return this.loginTransaction.execute(status -> attempt(email, password, previousSessionId));
			}
			catch (PessimisticLockingFailureException ex) {
				if (attempt >= MAX_ATTEMPTS) {
					throw ex;
				}
				log.debug("login transaction lost a lock conflict, retrying attempt={} exception={}", attempt + 1,
						ex.getClass().getName());
			}
		}
	}

	private LoginResult attempt(String email, String password, String previousSessionId) {
		String normalized = EmailAddresses.normalize(email);
		this.throttle.lock(normalized);
		Optional<Duration> locked = this.throttle.lockedFor(normalized, now());
		if (locked.isPresent()) {
			return new LoginResult.RateLimited(locked.get());
		}
		Optional<MemberEntity> member = this.members.findByEmail(normalized);
		String storedHash = member.map(MemberEntity::getPasswordHash).orElse(this.dummyHash);
		boolean matches = this.passwordHasher.matches(password, storedHash);
		if (member.isEmpty() || !matches) {
			this.throttle.recordFailure(normalized, now());
			return new LoginResult.InvalidCredentials();
		}
		MemberEntity found = member.get();
		if (!found.isEmailVerified()) {
			return new LoginResult.EmailNotVerified();
		}
		this.throttle.recordSuccess(normalized);
		IssuedSession issued = this.sessions.issueMember(found.getMemberId(), previousSessionId);
		return new LoginResult.Succeeded(found.getMemberId(), found.getEmail(), issued);
	}

	/**
	 * 현재 회원 세션을 찾고 사용 시각을 갱신한다.
	 * @param sessionId 요청 쿠키의 세션 ID, {@code null} 가능
	 * @return 회원 세션. 세션 없음·만료·로그아웃·익명 세션은 빈 값
	 */
	@Transactional
	public Optional<CurrentMemberSession> currentSession(String sessionId) {
		return this.sessions.useMember(sessionId)
			.flatMap(session -> this.members.findById(session.memberId())
				.map(member -> new CurrentMemberSession(member.getMemberId(), member.getEmail(),
						session.authenticatedAt(), session.expiresAt(), session.absoluteExpiresAt())));
	}

	/**
	 * 현재 세션만 끝낸다. 다른 기기의 세션은 그대로다.
	 * @param sessionId 요청 쿠키의 세션 ID, {@code null} 가능
	 */
	@Transactional
	public void logout(String sessionId) {
		this.sessions.end(sessionId);
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
