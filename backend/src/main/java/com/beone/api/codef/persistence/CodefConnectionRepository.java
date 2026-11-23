package com.beone.api.codef.persistence;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * CODEF 연결 저장소. 행은 사용자 ID로만 찾는다(D-09).
 */
public interface CodefConnectionRepository extends JpaRepository<CodefConnectionEntity, Long> {

	/**
	 * 사용자 행을 쓰기 잠금한다. 연결 행이 아직 없는 사용자의 동시 최초 연결도 차례로 처리하려고 연결 행 대신 잠근다. 사용자 행은 읽기만
	 * 하고 바꾸지 않는다.
	 * @param userId 사용자 ID
	 * @return 사용자 ID
	 */
	@Query(value = "SELECT user_id FROM users WHERE user_id = :userId FOR UPDATE", nativeQuery = true)
	Long lockUser(@Param("userId") long userId);

	/**
	 * 이 사용자의 연결 행을 쓰기 잠금으로 찾는다. {@link #lockUser} 뒤에 불러 앞 요청이 커밋한 값을 읽는다.
	 * @param userId 사용자 ID
	 * @return 연결 행
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from CodefConnectionEntity c where c.userId = :userId")
	Optional<CodefConnectionEntity> lockByUserId(@Param("userId") long userId);

}
