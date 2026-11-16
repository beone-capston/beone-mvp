package com.beone.api.order.web;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.beone.api.order.application.NewPlannedSpend;
import com.beone.api.order.application.PaymentMethod;
import com.beone.api.order.application.PlannedSpendService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * {@code PUT /api/v1/me/planned-spends} 요청 본문(docs/api/orders.md). {@code items}는 0~5건이고 빈 배열이면 모두 지운다.
 *
 * @param items 필수. 교체할 예정 소비 목록
 */
public record PlannedSpendsRequest(@NotNull(message = "필수 값입니다.")
		@Size(max = PlannedSpendService.MAX_ITEMS, message = "5건 이하여야 합니다.")
		List<@NotNull(message = "필수 값입니다.") @Valid Item> items) {

	List<NewPlannedSpend> toNewPlannedSpends() {
		return this.items.stream().map(Item::toNewPlannedSpend).toList();
	}

	/**
	 * 예정 소비 한 건.
	 *
	 * @param plannedDate 필수. {@code YYYY-MM-DD}, 서울 기준 이번 달
	 * @param amountWon 필수. 1 이상 JSON 정수
	 * @param merchantClass 필수. 허용 목록의 코드
	 * @param paymentMethod 필수. 결제 방식
	 * @param merchantName 선택. 최대 50자
	 * @param plannedTime 선택. {@code HH:mm:ss}, 없으면 {@code null}
	 */
	public record Item(
			@NotNull(message = "필수 값입니다.") @InCurrentMonth
			@JsonDeserialize(using = StrictDateDeserializer.class) LocalDate plannedDate,
			@NotNull(message = "필수 값입니다.") @Min(value = 1, message = "1 이상이어야 합니다.")
			@JsonDeserialize(using = StrictWonDeserializer.class) Long amountWon,
			@NotNull(message = "필수 값입니다.") @AllowedMerchantClass String merchantClass,
			@NotNull(message = "필수 값입니다.")
			@JsonDeserialize(using = StrictPaymentMethodDeserializer.class) PaymentMethod paymentMethod,
			@Size(max = 50, message = "50자 이하여야 합니다.") String merchantName,
			@JsonDeserialize(using = StrictTimeDeserializer.class) LocalTime plannedTime) {

		NewPlannedSpend toNewPlannedSpend() {
			return new NewPlannedSpend(this.plannedDate, this.plannedTime, this.amountWon, this.merchantClass,
					this.paymentMethod, this.merchantName);
		}

	}

}
