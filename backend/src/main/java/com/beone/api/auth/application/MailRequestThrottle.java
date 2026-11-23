package com.beone.api.auth.application;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

import com.beone.api.auth.persistence.MailThrottleEntity;
import com.beone.api.auth.persistence.MailThrottleRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 같은 주소의 메일 요청 간격 제한(docs/api/auth.md 「메일 요청 제한」).
 *
 * <p>가입·인증메일 재요청(이후 재설정 요청도)을 합산하고, 계정 유무·실제 발송 여부와 관계없이 센다. 같은 정규화 이메일의 마지막
 * 접수 요청 뒤 60초가 지나야 다음 요청을 접수한다. 거절된 요청은 기준 시각을 늦추지 않는다. 기록 키는 이메일의 SHA-256 해시다.
 *
 * <p>호출한 트랜잭션 안에서 처음부터 배타 행 잠금을 쥐므로, 같은 주소의 동시 요청은 차례로 판정되어 하나만 접수되고 나머지는
 * 거절된다.
 */
@Component
@Profile({ "local", "demo", "member" })
public class MailRequestThrottle {

	/** 같은 주소의 재발송 간격(사본). */
	static final Duration INTERVAL = Duration.ofSeconds(60);

	private final MailThrottleRepository repository;

	MailRequestThrottle(MailThrottleRepository repository) {
		this.repository = Objects.requireNonNull(repository, "repository");
	}

	/**
	 * 요청을 접수하거나 거절한다.
	 * @param normalizedEmail 정규화한 이메일
	 * @param now 요청 시각(초 단위)
	 * @throws MailRateLimitedException 간격 안의 재요청
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void acquire(String normalizedEmail, LocalDateTime now) {
		String key = SessionTokens.hash(normalizedEmail);
		this.repository.createOrLock(key);
		MailThrottleEntity row = this.repository.findLockedByEmailHash(key)
			.orElseThrow(() -> new IllegalStateException("mail throttle row disappeared"));
		LocalDateTime last = row.getLastRequestedAt();
		if (last != null && last.isAfter(now.minus(INTERVAL))) {
			throw new MailRateLimitedException(retryAfter(last, now));
		}
		row.accept(now);
	}

	/** 남은 시간을 초 단위로 올림하고 최소 1초로 둔다. */
	static Duration retryAfter(LocalDateTime last, LocalDateTime now) {
		Duration remaining = Duration.between(now, last.plus(INTERVAL));
		long seconds = remaining.getSeconds() + (remaining.getNano() > 0 ? 1 : 0);
		return Duration.ofSeconds(Math.max(1, seconds));
	}

}
