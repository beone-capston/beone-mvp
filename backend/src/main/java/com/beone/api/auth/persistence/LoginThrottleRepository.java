package com.beone.api.auth.persistence;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 로그인 잠금 상태 저장소.
 *
 * <p>같은 이메일의 로그인은 이 행의 배타 잠금으로 차례로 판정한다. 공유 잠금 뒤 배타 잠금으로 올리면 동시 요청끼리 교착하므로 처음부터
 * {@code INSERT ... ON DUPLICATE KEY UPDATE}로 배타 잠금을 잡는다(U2 메일 요청 제한과 같은 방식).
 */
public interface LoginThrottleRepository extends JpaRepository<LoginThrottleEntity, String> {

	/**
	 * 행이 없으면 잠기지 않은 상태로 만들고, 있으면 배타 잠금만 건다. 트랜잭션 안에서만 부른다.
	 * @param emailHash 정규화 이메일의 SHA-256 16진수
	 */
	@Modifying
	@Query(value = "INSERT INTO auth_login_throttles (email_hash, locked_until) VALUES (:emailHash, NULL)"
			+ " ON DUPLICATE KEY UPDATE email_hash = email_hash", nativeQuery = true)
	void createOrLock(@Param("emailHash") String emailHash);

	/**
	 * 잠금 상태 행을 쓰기 잠금으로 읽는다. {@link #createOrLock} 뒤에 부른다.
	 * @param emailHash 정규화 이메일의 SHA-256 16진수
	 * @return 잠금 상태 행
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from LoginThrottleEntity t where t.emailHash = :emailHash")
	Optional<LoginThrottleEntity> findLockedByEmailHash(@Param("emailHash") String emailHash);

}
