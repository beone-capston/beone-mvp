package com.beone.api.codef.persistence;

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

}
