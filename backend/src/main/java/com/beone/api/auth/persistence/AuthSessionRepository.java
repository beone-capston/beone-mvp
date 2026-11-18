package com.beone.api.auth.persistence;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 서버 세션 저장소. 세션은 해시로만 찾는다.
 */
public interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, Long> {

	/**
	 * 세션 ID 해시로 세션 행을 찾고 쓰기 잠금을 건다. 같은 세션을 쓰는 동시 요청이 만료 판정과 갱신·삭제를 겹쳐, 다른 요청이 방금
	 * 연장한 세션을 지우지 않게 한다. 트랜잭션 안에서만 호출한다.
	 * @param sessionHash 세션 ID의 SHA-256 해시
	 * @return 세션 행, 없으면 빈 값
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<AuthSessionEntity> findBySessionHash(String sessionHash);

	/**
	 * 회원의 세션을 오래 쓰지 않은 순(마지막 사용 시각, 같으면 발급 순)으로 찾고 쓰기 잠금을 건다. 동시 세션 수를 맞출 때 쓴다. 회원 행을
	 * 먼저 잠근 트랜잭션 안에서만 호출한다.
	 * @param memberId 회원 ID
	 * @return 만료 여부와 관계없는 그 회원의 세션 행
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	List<AuthSessionEntity> findByMemberIdOrderByLastUsedAtAscAuthSessionIdAsc(String memberId);

	/**
	 * 회원의 세션을 모두 지운다(비밀번호 재설정). 잠금 읽기와 같은 최신 행 기준으로 지우므로, 회원 행 잠금 전에 커밋된 로그인 세션도
	 * 남기지 않는다. 회원 행을 먼저 잠근 트랜잭션 안에서만 호출한다.
	 * @param memberId 회원 ID
	 * @return 지운 세션 수
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from AuthSessionEntity s where s.memberId = :memberId")
	int deleteAllByMemberId(@Param("memberId") String memberId);

}
