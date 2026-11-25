package com.beone.api.preference.application;

import java.util.Objects;

/**
 * 결제 전 설정 변경 요청 값. 요청 형식 검사(필드 누락, 고정 모드인데 대표 카드 {@code null})는 웹 계층이 먼저 한다.
 *
 * @param selectionMode 카드 선택 방식
 * @param representativeUserCardId 대표 카드 외부 ID. {@code null}이면 대표 카드를 지운다
 */
public record PaymentPreferenceChange(PaymentSelectionMode selectionMode, String representativeUserCardId) {

	public PaymentPreferenceChange {
		Objects.requireNonNull(selectionMode, "selectionMode");
		if (selectionMode == PaymentSelectionMode.FIXED_REPRESENTATIVE && representativeUserCardId == null) {
			throw new IllegalArgumentException("fixed mode requires a representative card");
		}
	}

}
