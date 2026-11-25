package com.beone.api.monthlystate.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * 택1 선택 저장소.
 */
public interface ServiceSelectionRepository extends JpaRepository<ServiceSelectionEntity, Long> {

	/**
	 * 월간 상태 하나의 선택을 요청 순서로 찾는다.
	 * @param monthlyStateId 월간 상태 ID
	 * @return 선택 행 목록
	 */
	List<ServiceSelectionEntity> findByMonthlyStateIdOrderBySeqAsc(Long monthlyStateId);

	/**
	 * 월간 상태 하나의 선택을 모두 지운다. 교체 전에 부른다.
	 * @param monthlyStateId 월간 상태 ID
	 * @return 지운 행 수
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = false)
	@Query("delete from ServiceSelectionEntity s where s.monthlyStateId = :monthlyStateId")
	int deleteByMonthlyStateId(Long monthlyStateId);

}
