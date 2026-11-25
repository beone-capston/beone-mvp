package com.beone.api.ledger.web;

import java.util.List;

import com.beone.api.common.web.ApiErrorCode;
import com.beone.api.common.web.ApiErrorResponse;
import com.beone.api.common.web.CommonApiErrorCode;
import com.beone.api.common.web.FieldViolation;
import com.beone.api.ledger.application.LedgerCardNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 원장 조회 API의 기능별 처리기(docs/api.md 기능별 오류 처리). 공통 처리기보다 먼저 적용된다. 쿼리 값은 응답·로그에 남기지 않는다.
 */
@RestControllerAdvice(assignableTypes = LedgerTransactionController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class LedgerExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(LedgerExceptionHandler.class);

	// 공통 처리기의 타입 오류 문구와 같다(docs/api.md)
	private static final String INVALID_FORMAT = "형식이 올바르지 않습니다.";

	@ExceptionHandler(InvalidLedgerQueryException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidQuery(InvalidLedgerQueryException ex) {
		return respond(CommonApiErrorCode.INVALID_REQUEST, List.of(new FieldViolation(ex.field(), INVALID_FORMAT)), ex);
	}

	@ExceptionHandler(LedgerCardNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleCardNotFound(LedgerCardNotFoundException ex) {
		return respond(LedgerErrorCode.USER_CARD_NOT_FOUND, List.of(), ex);
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
