package com.beone.api.auth.web;

import com.beone.api.common.web.ApiErrorCode;
import com.beone.api.common.web.ApiErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 회원 인증 API의 기능별 처리기(docs/api.md 기능별 오류 처리). 공통 처리기보다 먼저 적용된다.
 *
 * <p>이 패키지의 컨트롤러에만 적용한다. 토큰·쿠키 값은 응답·로그에 남기지 않는다.
 */
@RestControllerAdvice(basePackageClasses = AuthExceptionHandler.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class AuthExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(AuthExceptionHandler.class);

	@ExceptionHandler(CsrfInvalidException.class)
	ResponseEntity<ApiErrorResponse> handleCsrfInvalid(CsrfInvalidException ex) {
		return respond(AuthErrorCode.CSRF_INVALID, ex);
	}

	private static ResponseEntity<ApiErrorResponse> respond(ApiErrorCode errorCode, Exception ex) {
		log.debug("API error code={} status={} exception={}", errorCode.code(), errorCode.status().value(),
				ex.getClass().getName());
		return ResponseEntity.status(errorCode.status())
			.contentType(MediaType.APPLICATION_JSON)
			.body(ApiErrorResponse.of(errorCode));
	}

}
