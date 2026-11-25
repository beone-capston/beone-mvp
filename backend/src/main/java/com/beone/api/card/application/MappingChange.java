package com.beone.api.card.application;

import java.util.Objects;

/**
 * 매핑 확인·변경 입력. 웹 계층이 형식 검증을 마친 값이다(결정 27 A2-7).
 *
 * @param status {@code USER_CONFIRMED}·{@code NOT_SUPPORTED}·{@code UNMAPPED}. {@code CANDIDATE}는 서버만 만든다
 * @param cardProductId {@code USER_CONFIRMED}일 때만 값이 있다
 * @param confirmationMethod {@code USER_CONFIRMED}면 요청 값, {@code NOT_SUPPORTED}면 {@code MANUAL_SELECTION},
 * {@code UNMAPPED}면 {@code null}
 */
public record MappingChange(MappingStatus status, String cardProductId, ConfirmationMethod confirmationMethod) {

	public MappingChange {
		Objects.requireNonNull(status, "status");
		if (status == MappingStatus.CANDIDATE) {
			throw new IllegalArgumentException("CANDIDATE is server-only");
		}
		if ((status == MappingStatus.USER_CONFIRMED) != (cardProductId != null)) {
			throw new IllegalArgumentException("cardProductId is required only for USER_CONFIRMED");
		}
		if ((status == MappingStatus.UNMAPPED) != (confirmationMethod == null)) {
			throw new IllegalArgumentException("confirmationMethod is absent only for UNMAPPED");
		}
	}

}
