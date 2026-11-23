package com.beone.api.order.application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * 검증을 마친 예정 소비 한 건의 입력.
 *
 * @param plannedDate 예정일(이번 달)
 * @param plannedTime 예정 시각. 없으면 {@code null}
 * @param amountWon 금액(원, 1 이상)
 * @param merchantClass 허용 목록의 가맹점·업종 분류 코드
 * @param paymentMethod 결제 방식
 * @param merchantName 가맹점 이름. 없으면 {@code null}
 */
public record NewPlannedSpend(LocalDate plannedDate, LocalTime plannedTime, long amountWon, String merchantClass,
		PaymentMethod paymentMethod, String merchantName) {

	public NewPlannedSpend {
		Objects.requireNonNull(plannedDate, "plannedDate");
		if (amountWon < 1) {
			throw new IllegalArgumentException("amountWon must be positive");
		}
		Objects.requireNonNull(merchantClass, "merchantClass");
		Objects.requireNonNull(paymentMethod, "paymentMethod");
	}

}
