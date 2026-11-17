package com.beone.api.recommendation.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 추천 저장소. 조회는 항상 사용자 ID와 함께 한다(D-09).
 */
public interface RecommendationRepository extends JpaRepository<RecommendationEntity, Long> {

	/** 이 사용자의 추천을 찾는다. */
	Optional<RecommendationEntity> findByRecommendationIdAndUserId(Long recommendationId, Long userId);

}
