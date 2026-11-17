package com.beone.api.auth.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 서버 세션 저장소. 세션은 해시로만 찾는다.
 */
public interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, Long> {

	/**
	 * 세션 ID 해시로 세션 행을 찾는다.
	 * @param sessionHash 세션 ID의 SHA-256 해시
	 * @return 세션 행, 없으면 빈 값
	 */
	Optional<AuthSessionEntity> findBySessionHash(String sessionHash);

}
