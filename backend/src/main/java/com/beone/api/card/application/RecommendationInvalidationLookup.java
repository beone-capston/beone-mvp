package com.beone.api.card.application;

import java.util.List;

import com.beone.api.user.application.CurrentUser;

/**
 * 보유 카드 변경으로 무효가 된 추천을 찾는 조회 포트(docs/api/cards.md {@code invalidatedRecommendationIds}, #120).
 *
 * <p>추천(recommendation)이 보유 카드에 의존하므로 순환을 피하려고 인터페이스는 보유 카드 쪽에 두고 구현은 추천 쪽에 둔다. 구현 빈이
 * 없으면 {@link #NONE}을 써서 무효화된 추천이 없는 것으로 본다. 보유 카드 변경과 같은 트랜잭션에서 다음 순서로 부른다.
 * <ol>
 * <li>변경 전 {@link #validUsing}: 이 카드를 근거로 쓰고 지금 지연 판정으로 유효한 추천</li>
 * <li>변경 저장 뒤 {@link #invalidatedByMapping}: 그중 이제 {@code MAPPING_CHANGED}가 된 추천</li>
 * </ol>
 * 이미 무효였던 추천은 넣지 않는다(결정 28 보충).
 */
public interface RecommendationInvalidationLookup {

	/** 추천 구현이 없을 때 쓰는 기본 구현. 항상 빈 목록이다. */
	RecommendationInvalidationLookup NONE = new RecommendationInvalidationLookup() {

		@Override
		public List<String> validUsing(CurrentUser user, long userCardId) {
			return List.of();
		}

		@Override
		public List<String> invalidatedByMapping(CurrentUser user, List<String> candidates) {
			return List.of();
		}

	};

	/**
	 * 변경 전에 부른다.
	 * @param user 현재 사용자
	 * @param userCardId 저장소 카드 번호
	 * @return 이 카드를 근거로 쓰고 지금 유효한 추천 외부 ID
	 */
	List<String> validUsing(CurrentUser user, long userCardId);

	/**
	 * 변경을 저장한 뒤 부른다.
	 * @param user 현재 사용자
	 * @param candidates {@link #validUsing}의 결과
	 * @return 이제 {@code MAPPING_CHANGED}로 무효가 된 추천 외부 ID, ID 오름차순
	 */
	List<String> invalidatedByMapping(CurrentUser user, List<String> candidates);

}
