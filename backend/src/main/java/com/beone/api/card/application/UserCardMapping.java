package com.beone.api.card.application;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 보유 카드 한 장의 매핑 기록(D-15). 가상 포트폴리오 카드에는 없다.
 *
 * <p>CODEF 후보 상품({@code candidateCardProductId})은 CODEF 카드명이 이름 사전과 정확히 일치할 때 동기화가 둔다(#268). 후보 근거
 * ({@code candidateBasis})는 여기 두지 않으며 응답에서 {@code null}이다.
 *
 * @param status 매핑 상태
 * @param cardProductId {@code USER_CONFIRMED}일 때 상품 ID, 그 밖에는 {@code null}
 * @param confirmationMethod 확인 방법. {@code UNMAPPED}·{@code CANDIDATE}면 {@code null}
 * @param confirmedAt 확인 시각(서울). {@code UNMAPPED}·{@code CANDIDATE}면 {@code null}
 * @param candidateCardProductId {@code CANDIDATE}일 때 후보 상품 ID, 그 밖에는 {@code null}
 */
public record UserCardMapping(MappingStatus status, String cardProductId, ConfirmationMethod confirmationMethod,
		LocalDateTime confirmedAt, String candidateCardProductId) {

	/** 사용자 확인 매핑으로 계산한 결과에 붙이는 표시(D-15). */
	public static final String USER_CONFIRMED_NOTE = "사용자 확인 매핑, 공식 SKU·약관 버전 미검증";

	public UserCardMapping {
		Objects.requireNonNull(status, "status");
		if ((status == MappingStatus.CANDIDATE) != (candidateCardProductId != null)) {
			throw new IllegalArgumentException("candidateCardProductId is required only for CANDIDATE");
		}
	}

	/**
	 * 후보가 없는 매핑({@code CANDIDATE}가 아닌 상태).
	 * @param status 매핑 상태
	 * @param cardProductId {@code USER_CONFIRMED}일 때 상품 ID
	 * @param confirmationMethod 확인 방법
	 * @param confirmedAt 확인 시각(서울)
	 */
	public UserCardMapping(MappingStatus status, String cardProductId, ConfirmationMethod confirmationMethod,
			LocalDateTime confirmedAt) {
		this(status, cardProductId, confirmationMethod, confirmedAt, null);
	}

	/**
	 * {@code USER_CONFIRMED}면 고정 표시, 그 밖에는 {@code null}.
	 * @return 고정 표시 문구, {@code USER_CONFIRMED}가 아니면 {@code null}
	 */
	public String note() {
		return (this.status == MappingStatus.USER_CONFIRMED) ? USER_CONFIRMED_NOTE : null;
	}

}
