package com.beone.api.monthlystate.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * 한도 묶음 사용액 저장소.
 */
public interface BucketUsageRepository extends JpaRepository<BucketUsageEntity, Long> {

	/** 월간 상태 하나의 사용액을 요청 순서로 찾는다. */
	List<BucketUsageEntity> findByMonthlyStateIdOrderBySeqAsc(Long monthlyStateId);

	/** 월간 상태 하나의 사용액을 모두 지운다. 교체 전에 부른다. */
	@Modifying(flushAutomatically = true, clearAutomatically = false)
	@Query("delete from BucketUsageEntity b where b.monthlyStateId = :monthlyStateId")
	int deleteByMonthlyStateId(Long monthlyStateId);

}
