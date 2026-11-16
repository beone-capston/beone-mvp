package com.beone.api.order.web;

import java.util.List;

import com.beone.api.order.application.PaymentMethod;
import com.beone.api.order.application.PlannedSpendDetail;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 예정 소비 조회·교체의 {@code data}(docs/api/orders.md).
 *
 * @param items 이번 달 예정 소비. 요청 {@code items} 순서
 * @param usedInRecommendation 1차에서는 항상 {@code false}(D-18 K4)
 */
public record PlannedSpendsResponse(List<Item> items, boolean usedInRecommendation) {

	public PlannedSpendsResponse {
		items = List.copyOf(items);
	}

	static PlannedSpendsResponse from(List<PlannedSpendDetail> details) {
		return new PlannedSpendsResponse(details.stream().map(Item::from).toList(), false);
	}

	/**
	 * 예정 소비 한 건. 값이 없는 필드도 {@code null}로 쓴다.
	 *
	 * @param plannedSpendId 예정 소비 ID({@code ps-…})
	 * @param plannedDate {@code YYYY-MM-DD}
	 * @param amountWon 금액(원)
	 * @param merchantClass 가맹점·업종 분류 코드
	 * @param paymentMethod 결제 방식
	 * @param merchantName 가맹점 이름 또는 {@code null}
	 * @param plannedTime {@code HH:mm:ss} 또는 {@code null}
	 */
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record Item(String plannedSpendId, String plannedDate, long amountWon, String merchantClass,
			PaymentMethod paymentMethod, String merchantName, String plannedTime) {

		static Item from(PlannedSpendDetail detail) {
			return new Item(detail.plannedSpendId(), OrderValueFormats.date(detail.plannedDate()), detail.amountWon(),
					detail.merchantClass(), detail.paymentMethod(), detail.merchantName(),
					OrderValueFormats.time(detail.plannedTime()));
		}

	}

}
