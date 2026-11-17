package com.beone.api.auth.persistence;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/**
 * 회원 저장소.
 */
public interface MemberRepository extends JpaRepository<MemberEntity, String> {

	/**
	 * 정규화한 이메일로 회원을 찾고 행에 쓰기 잠금을 건다. 트랜잭션 안에서만 부른다.
	 * @param email 정규화한 이메일
	 * @return 회원, 없으면 빈 값
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<MemberEntity> findByEmail(String email);

}
