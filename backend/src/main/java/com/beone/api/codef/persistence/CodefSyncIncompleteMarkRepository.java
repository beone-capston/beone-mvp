package com.beone.api.codef.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 동기화별 불완전 행 건수 저장소(V18, #289). 행은 사용자 ID와 함께 찾는다(D-09).
 */
public interface CodefSyncIncompleteMarkRepository extends JpaRepository<CodefSyncIncompleteMarkEntity, Long> {

	/**
	 * 이 사용자의 주어진 동기화들의 불완전 행 건수.
	 * @param userId 사용자 ID
	 * @param syncIds 동기화 ID
	 * @return 건수 행
	 */
	List<CodefSyncIncompleteMarkEntity> findByUserIdAndSyncIdIn(Long userId, Collection<Long> syncIds);

}
