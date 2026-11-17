package com.beone.api.card.web;

import java.util.List;

import com.beone.api.card.application.CandidateMismatchException;
import com.beone.api.card.application.CardProductNotFoundException;
import com.beone.api.card.application.PortfolioReadOnlyException;
import com.beone.api.card.application.RepresentativeCardInUseException;
import com.beone.api.card.application.UserCardDuplicatedException;
import com.beone.api.card.application.UserCardNotFoundException;
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
 * 현재 사용자·보유 카드 API의 기능별 처리기(docs/api.md 기능별 오류 처리). 공통 처리기보다 먼저 적용된다.
 *
 * <p>없거나 다른 사용자·해제한 카드는 구분하지 않고 404다. 경로 값·요청 값은 응답·로그에 남기지 않는다.
 */
@RestControllerAdvice(assignableTypes = { UserCardController.class, CurrentUserController.class })
@Order(Ordered.HIGHEST_PRECEDENCE)
class UserCardExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(UserCardExceptionHandler.class);

	private static final String CANDIDATE_MISMATCH = "현재 후보와 일치하지 않습니다.";

	@ExceptionHandler(UserCardNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleUserCardNotFound(UserCardNotFoundException ex) {
		return respond(UserCardErrorCode.USER_CARD_NOT_FOUND, List.of(), ex);
	}

	@ExceptionHandler(UserCardDuplicatedException.class)
	ResponseEntity<ApiErrorResponse> handleDuplicated(UserCardDuplicatedException ex) {
		return respond(UserCardErrorCode.USER_CARD_DUPLICATED, List.of(), ex);
	}

	@ExceptionHandler(PortfolioReadOnlyException.class)
	ResponseEntity<ApiErrorResponse> handleReadOnly(PortfolioReadOnlyException ex) {
		return respond(UserCardErrorCode.PORTFOLIO_READ_ONLY, List.of(), ex);
	}

	// 결제 전 설정(preference)의 대표 카드 보호 구현이 던진다(RepresentativeCardGuard).
	@ExceptionHandler(RepresentativeCardInUseException.class)
	ResponseEntity<ApiErrorResponse> handleRepresentativeInUse(RepresentativeCardInUseException ex) {
		return respond(UserCardErrorCode.REPRESENTATIVE_CARD_IN_USE, List.of(), ex);
	}

	@ExceptionHandler(CardProductNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleCardProductNotFound(CardProductNotFoundException ex) {
		return respond(CardProductErrorCode.CARD_PRODUCT_NOT_FOUND, List.of(), ex);
	}

	@ExceptionHandler(CandidateMismatchException.class)
	ResponseEntity<ApiErrorResponse> handleCandidateMismatch(CandidateMismatchException ex) {
		return respond(CommonApiErrorCode.INVALID_REQUEST,
				List.of(new FieldViolation("confirmationMethod", CANDIDATE_MISMATCH)), ex);
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
