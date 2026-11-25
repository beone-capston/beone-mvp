package com.beone.api.recommendation.web;

import java.util.List;

import com.beone.api.common.web.ApiErrorCode;
import com.beone.api.common.web.ApiErrorResponse;
import com.beone.api.order.application.OrderNotFoundException;
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
 * 추천 API의 기능별 처리기(docs/api.md 기능별 오류 처리). 공통 처리기보다 먼저 적용된다. 경로 값은 응답·로그에 남기지 않는다.
 *
 * <p>주문 조회는 order의 공개 부품을 쓰므로 그 예외({@link OrderNotFoundException})를 여기서 404 {@code ORDER_NOT_FOUND}로 바꾼다.
 */
@RestControllerAdvice(assignableTypes = RecommendationController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class RecommendationExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(RecommendationExceptionHandler.class);

	@ExceptionHandler(OrderNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleOrderNotFound(OrderNotFoundException ex) {
		return respond(RecommendationErrorCode.ORDER_NOT_FOUND, ex);
	}

	@ExceptionHandler(OrderAlreadyPaidException.class)
	ResponseEntity<ApiErrorResponse> handleOrderAlreadyPaid(OrderAlreadyPaidException ex) {
		return respond(RecommendationErrorCode.ORDER_ALREADY_PAID, ex);
	}

	@ExceptionHandler(RecommendationNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleRecommendationNotFound(RecommendationNotFoundException ex) {
		return respond(RecommendationErrorCode.RECOMMENDATION_NOT_FOUND, ex);
	}

	private static ResponseEntity<ApiErrorResponse> respond(ApiErrorCode errorCode, Exception ex) {
		log.debug("API error code={} status={} exception={}", errorCode.code(), errorCode.status().value(),
				ex.getClass().getName());
		return ResponseEntity.status(errorCode.status())
			.contentType(MediaType.APPLICATION_JSON)
			.body(ApiErrorResponse.of(errorCode, List.of()));
	}

}
