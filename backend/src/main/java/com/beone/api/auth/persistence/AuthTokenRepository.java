package com.beone.api.auth.persistence;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 1회용 토큰 저장소. 토큰은 해시로만 찾는다.
 */
public interface AuthTokenRepository extends JpaRepository<AuthTokenEntity, Long> {

	/**
	 * 해시로 토큰을 찾고 행에 쓰기 잠금을 건다. 같은 토큰의 동시 사용을 한 번만 통과시키려는 것이다. 트랜잭션 안에서만 부른다.
	 * @param tokenHash 토큰 원문의 SHA-256 16진수
	 * @return 토큰 행, 없으면 빈 값
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<AuthTokenEntity> findByTokenHash(String tokenHash);

	/**
	 * 회원의 해당 목적 토큰을 모두 지운다(재발급·사용 뒤 이전 토큰 무효).
	 * @param memberId 회원 ID
	 * @param purpose 토큰 목적
	 * @return 지운 행 수
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from AuthTokenEntity t where t.memberId = :memberId and t.purpose = :purpose")
	int deleteByMemberIdAndPurpose(@Param("memberId") String memberId, @Param("purpose") AuthTokenPurpose purpose);

}
