package com.beone.api.benefit.input;

import java.util.Objects;
import java.util.Optional;

import com.beone.api.benefit.card.CardId;

/**
 * 결제 전 카드 선택 설정.
 * {@code AUTO_RECOMMEND}에서 대표 카드는 혜택이 같은 카드 사이의 동률 판정에만 쓴다(D-12 3단계).
 */
public record SelectionPreference(SelectionMode mode, Optional<CardId> representativeCard) {

	public SelectionPreference {
		Objects.requireNonNull(mode, "mode");
		Objects.requireNonNull(representativeCard, "representativeCard");
		if (mode == SelectionMode.FIXED_REPRESENTATIVE && representativeCard.isEmpty()) {
			throw new IllegalArgumentException("fixed representative mode needs a representative card");
		}
	}

	public static SelectionPreference autoRecommend() {
		return new SelectionPreference(SelectionMode.AUTO_RECOMMEND, Optional.empty());
	}

}
