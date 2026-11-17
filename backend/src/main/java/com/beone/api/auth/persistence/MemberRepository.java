package com.beone.api.auth.persistence;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 회원 저장소.
 *
 * <p>잠금 순서: 회원 행을 먼저 잠그고 그 회원의 토큰 행을 잠근다. 가입·재요청·이메일 인증이 모두 이 순서를 따라 서로 교착하지 않는다.
 */
public interface MemberRepository extends JpaRepository<MemberEntity, String> {

	/**
	 * 정규화한 이메일로 회원을 찾고 행에 쓰기 잠금을 건다. 트랜잭션 안에서만 부른다.
	 * @param email 정규화한 이메일
	 * @return 회원, 없으면 빈 값
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<MemberEntity> findByEmail(String email);

	/**
	 * 회원 ID로 회원을 찾고 행에 쓰기 잠금을 건다. 트랜잭션 안에서만 부른다.
	 * @param memberId 회원 ID
	 * @return 회원, 없으면 빈 값
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select m from MemberEntity m where m.memberId = :memberId")
	Optional<MemberEntity> findLockedById(@Param("memberId") String memberId);

}
