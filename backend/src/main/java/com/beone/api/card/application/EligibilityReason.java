package com.beone.api.card.application;

/**
 * 보유 카드가 추천 비교에서 빠지는 이유(D-15, D-19). 응답 {@code recommendationEligibility.reason} 값과 같다.
 */
public enum EligibilityReason {

	/** 매핑 미확인({@code UNMAPPED}·{@code CANDIDATE}). 사용자가 확인하면 추천에 들어갈 수 있다. */
	MAPPING_UNCONFIRMED,

	/** 규칙 카드 4종이 아님({@code NOT_SUPPORTED}). */
	NO_SUPPORTED_RULE,

	/** 사용자가 직접 추천에서 뺐다. D-18 K3 순서와 별개로 표시한다. */
	USER_EXCLUDED

}
