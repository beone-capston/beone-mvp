package com.beone.api.recommendation.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 추천 저장소. 조회는 항상 사용자 ID와 함께 한다(D-09).
 */
public interface RecommendationRepository extends JpaRepository<RecommendationEntity, Long> {

	/**
	 * 이 사용자의 추천을 찾는다.
	 * @param recommendationId 추천 번호
	 * @param userId 사용자 ID
	 * @return 추천, 없으면 빈 값
	 */
	Optional<RecommendationEntity> findByRecommendationIdAndUserId(Long recommendationId, Long userId);

	/**
	 * 이 사용자의 이 주문으로 만든 추천 중 ID가 가장 큰 것.
	 * @param userId 사용자 ID
	 * @param orderId 주문 번호
	 * @return 가장 최근 추천, 없으면 빈 값
	 */
	Optional<RecommendationEntity> findFirstByUserIdAndOrderIdOrderByRecommendationIdDesc(Long userId, Long orderId);

}
