package com.beone.api.auth.application;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

import com.beone.api.auth.persistence.LoginFailureEntity;
import com.beone.api.auth.persistence.LoginFailureRepository;
import com.beone.api.auth.persistence.LoginThrottleEntity;
import com.beone.api.auth.persistence.LoginThrottleRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 같은 이메일의 로그인 실패 제한(docs/api/auth.md 「사본에 없어 사용자가 정한 값」, 2026-11-18 사용자 직접 결정).
 *
 * <p>같은 정규화 이메일로 15분 안에 5번 실패하면 그때부터 15분 동안 잠근다. 잠긴 동안의 로그인은 비밀번호를 보지 않고 거절하며
 * 실패로 세지 않고 잠금을 늘리지도 않는다. 계정이 없어도 똑같이 센다. 잠그거나 로그인에 성공하면 그 이메일의 실패 기록을 지운다. 기준은
 * 이메일이고 IP 기준은 없다. 기록 키는 이메일의 SHA-256 해시다.
 *
 * <p>호출한 트랜잭션 안에서 처음부터 배타 행 잠금을 쥐므로 같은 이메일의 동시 로그인은 차례로 판정된다.
 */
@Component
@Profile({ "local", "demo" })
public class LoginAttemptThrottle {

	/** 실패를 세는 기간과 잠금 기간(사용자 직접 결정). */
	static final Duration WINDOW = Duration.ofMinutes(15);

	/** 잠그는 실패 수(사용자 직접 결정). */
	static final int MAX_FAILURES = 5;

	private final LoginThrottleRepository throttles;

	private final LoginFailureRepository failures;

	LoginAttemptThrottle(LoginThrottleRepository throttles, LoginFailureRepository failures) {
		this.throttles = Objects.requireNonNull(throttles, "throttles");
		this.failures = Objects.requireNonNull(failures, "failures");
	}

	/**
	 * 이메일의 잠금 상태 행을 배타 잠금하고 잠겨 있는지 본다. 같은 트랜잭션에서 {@link #recordFailure}·{@link #recordSuccess}보다 먼저
	 * 부른다.
	 * @param normalizedEmail 정규화한 이메일
	 * @param now 요청 시각(초 단위)
	 * @return 잠겨 있으면 남은 시간(1초 이상), 아니면 빈 값
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<Duration> lockedFor(String normalizedEmail, LocalDateTime now) {
		LocalDateTime until = lockRow(normalizedEmail).getLockedUntil();
		if (until != null && now.isBefore(until)) {
			return Optional.of(retryAfter(until, now));
		}
		return Optional.empty();
	}

	/**
	 * 실패를 기록하고, 15분 안의 실패가 5번이 되면 15분 동안 잠근다.
	 * @param normalizedEmail 정규화한 이메일
	 * @param now 실패 시각(초 단위)
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void recordFailure(String normalizedEmail, LocalDateTime now) {
		String key = SessionTokens.hash(normalizedEmail);
		LoginThrottleEntity row = lockRow(normalizedEmail);
		LocalDateTime windowStart = now.minus(WINDOW);
		this.failures.deleteFailuresNotAfter(key, windowStart);
		this.failures.save(new LoginFailureEntity(key, now));
		if (this.failures.countFailuresAfter(key, windowStart) >= MAX_FAILURES) {
			row.lockUntil(now.plus(WINDOW));
			this.failures.deleteAllFailures(key);
		}
	}

	/**
	 * 로그인 성공: 실패 기록을 지우고 지난 잠금 시각을 비운다.
	 * @param normalizedEmail 정규화한 이메일
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void recordSuccess(String normalizedEmail) {
		lockRow(normalizedEmail).unlock();
		this.failures.deleteAllFailures(SessionTokens.hash(normalizedEmail));
	}

	private LoginThrottleEntity lockRow(String normalizedEmail) {
		String key = SessionTokens.hash(normalizedEmail);
		this.throttles.createOrLock(key);
		return this.throttles.findLockedByEmailHash(key)
			.orElseThrow(() -> new IllegalStateException("login throttle row disappeared"));
	}

	/** 남은 시간을 초 단위로 올림하고 최소 1초로 둔다. */
	static Duration retryAfter(LocalDateTime until, LocalDateTime now) {
		Duration remaining = Duration.between(now, until);
		long seconds = remaining.getSeconds() + (remaining.getNano() > 0 ? 1 : 0);
		return Duration.ofSeconds(Math.max(1, seconds));
	}

}
