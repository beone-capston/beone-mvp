package com.beone.api.order.persistence;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 예정 소비 저장소. 조회·삭제는 항상 사용자 ID와 함께 한다(D-09).
 */
public interface PlannedSpendRepository extends JpaRepository<PlannedSpendEntity, Long> {

	/**
	 * 이 사용자의 기간 안 예정 소비를 저장 순서(ID 오름차순, 곧 요청 items 순서)로 돌려준다.
	 * @param userId 사용자 ID
	 * @param from 시작일(포함)
	 * @param to 종료일(포함)
	 * @return 예정 소비 목록
	 */
	List<PlannedSpendEntity> findByUserIdAndPlannedDateBetweenOrderByPlannedSpendIdAsc(Long userId, LocalDate from,
			LocalDate to);

	/**
	 * 이 사용자의 예정 소비를 날짜와 관계없이 모두 지운다.
	 * @param userId 사용자 ID
	 * @return 지운 행 수
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("DELETE FROM PlannedSpendEntity p WHERE p.userId = :userId")
	int deleteAllOfUser(@Param("userId") Long userId);

}
