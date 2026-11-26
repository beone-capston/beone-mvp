package com.beone.api.codef.persistence;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 카드사 조회 실적(참고) 저장소.
 */
public interface CodefPerformanceReferenceRepository extends JpaRepository<CodefPerformanceReferenceEntity, Long> {

	/**
	 * 이 사용자의 이 보유 카드에 연결된 참고값 중 반영기간이 정확히 같은 최신 1건(조회 시각, 같으면 ID가 큰 것).
	 * @param userId 사용자 ID
	 * @param userCardId 보유 카드 ID
	 * @param periodFrom 반영기간 시작일
	 * @param periodTo 반영기간 종료일
	 * @return 최신 참고값, 없으면 빈 값
	 */
	Optional<CodefPerformanceReferenceEntity> findFirstByUserIdAndUserCardIdAndPeriodFromAndPeriodToOrderByFetchedAtDescCodefPerformanceReferenceIdDesc(
			Long userId, Long userCardId, LocalDate periodFrom, LocalDate periodTo);

	/**
	 * 이 사용자의 참고값 하나.
	 * @param codefPerformanceReferenceId 참고값 ID
	 * @param userId 사용자 ID
	 * @return 참고값, 없거나 다른 사용자의 것이면 빈 값
	 */
	Optional<CodefPerformanceReferenceEntity> findByCodefPerformanceReferenceIdAndUserId(
			Long codefPerformanceReferenceId, Long userId);

}
