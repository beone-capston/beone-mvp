package com.beone.api.codef.persistence;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * CODEF 동기화 기록 저장소.
 */
public interface CodefSyncRepository extends JpaRepository<CodefSyncEntity, Long> {

	/**
	 * 이 사용자의 승인내역 판정을 마친 동기화 중 조회 기간이 주어진 기간과 겹치는 것(#289).
	 * @param userId 사용자 ID
	 * @param from 기간 시작일
	 * @param to 기간 종료일
	 * @return 동기화. 순서는 정하지 않는다
	 */
	@Query("SELECT s FROM CodefSyncEntity s WHERE s.userId = :userId AND s.approvalsJudged = TRUE"
			+ " AND s.periodFrom <= :to AND s.periodTo >= :from")
	List<CodefSyncEntity> findJudgedOverlapping(@Param("userId") Long userId, @Param("from") LocalDate from,
			@Param("to") LocalDate to);

}
