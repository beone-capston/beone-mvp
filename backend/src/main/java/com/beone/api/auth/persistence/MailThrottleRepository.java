package com.beone.api.auth.persistence;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 같은 주소의 메일 요청 간격 기록({@code auth_mail_throttles}) 저장소.
 *
 * <p>{@link #createOrLock}로 행을 만들거나 기존 행에 배타 잠금을 건 뒤, {@link #findLockedByEmailHash}로 최신 값을 읽어 판정한다.
 * 처음부터 배타 잠금을 쓰므로 같은 주소의 동시 요청은 차례로 처리되고, 공유 잠금을 배타 잠금으로 올리다 생기는 교착이 없다.
 */
public interface MailThrottleRepository extends JpaRepository<MailThrottleEntity, String> {

	/**
	 * 행이 없으면 시각 없이 만들고, 있으면 값을 바꾸지 않은 채 배타 잠금을 건다. 트랜잭션 안에서만 부른다.
	 * @param emailHash 정규화한 이메일의 SHA-256 16진수
	 */
	@Modifying
	@Query(value = "INSERT INTO auth_mail_throttles (email_hash, last_requested_at) VALUES (:emailHash, NULL)"
			+ " ON DUPLICATE KEY UPDATE email_hash = email_hash", nativeQuery = true)
	void createOrLock(@Param("emailHash") String emailHash);

	/**
	 * 잠금 읽기로 최신 행을 읽는다. 트랜잭션 안에서만 부른다.
	 * @param emailHash 정규화한 이메일의 SHA-256 16진수
	 * @return 행, 없으면 빈 값
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from MailThrottleEntity t where t.emailHash = :emailHash")
	Optional<MailThrottleEntity> findLockedByEmailHash(@Param("emailHash") String emailHash);

}
