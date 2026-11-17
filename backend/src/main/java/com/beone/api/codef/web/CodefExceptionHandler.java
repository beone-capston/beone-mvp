package com.beone.api.codef.web;

import com.beone.api.codef.application.CodefCardVerificationNotSupportedException;
import com.beone.api.codef.application.CodefLoginFailedException;
import com.beone.api.codef.application.CodefNotConnectedException;
import com.beone.api.codef.application.CodefPortfolioReadOnlyException;
import com.beone.api.codef.application.CodefUnavailableException;
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
 * CODEF 연결 API의 기능별 처리기(docs/api.md 기능별 오류 처리). 공통 처리기보다 먼저 적용된다.
 *
 * <p>요청 값·connectedId·CODEF 결과 코드는 응답·로그에 남기지 않는다. 로그는 오류 코드·상태·예외 클래스 이름만이다.
 */
@RestControllerAdvice(assignableTypes = CodefConnectionController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class CodefExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(CodefExceptionHandler.class);

	@ExceptionHandler(CodefNotConnectedException.class)
	ResponseEntity<ApiErrorResponse> handleNotConnected(CodefNotConnectedException ex) {
		return respond(CodefErrorCode.CODEF_NOT_CONNECTED, ex);
	}

	@ExceptionHandler(CodefLoginFailedException.class)
	ResponseEntity<ApiErrorResponse> handleLoginFailed(CodefLoginFailedException ex) {
		return respond(CodefErrorCode.CODEF_LOGIN_FAILED, ex);
	}

	@ExceptionHandler(CodefCardVerificationNotSupportedException.class)
	ResponseEntity<ApiErrorResponse> handleCardVerification(CodefCardVerificationNotSupportedException ex) {
		return respond(CodefErrorCode.CODEF_CARD_VERIFICATION_NOT_SUPPORTED, ex);
	}

	@ExceptionHandler(CodefUnavailableException.class)
	ResponseEntity<ApiErrorResponse> handleUnavailable(CodefUnavailableException ex) {
		return respond(CodefErrorCode.CODEF_UNAVAILABLE, ex);
	}

	@ExceptionHandler(CodefPortfolioReadOnlyException.class)
	ResponseEntity<ApiErrorResponse> handlePortfolioReadOnly(CodefPortfolioReadOnlyException ex) {
		return respond(CodefErrorCode.PORTFOLIO_READ_ONLY, ex);
	}

	private static ResponseEntity<ApiErrorResponse> respond(ApiErrorCode errorCode, Exception ex) {
		log.debug("API error code={} status={} exception={}", errorCode.code(), errorCode.status().value(),
				ex.getClass().getName());
		return ResponseEntity.status(errorCode.status())
			.contentType(MediaType.APPLICATION_JSON)
			.body(ApiErrorResponse.of(errorCode));
	}

}
