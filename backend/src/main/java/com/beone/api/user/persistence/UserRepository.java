package com.beone.api.user.persistence;

import java.util.Optional;

import com.beone.api.user.application.DemoUser;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 시연 사용자 저장소.
 */
public interface UserRepository extends JpaRepository<UserEntity, Long> {

	/**
	 * 시연 사용자 구분으로 한 명을 찾는다.
	 * @param demoUser 시연 사용자 구분
	 * @return 사용자, 없으면 빈 값
	 */
	Optional<UserEntity> findByDemoUser(DemoUser demoUser);

}
