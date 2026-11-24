package com.beone.api.card.application;

import java.util.Objects;

/**
 * 수동 등록 입력. 웹 계층이 형식 검증을 마친 값이다.
 *
 * @param displayName 앞뒤 공백을 뗀 표시 이름(1~30자)
 * @param cardProductId 사용자가 고른 상품. 없으면 {@code null}
 * @param notSupported 4종이 아니라고 골랐는지. {@code cardProductId}와 함께 {@code true}일 수 없다
 */
public record NewUserCard(String displayName, String cardProductId, boolean notSupported) {

	public NewUserCard {
		Objects.requireNonNull(displayName, "displayName");
		if (notSupported && cardProductId != null) {
			throw new IllegalArgumentException("cardProductId and notSupported are exclusive");
		}
	}

	/**
	 * 저장할 매핑 상태. 상품을 고르면 {@code USER_CONFIRMED}, 4종이 아니면 {@code NOT_SUPPORTED}, 둘 다 없으면 {@code UNMAPPED}.
	 * @return 저장할 매핑 상태
	 */
	public MappingStatus mappingStatus() {
		if (this.cardProductId != null) {
			return MappingStatus.USER_CONFIRMED;
		}
		return this.notSupported ? MappingStatus.NOT_SUPPORTED : MappingStatus.UNMAPPED;
	}

}
