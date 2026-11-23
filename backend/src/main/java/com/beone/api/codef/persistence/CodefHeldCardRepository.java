package com.beone.api.codef.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * CODEF 카드 대조 정보 저장소. 행은 사용자 ID와 함께 찾는다(D-09).
 */
public interface CodefHeldCardRepository extends JpaRepository<CodefHeldCardEntity, Long> {

	/**
	 * 이 사용자의 주어진 보유 카드들의 대조 정보. 해제하지 않은 CODEF 카드 ID 목록을 넘긴다.
	 * @param userId 사용자 ID
	 * @param userCardIds 보유 카드 ID
	 * @return 대조 정보
	 */
	List<CodefHeldCardEntity> findByUserIdAndUserCardIdIn(Long userId, Collection<Long> userCardIds);

}
