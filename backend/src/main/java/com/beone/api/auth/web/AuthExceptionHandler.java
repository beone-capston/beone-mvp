package com.beone.api.auth.web;

import java.time.Duration;
import java.util.List;

import com.beone.api.auth.application.AuthTokenInvalidException;
import com.beone.api.auth.application.MailRateLimitedException;
import com.beone.api.common.web.ApiErrorCode;
import com.beone.api.common.web.ApiErrorResponse;
import com.beone.api.common.web.CommonApiErrorCode;
import com.beone.api.common.web.FieldViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
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

	/** 탈퇴 비밀번호 불일치 필드 오류 문구. */
	static final String PASSWORD_MISMATCH_MESSAGE = "비밀번호가 일치하지 않습니다.";

	@ExceptionHandler(CsrfInvalidException.class)
	ResponseEntity<ApiErrorResponse> handleCsrfInvalid(CsrfInvalidException ex) {
		return respond(AuthErrorCode.CSRF_INVALID, ex);
	}

	@ExceptionHandler(AuthTokenInvalidException.class)
	ResponseEntity<ApiErrorResponse> handleTokenInvalid(AuthTokenInvalidException ex) {
		return respond(AuthErrorCode.AUTH_TOKEN_INVALID, ex);
	}

	@ExceptionHandler(UnauthenticatedException.class)
	ResponseEntity<ApiErrorResponse> handleUnauthenticated(UnauthenticatedException ex) {
		return respond(AuthErrorCode.UNAUTHENTICATED, ex);
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex) {
		return respond(AuthErrorCode.INVALID_CREDENTIALS, ex);
	}

	@ExceptionHandler(EmailNotVerifiedException.class)
	ResponseEntity<ApiErrorResponse> handleEmailNotVerified(EmailNotVerifiedException ex) {
		return respond(AuthErrorCode.EMAIL_NOT_VERIFIED, ex);
	}

	/** 탈퇴 비밀번호 재확인 실패: 세션은 유효하므로 401 대신 400과 {@code password} 필드 오류. */
	@ExceptionHandler(PasswordConfirmationFailedException.class)
	ResponseEntity<ApiErrorResponse> handlePasswordConfirmationFailed(PasswordConfirmationFailedException ex) {
		log.debug("API error code={} status={} exception={}", CommonApiErrorCode.INVALID_REQUEST.code(),
				CommonApiErrorCode.INVALID_REQUEST.status().value(), ex.getClass().getName());
		return ResponseEntity.status(CommonApiErrorCode.INVALID_REQUEST.status())
			.contentType(MediaType.APPLICATION_JSON)
			.body(ApiErrorResponse.of(CommonApiErrorCode.INVALID_REQUEST,
					List.of(new FieldViolation("password", PASSWORD_MISMATCH_MESSAGE))));
	}

	/** 메일 요청 제한: 429와 남은 초({@code Retry-After}). */
	@ExceptionHandler(MailRateLimitedException.class)
	ResponseEntity<ApiErrorResponse> handleRateLimited(MailRateLimitedException ex) {
		return rateLimited(ex.retryAfter(), ex);
	}

	/** 로그인 실패 제한: 429와 남은 초({@code Retry-After}). */
	@ExceptionHandler(LoginRateLimitedException.class)
	ResponseEntity<ApiErrorResponse> handleLoginRateLimited(LoginRateLimitedException ex) {
		return rateLimited(ex.retryAfter(), ex);
	}

	private static ResponseEntity<ApiErrorResponse> rateLimited(Duration retryAfter, Exception ex) {
		log.debug("API error code={} status={} exception={}", AuthErrorCode.RATE_LIMITED.code(),
				AuthErrorCode.RATE_LIMITED.status().value(), ex.getClass().getName());
		return ResponseEntity.status(AuthErrorCode.RATE_LIMITED.status())
			.header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter.toSeconds()))
			.contentType(MediaType.APPLICATION_JSON)
			.body(ApiErrorResponse.of(AuthErrorCode.RATE_LIMITED));
	}

	private static ResponseEntity<ApiErrorResponse> respond(ApiErrorCode errorCode, Exception ex) {
		log.debug("API error code={} status={} exception={}", errorCode.code(), errorCode.status().value(),
				ex.getClass().getName());
		return ResponseEntity.status(errorCode.status())
			.contentType(MediaType.APPLICATION_JSON)
			.body(ApiErrorResponse.of(errorCode));
	}

}
