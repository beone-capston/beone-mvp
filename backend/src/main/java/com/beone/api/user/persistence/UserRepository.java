package com.beone.api.user.persistence;

import java.util.Optional;

import com.beone.api.user.application.DemoUser;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 시연 사용자 저장소.
 */
public interface UserRepository extends JpaRepository<UserEntity, Long> {

	/** 시연 사용자 구분으로 한 명을 찾는다. */
	Optional<UserEntity> findByDemoUser(DemoUser demoUser);

}
