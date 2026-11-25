package com.beone.api.payment.web;

import com.beone.api.common.web.ApiResponse;
import com.beone.api.payment.application.ApprovalDocument;
import com.beone.api.payment.application.CancellationDocument;
import com.beone.api.payment.application.PaymentDetail;
import com.beone.api.payment.application.SimulatedPaymentService;
import com.beone.api.user.application.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
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
 * <p>{@code local}·{@code demo}·{@code member} 프로필에서 등록한다. 그 밖의 환경에서는 경로가 없어 404 {@code NOT_FOUND}다.
 * {@code member}에서는 회원 세션의 장부가 현재 사용자이고, 세션 401·쓰기 CSRF 403·캐시 금지는 {@code /api/v1/me/**} 공통
 * 인터셉터·필터가 컨트롤러 전에 처리한다(결정 37).
 *
 * <p>{@code simulateFailure=true}는 {@code local}·{@code demo}에서만 받아 201 {@code FAILED}로 기록한다(D-23-4). 그 밖에서는
 * {@link SimulateFailurePolicy}가 서비스 호출 전에 400 {@code INVALID_REQUEST}로 거절하므로 결제·멱등 키를 저장하지 않는다.
 */
@RestController
@Profile({ "local", "demo", "member" })
@RequestMapping("/api/v1/me")
public class SimulatedPaymentController {

	/** 키 최대 길이. 저장 열 크기와 같다. UUID 형식을 권장하지만 강제하지 않는다(계약 "UUID 형식 권장"). */
	static final int MAX_KEY_LENGTH = 255;

	private final SimulatedPaymentService simulatedPaymentService;

	private final SimulateFailurePolicy simulateFailurePolicy;

	public SimulatedPaymentController(SimulatedPaymentService simulatedPaymentService, Environment environment) {
		this.simulatedPaymentService = simulatedPaymentService;
		this.simulateFailurePolicy = SimulateFailurePolicy.of(environment);
	}

	@PostMapping("/orders/{orderId}/simulated-payments")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<ApprovalDocument> approve(CurrentUser currentUser, @PathVariable String orderId,
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
			@RequestBody(required = false) ApprovalRequest request) {
		ApprovalRequest body = (request == null) ? ApprovalRequest.EMPTY : request;
		this.simulateFailurePolicy.check(body);
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
