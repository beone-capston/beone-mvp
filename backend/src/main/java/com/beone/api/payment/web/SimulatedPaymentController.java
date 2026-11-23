package com.beone.api.payment.web;

import com.beone.api.common.web.ApiResponse;
import com.beone.api.payment.application.ApprovalDocument;
import com.beone.api.payment.application.CancellationDocument;
import com.beone.api.payment.application.PaymentDetail;
import com.beone.api.payment.application.SimulatedPaymentService;
import com.beone.api.user.application.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 모의 승인·상세 조회·취소 API 3개(docs/api/mock-payments.md). 현재 사용자는 {@link CurrentUser} 인자로만 받는다(D-09).
 *
 * <p>{@code local}·{@code demo} 프로필에서만 등록한다(다른 사용자 자원 API와 같음). 그래서 계약의 "{@code local}·{@code demo} 밖의
 * {@code simulateFailure}는 400"은 경로 자체가 없어 404 {@code NOT_FOUND}로 나타난다. 승인 결과 {@code FAILED}도 201이다(D-23-4).
 */
@RestController
@Profile({ "local", "demo" })
@RequestMapping("/api/v1/me")
public class SimulatedPaymentController {

	/** 키 최대 길이. 저장 열 크기와 같다. UUID 형식을 권장하지만 강제하지 않는다(계약 "UUID 형식 권장"). */
	static final int MAX_KEY_LENGTH = 255;

	private final SimulatedPaymentService simulatedPaymentService;

	public SimulatedPaymentController(SimulatedPaymentService simulatedPaymentService) {
		this.simulatedPaymentService = simulatedPaymentService;
	}

	@PostMapping("/orders/{orderId}/simulated-payments")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<ApprovalDocument> approve(CurrentUser currentUser, @PathVariable String orderId,
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
			@RequestBody(required = false) ApprovalRequest request) {
		ApprovalRequest body = (request == null) ? ApprovalRequest.EMPTY : request;
		return ApiResponse.of(this.simulatedPaymentService.approve(currentUser, orderId, body.toCommand(),
				requireKey(idempotencyKey)));
	}

	@GetMapping("/simulated-payments/{simulatedPaymentId}")
	public ApiResponse<PaymentDetail> getPayment(CurrentUser currentUser, @PathVariable String simulatedPaymentId) {
		return ApiResponse.of(this.simulatedPaymentService.get(currentUser, simulatedPaymentId));
	}

	@PostMapping("/simulated-payments/{simulatedPaymentId}/cancellations")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<CancellationDocument> cancel(CurrentUser currentUser, @PathVariable String simulatedPaymentId,
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
			@Valid @RequestBody CancellationRequest request) {
		return ApiResponse.of(this.simulatedPaymentService.cancel(currentUser, simulatedPaymentId,
				request.amountWon(), requireKey(idempotencyKey)));
	}

	private static String requireKey(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > MAX_KEY_LENGTH) {
			throw new InvalidIdempotencyKeyException();
		}
		return idempotencyKey;
	}

}
