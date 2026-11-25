package com.beone.api.payment.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * {@code POST /api/v1/me/simulated-payments/{simulatedPaymentId}/cancellations} 요청 본문(docs/api/mock-payments.md).
 *
 * @param amountWon 필수. 1 이상 JSON 정수. 남은 취소 가능 금액 이하인지는 서비스가 본다(409)
 */
public record CancellationRequest(@NotNull(message = "필수 값입니다.") @Min(value = 1, message = "1 이상이어야 합니다.")
@JsonDeserialize(using = StrictWonDeserializer.class) Long amountWon) {

}
