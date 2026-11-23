package com.beone.api.order.application;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 저장된 예정 소비 한 건.
 *
 * @param plannedSpendId 외부 ID({@code ps-…})
 * @param plannedDate 예정일
 * @param amountWon 금액(원)
 * @param merchantClass 가맹점·업종 분류 코드
 * @param paymentMethod 결제 방식
 * @param merchantName 가맹점 이름. 없으면 {@code null}
 * @param plannedTime 예정 시각. 없으면 {@code null}
 */
public record PlannedSpendDetail(String plannedSpendId, LocalDate plannedDate, long amountWon, String merchantClass,
		PaymentMethod paymentMethod, String merchantName, LocalTime plannedTime) {

}
