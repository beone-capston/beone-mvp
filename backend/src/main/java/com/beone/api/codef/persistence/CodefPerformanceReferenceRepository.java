package com.beone.api.codef.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 카드사 조회 실적(참고) 저장소.
 */
public interface CodefPerformanceReferenceRepository extends JpaRepository<CodefPerformanceReferenceEntity, Long> {

}
