package com.beone.api.order.web;

import com.beone.api.common.web.ApiErrorResponse;
import com.beone.api.order.application.OrderNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 주문 기능 오류를 공통 실패 응답으로 바꾸는 기능별 처리기(docs/api.md 기능별 오류 처리). 공통 처리기보다 먼저 적용된다.
 *
 * <p>없거나 다른 사용자의 주문은 구분하지 않고 404 {@code ORDER_NOT_FOUND}다. 경로 값은 응답·로그에 남기지 않는다.
 */
@RestControllerAdvice(assignableTypes = OrderController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OrderExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(OrderExceptionHandler.class);

	@ExceptionHandler(OrderNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleOrderNotFound(OrderNotFoundException ex) {
		OrderErrorCode errorCode = OrderErrorCode.ORDER_NOT_FOUND;
		log.debug("API error code={} status={} exception={}", errorCode.code(), errorCode.status().value(),
				ex.getClass().getName());
		return ResponseEntity.status(errorCode.status())
			.contentType(MediaType.APPLICATION_JSON)
			.body(ApiErrorResponse.of(errorCode));
	}

}
