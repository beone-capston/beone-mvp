package com.beone.api.preference.application;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 결제 전 설정 조회·변경 결과(docs/api/preferences.md).
 *
 * @param selectionMode 카드 선택 방식
 * @param representativeUserCardId 대표 카드 외부 ID({@code uc-}+번호). 없으면 {@code null}
 * @param updatedAt 마지막 설정 시각(서울). 설정한 적이 없으면 {@code null}
 */
public record PaymentPreferenceView(PaymentSelectionMode selectionMode, String representativeUserCardId,
		LocalDateTime updatedAt) {

	/** 설정한 적이 없을 때의 미설정 표시. 저장된 기본값이 아니다. */
	public static final PaymentPreferenceView NOT_SET = new PaymentPreferenceView(PaymentSelectionMode.AUTO_RECOMMEND,
			null, null);

	public PaymentPreferenceView {
		Objects.requireNonNull(selectionMode, "selectionMode");
		if (selectionMode == PaymentSelectionMode.FIXED_REPRESENTATIVE && representativeUserCardId == null) {
			throw new IllegalArgumentException("fixed mode requires a representative card");
		}
	}

}
