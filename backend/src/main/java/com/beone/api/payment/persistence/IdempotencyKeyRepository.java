package com.beone.api.payment.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 멱등 키 저장소. 사용자별로 찾는다(다른 사용자의 같은 키는 별개, docs/api.md 중복 방지).
 */
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyEntity, Long> {

	/**
	 * 이 사용자의 키 하나를 찾는다.
	 * @param userId 사용자 ID
	 * @param idempotencyKey Idempotency-Key 값
	 * @return 저장된 키, 없으면 빈 값
	 */
	Optional<IdempotencyKeyEntity> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

}
