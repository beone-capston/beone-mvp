package com.beone.api.order.application;

import java.util.Optional;

import com.beone.api.user.application.CurrentUser;

/**
 * 주문으로 마지막에 계산한 추천 ID를 찾는 조회 포트(docs/api/orders.md {@code latestRecommendationId}, #120).
 *
 * <p>추천(recommendation)이 주문에 의존하므로 순환을 피하려고 인터페이스는 주문 쪽에 두고 구현은 추천 쪽에 둔다. 구현 빈이 없으면
 * {@link #NONE}을 써서 계산한 추천이 없는 것으로 본다.
 */
public interface LatestRecommendationLookup {

	/** 추천 구현이 없을 때 쓰는 기본 구현. 항상 비어 있다. */
	LatestRecommendationLookup NONE = (user, orderId) -> Optional.empty();

	/**
	 * 이 사용자의 이 주문으로 마지막에 계산한 추천.
	 * @param user 현재 사용자
	 * @param orderId 저장소 주문 번호
	 * @return 추천 외부 ID({@code rec-…}). 없으면 비어 있다
	 */
	Optional<String> latestRecommendationId(CurrentUser user, long orderId);

}
