package com.beone.api.payment.application;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import com.beone.api.payment.persistence.IdempotencyKeyEntity;
import com.beone.api.payment.persistence.IdempotencyKeyRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모의 승인·취소의 Idempotency-Key 저장(docs/api.md 중복 방지). 사용자별로 24시간 보존하고, 같은 키·같은 지문은 저장한 성공 응답을,
 * 같은 키·다른 지문은 409를 낸다.
 *
 * <p>호출자는 대상 자원(주문·결제) 행을 잠근 뒤 {@link #replay}를 부르므로 같은 자원의 같은 키 요청은 차례로 처리된다. 다른 자원에 같은 키를
 * 동시에 쓰면 지문이 다르므로 늦게 저장하는 쪽이 유일 제약에 걸려 409가 된다. 실패 응답(4xx)은 저장하지 않아, 상태를 고친 뒤 같은 키로 다시
 * 요청할 수 있다.
 */
@Component
public class IdempotencyStore {

	/** 키 보존 기간. 지나면 같은 키를 새 요청으로 받는다. */
	static final Duration RETENTION = Duration.ofHours(24);

	private final IdempotencyKeyRepository repository;

	public IdempotencyStore(IdempotencyKeyRepository repository) {
		this.repository = repository;
	}

	/**
	 * 같은 키의 이전 성공 응답을 찾는다. 보존 기간이 지난 키는 지우고 없는 것으로 본다.
	 * @return 같은 지문이면 저장한 응답 JSON
	 * @throws IdempotencyKeyReusedException 같은 키·다른 지문
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<String> replay(long userId, String key, String fingerprint, LocalDateTime now) {
		Optional<IdempotencyKeyEntity> found = this.repository.findByUserIdAndIdempotencyKey(userId, key);
		if (found.isEmpty()) {
			return Optional.empty();
		}
		IdempotencyKeyEntity entity = found.get();
		if (!entity.getCreatedAt().plus(RETENTION).isAfter(now)) {
			this.repository.delete(entity);
			this.repository.flush();
			return Optional.empty();
		}
		if (!entity.getFingerprint().equals(fingerprint)) {
			throw new IdempotencyKeyReusedException();
		}
		return Optional.of(entity.getResponseJson());
	}

	/**
	 * 성공 응답을 키에 연결해 저장한다.
	 * @throws IdempotencyKeyReusedException 같은 키를 다른 요청이 먼저 저장함
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void remember(long userId, String key, String fingerprint, String responseJson, LocalDateTime now) {
		try {
			this.repository.saveAndFlush(new IdempotencyKeyEntity(userId, key, fingerprint, responseJson, now));
		}
		catch (DataIntegrityViolationException ex) {
			throw new IdempotencyKeyReusedException();
		}
	}

}
