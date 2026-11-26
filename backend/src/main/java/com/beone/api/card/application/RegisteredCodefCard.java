package com.beone.api.card.application;

/**
 * CODEF 보유카드로 만든 보유 카드(#129, #268).
 *
 * @param userCardId 보유 카드 저장소 번호
 * @param candidateCardProductId 후보 상품 ID. 후보를 두지 않았으면 {@code null}({@code UNMAPPED})
 */
public record RegisteredCodefCard(long userCardId, String candidateCardProductId) {

	/**
	 * 후보를 두었으면 {@code CANDIDATE}, 아니면 {@code UNMAPPED}.
	 * @return 만든 카드의 매핑 상태
	 */
	public MappingStatus mappingStatus() {
		return (this.candidateCardProductId == null) ? MappingStatus.UNMAPPED : MappingStatus.CANDIDATE;
	}

}
