package com.beone.api.user.web;

import com.beone.api.common.web.ApiErrorCode;
import com.beone.api.common.web.ApiErrorResponse;
import com.beone.api.common.web.CommonApiErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 시연 사용자 헤더 오류를 공통 실패 응답으로 바꾸는 기능별 처리기(docs/api.md 기능별 오류 처리).
 *
 * <p>공통 처리기보다 먼저 적용되도록 우선순위를 지정한다. 헤더 값은 응답·로그에 남기지 않는다.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DemoUserExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(DemoUserExceptionHandler.class);

	@ExceptionHandler(InvalidDemoUserHeaderException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidDemoUserHeader(InvalidDemoUserHeaderException ex) {
		ApiErrorCode errorCode = CommonApiErrorCode.INVALID_REQUEST;
		log.debug("API error code={} status={} exception={}", errorCode.code(), errorCode.status().value(),
				ex.getClass().getName());
		return ResponseEntity.status(errorCode.status())
			.contentType(MediaType.APPLICATION_JSON)
			.body(ApiErrorResponse.of(errorCode));
	}

}
