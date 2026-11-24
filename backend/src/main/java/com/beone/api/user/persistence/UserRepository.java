package com.beone.api.user.persistence;

import java.util.List;
import java.util.Optional;

import com.beone.api.user.application.DemoUser;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사용자(장부) 저장소. 시연 사용자와 회원 장부를 함께 담는다.
 */
public interface UserRepository extends JpaRepository<UserEntity, Long> {

	/** 시연 사용자 구분으로 한 명을 찾는다. */
	Optional<UserEntity> findByDemoUser(DemoUser demoUser);

	/**
	 * 시연 사용자 행만 찾는다. 회원 장부는 빠진다.
	 * @return 시연 사용자 행 목록
	 */
	List<UserEntity> findByDemoUserIsNotNull();

	/**
	 * 회원의 장부를 찾는다.
	 * @param memberId 회원 ID
	 * @return 회원 장부, 없으면 빈 값
	 */
	Optional<UserEntity> findByMemberId(String memberId);

	/**
	 * 회원의 장부가 있는지.
	 * @param memberId 회원 ID
	 * @return 있으면 {@code true}
	 */
	boolean existsByMemberId(String memberId);

}
