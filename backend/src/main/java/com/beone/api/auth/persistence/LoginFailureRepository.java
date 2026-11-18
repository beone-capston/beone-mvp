package com.beone.api.auth.persistence;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 로그인 실패 기록 저장소. 같은 이메일의 잠금 상태 행을 잠근 트랜잭션 안에서만 쓴다.
 */
public interface LoginFailureRepository extends JpaRepository<LoginFailureEntity, Long> {

	/**
	 * 기준 시각보다 뒤의 실패 수를 센다.
	 * @param emailHash 정규화 이메일의 SHA-256 16진수
	 * @param after 이 시각보다 뒤(같은 시각 제외)
	 * @return 실패 수
	 */
	@Query("select count(f) from LoginFailureEntity f where f.emailHash = :emailHash and f.failedAt > :after")
	long countFailuresAfter(@Param("emailHash") String emailHash, @Param("after") LocalDateTime after);

	/**
	 * 기준 시각 이전(같은 시각 포함)의 실패 기록을 지운다.
	 * @param emailHash 정규화 이메일의 SHA-256 16진수
	 * @param notAfter 이 시각 이하
	 * @return 지운 수
	 */
	@Modifying(flushAutomatically = true)
	@Query("delete from LoginFailureEntity f where f.emailHash = :emailHash and f.failedAt <= :notAfter")
	int deleteFailuresNotAfter(@Param("emailHash") String emailHash, @Param("notAfter") LocalDateTime notAfter);

	/**
	 * 이메일의 실패 기록을 모두 지운다.
	 * @param emailHash 정규화 이메일의 SHA-256 16진수
	 * @return 지운 수
	 */
	@Modifying(flushAutomatically = true)
	@Query("delete from LoginFailureEntity f where f.emailHash = :emailHash")
	int deleteAllFailures(@Param("emailHash") String emailHash);

}
