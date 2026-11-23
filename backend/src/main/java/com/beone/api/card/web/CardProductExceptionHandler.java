package com.beone.api.card.web;

import java.util.List;

import com.beone.api.card.application.CardProductNotFoundException;
import com.beone.api.common.web.ApiErrorCode;
import com.beone.api.common.web.ApiErrorResponse;
import com.beone.api.common.web.CommonApiErrorCode;
import com.beone.api.common.web.FieldViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 카드 상품 API의 기능별 처리기(docs/api.md 기능별 오류 처리). 공통 처리기보다 먼저 적용된다.
 *
 * <p>입력값(상품 ID·날짜 원문)은 응답·로그에 남기지 않는다. {@code asOf} 오류의 상세 문구는 공통 처리기의 타입 오류
 * 문구와 같다.
 */
@RestControllerAdvice(assignableTypes = CardProductController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class CardProductExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(CardProductExceptionHandler.class);

	private static final String INVALID_FORMAT = "형식이 올바르지 않습니다.";

	@ExceptionHandler(CardProductNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleNotFound(CardProductNotFoundException ex) {
		return respond(CardProductErrorCode.CARD_PRODUCT_NOT_FOUND, List.of(), ex);
	}

	@ExceptionHandler(InvalidAsOfException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidAsOf(InvalidAsOfException ex) {
		return respond(CommonApiErrorCode.INVALID_REQUEST,
				List.of(new FieldViolation(AsOfParameter.NAME, INVALID_FORMAT)), ex);
	}

	private static ResponseEntity<ApiErrorResponse> respond(ApiErrorCode errorCode, List<FieldViolation> errors,
			Exception ex) {
		log.debug("API error code={} status={} exception={}", errorCode.code(), errorCode.status().value(),
				ex.getClass().getName());
		return ResponseEntity.status(errorCode.status())
			.contentType(MediaType.APPLICATION_JSON)
			.body(ApiErrorResponse.of(errorCode, errors));
	}

}
