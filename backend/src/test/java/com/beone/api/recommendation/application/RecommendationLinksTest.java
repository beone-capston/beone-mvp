package com.beone.api.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.beone.api.recommendation.persistence.RecommendationCardBasisRepository;
import com.beone.api.recommendation.persistence.RecommendationEntity;
import com.beone.api.recommendation.persistence.RecommendationRepository;
import com.beone.api.user.application.CurrentUser;
import com.beone.api.user.application.DemoUser;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 주문·보유 카드 조회 포트의 추천 구현(#120). 기대값은 docs/api/orders.md 172행("마지막에 계산한 추천")과 docs/api/cards.md 415행,
 * 관리자 승인 규칙(변경 직전 유효했고 이 카드 변경으로 {@code MAPPING_CHANGED}가 된 추천만, ID 오름차순)에서 썼다.
 */
class RecommendationLinksTest {

	private static final CurrentUser ME = new CurrentUser(1L, DemoUser.MY_CARDS);

	private final RecommendationRepository recommendations = mock(RecommendationRepository.class);

	private final RecommendationCardBasisRepository bases = mock(RecommendationCardBasisRepository.class);

	private final RecommendationService service = mock(RecommendationService.class);

	private final RecommendationLinks links = new RecommendationLinks(this.recommendations, this.bases, this.service);

	@Test
	void latestRecommendationIsTheLargestIdOfTheOrderOrEmpty() {
		RecommendationEntity latest = new RecommendationEntity(1L, 5001L, "2026-11", LocalDateTime.of(2026, 11, 17, 10, 0));
		ReflectionTestUtils.setField(latest, "recommendationId", 3002L);
		given(this.recommendations.findFirstByUserIdAndOrderIdOrderByRecommendationIdDesc(1L, 5001L))
			.willReturn(Optional.of(latest));
		given(this.recommendations.findFirstByUserIdAndOrderIdOrderByRecommendationIdDesc(1L, 5002L))
			.willReturn(Optional.empty());

		assertThat(this.links.latestRecommendationId(ME, 5001L)).contains("rec-3002");
		assertThat(this.links.latestRecommendationId(ME, 5002L)).isEmpty();
	}

	@Test
	void onlyRecommendationsValidBeforeTheChangeAreCandidates() {
		given(this.bases.findRecommendationIdsUsingCard(1L, 1001L)).willReturn(List.of(1L, 2L, 3L));
		given(this.service.invalidationOf(ME, 1L)).willReturn(Optional.empty());
		given(this.service.invalidationOf(ME, 2L)).willReturn(Optional.of(InvalidationReason.ORDER_PAID));
		given(this.service.invalidationOf(ME, 3L)).willReturn(Optional.empty());

		assertThat(this.links.validUsing(ME, 1001L)).containsExactly("rec-1", "rec-3");
	}

	@Test
	void onlyCandidatesNowMappingChangedAreInvalidatedInAscendingOrder() {
		given(this.service.invalidationOf(ME, 9L)).willReturn(Optional.of(InvalidationReason.MAPPING_CHANGED));
		given(this.service.invalidationOf(ME, 4L)).willReturn(Optional.of(InvalidationReason.MAPPING_CHANGED));
		given(this.service.invalidationOf(ME, 5L)).willReturn(Optional.of(InvalidationReason.MONTHLY_STATE_CHANGED));
		given(this.service.invalidationOf(ME, 6L)).willReturn(Optional.empty());

		assertThat(this.links.invalidatedByMapping(ME, List.of("rec-9", "rec-5", "rec-4", "rec-6", "rec-x")))
			.containsExactly("rec-4", "rec-9");
	}

}
