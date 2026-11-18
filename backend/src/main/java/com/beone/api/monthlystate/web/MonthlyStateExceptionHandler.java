package com.beone.api.monthlystate.web;

import java.util.List;

import com.beone.api.common.web.ApiErrorCode;
import com.beone.api.common.web.ApiErrorResponse;
import com.beone.api.common.web.CommonApiErrorCode;
import com.beone.api.common.web.FieldViolation;
import com.beone.api.monthlystate.application.MappingRequiredException;
import com.beone.api.monthlystate.application.MonthlyStateCardNotFoundException;
import com.beone.api.monthlystate.application.RuleMismatchException;
import com.beone.api.monthlystate.application.SourceConflictNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 월간 상태 API의 기능별 처리기(docs/api.md 기능별 오류 처리). 공통 처리기보다 먼저 적용된다. 경로·요청 값은 응답·로그에 남기지 않는다.
 */
@RestControllerAdvice(assignableTypes = MonthlyStateController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class MonthlyStateExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(MonthlyStateExceptionHandler.class);

	private static final String INVALID_FORMAT = "형식이 올바르지 않습니다.";

	@ExceptionHandler(InvalidMonthException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidMonth(InvalidMonthException ex) {
		return respond(CommonApiErrorCode.INVALID_REQUEST,
				List.of(new FieldViolation(MonthParameter.NAME, INVALID_FORMAT)), ex);
	}

	@ExceptionHandler(MonthlyStateCardNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleCardNotFound(MonthlyStateCardNotFoundException ex) {
		return respond(MonthlyStateErrorCode.USER_CARD_NOT_FOUND, List.of(), ex);
	}

	@ExceptionHandler(MappingRequiredException.class)
	ResponseEntity<ApiErrorResponse> handleMappingRequired(MappingRequiredException ex) {
		return respond(MonthlyStateErrorCode.USER_CARD_MAPPING_REQUIRED, List.of(), ex);
	}

	@ExceptionHandler(SourceConflictNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleSourceConflictNotFound(SourceConflictNotFoundException ex) {
		return respond(MonthlyStateErrorCode.SOURCE_CONFLICT_NOT_FOUND, List.of(), ex);
	}

	@ExceptionHandler(RuleMismatchException.class)
	ResponseEntity<ApiErrorResponse> handleRuleMismatch(RuleMismatchException ex) {
		return respond(CommonApiErrorCode.INVALID_REQUEST, ex.violations()
			.stream()
			.map(violation -> new FieldViolation(violation.field(), violation.message()))
			.toList(), ex);
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
