package com.beone.api.order.web;

import java.time.LocalDate;
import java.time.LocalTime;

import com.beone.api.order.application.NewOrder;
import com.beone.api.order.application.PaymentMethod;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * {@code POST /api/v1/me/orders} 요청 본문(docs/api/orders.md).
 *
 * @param amountWon 필수. 1 이상 JSON 정수
 * @param orderDate 필수. {@code YYYY-MM-DD}
 * @param orderTime 선택. {@code HH:mm:ss}, 없으면 {@code null}
 * @param merchantName 선택. 최대 50자, 표시용
 * @param merchantClass 필수. 허용 목록의 코드
 * @param paymentMethod 필수. {@code DIRECT}·{@code ELIGIBLE_SIMPLE_PAY}·{@code AUTOPAY}
 */
public record CreateOrderRequest(
		@NotNull(message = "필수 값입니다.") @Min(value = 1, message = "1 이상이어야 합니다.")
		@JsonDeserialize(using = StrictWonDeserializer.class) Long amountWon,
		@NotNull(message = "필수 값입니다.") @JsonDeserialize(using = StrictDateDeserializer.class) LocalDate orderDate,
		@JsonDeserialize(using = StrictTimeDeserializer.class) LocalTime orderTime,
		@Size(max = 50, message = "50자 이하여야 합니다.") String merchantName,
		@NotNull(message = "필수 값입니다.") @AllowedMerchantClass String merchantClass,
		@NotNull(message = "필수 값입니다.")
		@JsonDeserialize(using = StrictPaymentMethodDeserializer.class) PaymentMethod paymentMethod) {

	NewOrder toNewOrder() {
		return new NewOrder(this.amountWon, this.orderDate, this.orderTime, this.merchantName, this.merchantClass,
				this.paymentMethod);
	}

}
