package com.beone.api.recommendation.application;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.beone.api.card.application.RecommendationInvalidationLookup;
import com.beone.api.order.application.LatestRecommendationLookup;
import com.beone.api.recommendation.persistence.RecommendationCardBasisRepository;
import com.beone.api.recommendation.persistence.RecommendationRepository;
import com.beone.api.user.application.CurrentUser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문·보유 카드 쪽 조회 포트의 추천 구현(#120). 주문의 마지막 추천과 카드 변경으로 무효가 된 추천을 돌려준다.
 *
 * <p>무효화 판정은 추천 조회와 같은 지연 판정({@link RecommendationService#invalidationOf})을 쓴다.
 */
@Component
public class RecommendationLinks implements LatestRecommendationLookup, RecommendationInvalidationLookup {

	private final RecommendationRepository recommendationRepository;

	private final RecommendationCardBasisRepository cardBasisRepository;

	private final RecommendationService recommendationService;

	public RecommendationLinks(RecommendationRepository recommendationRepository,
			RecommendationCardBasisRepository cardBasisRepository, RecommendationService recommendationService) {
		this.recommendationRepository = recommendationRepository;
		this.cardBasisRepository = cardBasisRepository;
		this.recommendationService = recommendationService;
	}

	/** 그 주문의 추천 중 ID가 가장 큰 것. 같은 주문도 계산할 때마다 새 추천이 생기고 승인 뒤에도 그대로 보여 준다. */
	@Override
	@Transactional(readOnly = true)
	public Optional<String> latestRecommendationId(CurrentUser user, long orderId) {
		return this.recommendationRepository.findFirstByUserIdAndOrderIdOrderByRecommendationIdDesc(user.userId(), orderId)
			.map(entity -> RecommendationId.format(entity.getRecommendationId()));
	}

	@Override
	@Transactional(readOnly = true)
	public List<String> validUsing(CurrentUser user, long userCardId) {
		return this.cardBasisRepository.findRecommendationIdsUsingCard(user.userId(), userCardId)
			.stream()
			.filter(id -> this.recommendationService.invalidationOf(user, id).isEmpty())
			.map(RecommendationId::format)
			.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<String> invalidatedByMapping(CurrentUser user, List<String> candidates) {
		return candidates.stream()
			.map(RecommendationId::parse)
			.flatMap(Optional::stream)
			.filter(id -> this.recommendationService.invalidationOf(user, id)
				.filter(reason -> reason == InvalidationReason.MAPPING_CHANGED)
				.isPresent())
			.sorted(Comparator.naturalOrder())
			.map(RecommendationId::format)
			.toList();
	}

}
