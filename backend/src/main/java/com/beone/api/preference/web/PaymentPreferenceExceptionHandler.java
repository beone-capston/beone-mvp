package com.beone.api.preference.web;

import com.beone.api.card.application.UserCardNotFoundException;
import com.beone.api.common.web.ApiErrorCode;
import com.beone.api.common.web.ApiErrorResponse;
import com.beone.api.preference.application.RepresentativeCardNotEligibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 결제 전 설정 API의 기능별 처리기(docs/api.md 기능별 오류 처리). 공통 처리기보다 먼저 적용된다.
 *
 * <p>없거나 다른 사용자·해제한 카드, 형식이 틀린 카드 ID는 구분하지 않고 404다. 요청 값은 응답·로그에 남기지 않는다.
 */
@RestControllerAdvice(assignableTypes = PaymentPreferenceController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class PaymentPreferenceExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(PaymentPreferenceExceptionHandler.class);

	@ExceptionHandler(UserCardNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleUserCardNotFound(UserCardNotFoundException ex) {
		return respond(PaymentPreferenceErrorCode.USER_CARD_NOT_FOUND, ex);
	}

	@ExceptionHandler(RepresentativeCardNotEligibleException.class)
	ResponseEntity<ApiErrorResponse> handleNotEligible(RepresentativeCardNotEligibleException ex) {
		return respond(PaymentPreferenceErrorCode.REPRESENTATIVE_CARD_NOT_ELIGIBLE, ex);
	}

	private static ResponseEntity<ApiErrorResponse> respond(ApiErrorCode errorCode, Exception ex) {
		log.debug("API error code={} status={} exception={}", errorCode.code(), errorCode.status().value(),
				ex.getClass().getName());
		return ResponseEntity.status(errorCode.status())
			.contentType(MediaType.APPLICATION_JSON)
			.body(ApiErrorResponse.of(errorCode));
	}

}
