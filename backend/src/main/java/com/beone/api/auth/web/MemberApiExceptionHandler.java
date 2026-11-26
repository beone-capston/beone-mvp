package com.beone.api.auth.web;

import com.beone.api.common.web.ApiErrorCode;
import com.beone.api.common.web.ApiErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 회원 프로필 업무 API의 세션·CSRF 실패를 인증 API와 같은 응답으로 바꾼다(결정 37 D5·D6). 업무 컨트롤러는 다른 패키지에 있어
 * {@link AuthExceptionHandler}(이 패키지 전용)가 닿지 않으므로 전역으로 둔다. 다루는 예외는 이 패키지의 인터셉터만 던진다.
 *
 * <p>{@code FORBIDDEN}은 쓰지 않는다(예약 유지). 쿠키·토큰 값은 응답·로그에 남기지 않는다.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Profile("member")
class MemberApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(MemberApiExceptionHandler.class);

	@ExceptionHandler(UnauthenticatedException.class)
	ResponseEntity<ApiErrorResponse> handleUnauthenticated(UnauthenticatedException ex) {
		return respond(AuthErrorCode.UNAUTHENTICATED, ex);
	}

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
