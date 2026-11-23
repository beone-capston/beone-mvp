package com.beone.api.recommendation.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 추천별 보유 카드 근거 저장소.
 */
public interface RecommendationCardBasisRepository extends JpaRepository<RecommendationCardBasisEntity, Long> {

	/** 추천 하나의 카드 근거를 넣은 순서로 찾는다. */
	List<RecommendationCardBasisEntity> findByRecommendationIdOrderByCardBasisIdAsc(Long recommendationId);

	/** 이 사용자의 추천 중 이 보유 카드를 근거로 쓴 추천 번호를 오름차순으로 찾는다. */
	@Query("select distinct b.recommendationId from RecommendationCardBasisEntity b, RecommendationEntity r"
			+ " where r.recommendationId = b.recommendationId and r.userId = :userId and b.userCardId = :userCardId"
			+ " order by b.recommendationId")
	List<Long> findRecommendationIdsUsingCard(@Param("userId") Long userId, @Param("userCardId") Long userCardId);

}
