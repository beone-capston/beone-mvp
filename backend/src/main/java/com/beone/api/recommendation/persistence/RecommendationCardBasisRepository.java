package com.beone.api.recommendation.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 추천별 보유 카드 근거 저장소.
 */
public interface RecommendationCardBasisRepository extends JpaRepository<RecommendationCardBasisEntity, Long> {

	/** 추천 하나의 카드 근거를 넣은 순서로 찾는다. */
	List<RecommendationCardBasisEntity> findByRecommendationIdOrderByCardBasisIdAsc(Long recommendationId);

}
