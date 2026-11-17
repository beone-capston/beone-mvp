package com.beone.api.card.application;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 보유 카드 한 장의 매핑 기록(D-15). 가상 포트폴리오 카드에는 없다.
 *
 * <p>CODEF 후보 값({@code candidateCardProductId}·{@code candidateBasis})은 2차 CODEF가 만들므로 여기 두지 않는다. 응답에서는
 * 항상 {@code null}이다(결정 27 A2-10).
 *
 * @param status 매핑 상태
 * @param cardProductId {@code USER_CONFIRMED}일 때 상품 ID, 그 밖에는 {@code null}
 * @param confirmationMethod 확인 방법. {@code UNMAPPED}면 {@code null}
 * @param confirmedAt 확인 시각(서울). {@code UNMAPPED}면 {@code null}
 */
public record UserCardMapping(MappingStatus status, String cardProductId, ConfirmationMethod confirmationMethod,
		LocalDateTime confirmedAt) {

	/** 사용자 확인 매핑으로 계산한 결과에 붙이는 표시(D-15). */
	public static final String USER_CONFIRMED_NOTE = "사용자 확인 매핑, 공식 SKU·약관 버전 미검증";

	public UserCardMapping {
		Objects.requireNonNull(status, "status");
	}

	/** {@code USER_CONFIRMED}면 고정 표시, 그 밖에는 {@code null}. */
	public String note() {
		return (this.status == MappingStatus.USER_CONFIRMED) ? USER_CONFIRMED_NOTE : null;
	}

}
