package com.beone.api.codef.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * CODEF 승인 행 저장소. 행은 사용자 ID와 함께 찾는다(D-09).
 */
public interface CodefApprovalRowRepository extends JpaRepository<CodefApprovalRowEntity, Long> {

	/**
	 * 이 사용자의 승인 행 전부. 재조회 대조의 이전 기록으로 쓴다.
	 * @param userId 사용자 ID
	 * @return 승인 행
	 */
	List<CodefApprovalRowEntity> findByUserId(Long userId);

	/**
	 * 이 사용자의 주어진 보유 카드들에서 사용일자가 기간 안인 승인 행(#289). 사용일자 → 사용일시 → 행 ID 오름차순.
	 * @param userId 사용자 ID
	 * @param userCardIds 보유 카드 ID
	 * @param from 기간 시작일
	 * @param to 기간 종료일
	 * @return 승인 행
	 */
	List<CodefApprovalRowEntity> findByUserIdAndUserCardIdInAndUsedDateBetweenOrderByUsedDateAscUsedTimeAscCodefApprovalRowIdAsc(
			Long userId, Collection<Long> userCardIds, LocalDate from, LocalDate to);

}
