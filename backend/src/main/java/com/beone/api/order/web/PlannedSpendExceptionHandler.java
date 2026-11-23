package com.beone.api.order.web;

import java.util.List;

import com.beone.api.common.web.ApiErrorCode;
import com.beone.api.common.web.ApiErrorResponse;
import com.beone.api.common.web.CommonApiErrorCode;
import com.beone.api.common.web.FieldViolation;
import com.beone.api.order.application.PlannedDateOutOfMonthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 예정 소비 기능의 요청 오류를 공통 실패 응답으로 바꾸는 기능별 처리기(docs/api.md 기능별 오류 처리).
 *
 * <p>웹 검증과 서비스가 "이번 달"을 각각 읽으므로, 그 사이 달이 바뀌면 서비스가 {@link PlannedDateOutOfMonthException}을 던진다.
 * 계약대로 400 {@code INVALID_REQUEST}이며, 어느 항목인지 정하지 않고 {@code items} 필드 오류로 알린다.
 */
@RestControllerAdvice(assignableTypes = PlannedSpendController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PlannedSpendExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(PlannedSpendExceptionHandler.class);

	@ExceptionHandler(PlannedDateOutOfMonthException.class)
	public ResponseEntity<ApiErrorResponse> handlePlannedDateOutOfMonth(PlannedDateOutOfMonthException ex) {
		ApiErrorCode errorCode = CommonApiErrorCode.INVALID_REQUEST;
		log.debug("API error code={} status={} exception={}", errorCode.code(), errorCode.status().value(),
				ex.getClass().getName());
		return ResponseEntity.status(errorCode.status())
			.contentType(MediaType.APPLICATION_JSON)
			.body(ApiErrorResponse.of(errorCode, List.of(new FieldViolation("items", InCurrentMonth.MESSAGE))));
	}

}
