package com.beone.api.payment.web;

import java.util.List;

import com.beone.api.common.web.ApiErrorCode;
import com.beone.api.common.web.ApiErrorResponse;
import com.beone.api.common.web.CommonApiErrorCode;
import com.beone.api.order.application.OrderNotFoundException;
import com.beone.api.payment.application.CancellationAmountExceededException;
import com.beone.api.payment.application.IdempotencyKeyReusedException;
import com.beone.api.payment.application.OrderNotPayableException;
import com.beone.api.payment.application.PaymentAlreadyCancelledException;
import com.beone.api.payment.application.RecommendationNotDeterminedException;
import com.beone.api.payment.application.RepresentativeCardNotEligibleException;
import com.beone.api.payment.application.SimulatedPaymentNotFoundException;
import com.beone.api.recommendation.application.OrderAlreadyPaidException;
import com.beone.api.recommendation.application.RecommendationNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 모의 결제 API의 기능별 처리기(docs/api.md 기능별 오류 처리). 공통 처리기보다 먼저 적용된다. 경로 값·키 값은 응답·로그에 남기지 않는다.
 *
 * <p>주문·추천 조회는 order·recommendation 공개 부품을 쓰므로 그 예외도 여기서 계약 코드로 바꾼다. 재계산이 주문 상태를 다시 보며 던질 수
 * 있는 추천 쪽 {@link OrderAlreadyPaidException}도 409 {@code ORDER_ALREADY_PAID}다.
 */
@RestControllerAdvice(assignableTypes = SimulatedPaymentController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class SimulatedPaymentExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentExceptionHandler.class);

	@ExceptionHandler({ InvalidIdempotencyKeyException.class, SimulateFailureNotAllowedException.class })
	ResponseEntity<ApiErrorResponse> handleInvalidRequest(RuntimeException ex) {
		return respond(CommonApiErrorCode.INVALID_REQUEST, ex);
	}

	@ExceptionHandler(OrderNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleOrderNotFound(OrderNotFoundException ex) {
		return respond(SimulatedPaymentErrorCode.ORDER_NOT_FOUND, ex);
	}

	@ExceptionHandler({ OrderNotPayableException.class, OrderAlreadyPaidException.class })
	ResponseEntity<ApiErrorResponse> handleOrderAlreadyPaid(RuntimeException ex) {
		return respond(SimulatedPaymentErrorCode.ORDER_ALREADY_PAID, ex);
	}

	@ExceptionHandler(RecommendationNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleRecommendationNotFound(RecommendationNotFoundException ex) {
		return respond(SimulatedPaymentErrorCode.RECOMMENDATION_NOT_FOUND, ex);
	}

	@ExceptionHandler(RecommendationNotDeterminedException.class)
	ResponseEntity<ApiErrorResponse> handleNotDetermined(RecommendationNotDeterminedException ex) {
		return respond(SimulatedPaymentErrorCode.RECOMMENDATION_NOT_DETERMINED, ex);
	}

	@ExceptionHandler(RepresentativeCardNotEligibleException.class)
	ResponseEntity<ApiErrorResponse> handleRepresentativeNotEligible(RepresentativeCardNotEligibleException ex) {
		return respond(SimulatedPaymentErrorCode.REPRESENTATIVE_CARD_NOT_ELIGIBLE, ex);
	}

	@ExceptionHandler(SimulatedPaymentNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handlePaymentNotFound(SimulatedPaymentNotFoundException ex) {
		return respond(SimulatedPaymentErrorCode.SIMULATED_PAYMENT_NOT_FOUND, ex);
	}

	@ExceptionHandler(CancellationAmountExceededException.class)
	ResponseEntity<ApiErrorResponse> handleAmountExceeded(CancellationAmountExceededException ex) {
		return respond(SimulatedPaymentErrorCode.CANCELLATION_AMOUNT_EXCEEDED, ex);
	}

	@ExceptionHandler(PaymentAlreadyCancelledException.class)
	ResponseEntity<ApiErrorResponse> handleAlreadyCancelled(PaymentAlreadyCancelledException ex) {
		return respond(SimulatedPaymentErrorCode.PAYMENT_ALREADY_CANCELLED, ex);
	}

	@ExceptionHandler(IdempotencyKeyReusedException.class)
	ResponseEntity<ApiErrorResponse> handleKeyReused(IdempotencyKeyReusedException ex) {
		return respond(SimulatedPaymentErrorCode.IDEMPOTENCY_KEY_REUSED, ex);
	}

	private static ResponseEntity<ApiErrorResponse> respond(ApiErrorCode errorCode, Exception ex) {
		log.debug("API error code={} status={} exception={}", errorCode.code(), errorCode.status().value(),
				ex.getClass().getName());
		return ResponseEntity.status(errorCode.status())
			.contentType(MediaType.APPLICATION_JSON)
			.body(ApiErrorResponse.of(errorCode, List.of()));
	}

}
