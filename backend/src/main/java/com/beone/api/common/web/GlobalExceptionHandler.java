package com.beone.api.common.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 공통 실패 응답을 만드는 최하위 우선순위 예외 처리기.
 *
 * <p>표준 MVC 예외는 상위 클래스가 분기하고 {@link #handleExceptionInternal}에서 원래 상태·헤더를 유지한 채
 * 본문만 바꾼다. 나머지 예외는 {@link #handleUnexpected}가 안전한 500으로 처리한다. 기능별 처리는
 * 컨트롤러 안의 {@code @ExceptionHandler}나 더 높은 우선순위의 advice에서 한다(docs/api.md).
 *
 * <p>응답과 로그에는 입력값·예외 메시지·스택을 남기지 않는다. 로그는 오류 코드·상태·예외 클래스 이름만 쓰고,
 * 5xx에는 원인 예외 클래스 목록과 첫 {@code com.beone} 발생 위치를 더한다.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	static final String TYPE_MISMATCH_MESSAGE = "형식이 올바르지 않습니다.";

	static final String REQUIRED_MESSAGE = "필수 값입니다.";

	private static final String APP_PACKAGE_PREFIX = "com.beone.";

	private static final int MAX_CAUSE_DEPTH = 20;

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
		return respond(ex, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
	}

	// 상위 구현은 요청 방식이 담긴 예외 메시지를 WARN으로 남기므로 로그 없이 공통 처리로 넘긴다.
	@Override
	protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return handleExceptionInternal(ex, null, headers, status, request);
	}

	// 상위 구현의 ProblemDetail 본문과 커밋된 응답의 예외 원문 WARN 로그를 사용하지 않는다.
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		return respond(ex, headers, statusCode, request);
	}

	private ResponseEntity<Object> respond(Exception ex, HttpHeaders headers, HttpStatusCode status,
			WebRequest request) {
		ApiErrorCode errorCode = errorCodeFor(status);
		boolean committed = isCommitted(request);
		logFailure(errorCode, status, ex, committed);
		if (committed) {
			return null;
		}
		List<FieldViolation> errors = (errorCode == CommonApiErrorCode.INVALID_REQUEST) ? violationsOf(ex) : List.of();
		// Accept 협상 실패로 오류 본문 작성이 다시 실패하지 않도록 JSON 형식을 고정한다.
		HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.putAll(headers);
		responseHeaders.setContentType(MediaType.APPLICATION_JSON);
		return createResponseEntity(ApiErrorResponse.of(errorCode, errors), responseHeaders, status, request);
	}

	/** 404·405·415 외의 상태는 4xx면 INVALID_REQUEST, 그 밖은 INTERNAL_SERVER_ERROR로 포괄한다. */
	static ApiErrorCode errorCodeFor(HttpStatusCode status) {
		return switch (status.value()) {
			case 404 -> CommonApiErrorCode.NOT_FOUND;
			case 405 -> CommonApiErrorCode.METHOD_NOT_ALLOWED;
			case 415 -> CommonApiErrorCode.UNSUPPORTED_MEDIA_TYPE;
			default -> status.is4xxClientError() ? CommonApiErrorCode.INVALID_REQUEST
					: CommonApiErrorCode.INTERNAL_SERVER_ERROR;
		};
	}

	private static List<FieldViolation> violationsOf(Exception ex) {
		return switch (ex) {
			case MethodArgumentNotValidException e -> violationsOf(e.getBindingResult());
			case HandlerMethodValidationException e when !e.isForReturnValue() -> violationsOf(e);
			case TypeMismatchException e -> List.of(new FieldViolation(e.getPropertyName(), TYPE_MISMATCH_MESSAGE));
			case MissingServletRequestParameterException e ->
				List.of(new FieldViolation(e.getParameterName(), REQUIRED_MESSAGE));
			case MissingPathVariableException e -> List.of(new FieldViolation(e.getVariableName(), REQUIRED_MESSAGE));
			case MissingServletRequestPartException e ->
				List.of(new FieldViolation(e.getRequestPartName(), REQUIRED_MESSAGE));
			default -> List.of();
		};
	}

	private static List<FieldViolation> violationsOf(Errors errors) {
		List<FieldViolation> violations = new ArrayList<>();
		for (FieldError error : errors.getFieldErrors()) {
			// 바인딩 실패의 기본 메시지에는 입력 원문이 들어가므로 고정 문구로 바꾼다.
			String message = error.isBindingFailure() ? TYPE_MISMATCH_MESSAGE : messageOf(error);
			violations.add(new FieldViolation(error.getField(), message));
		}
		errors.getGlobalErrors().forEach(error -> violations.add(new FieldViolation(null, messageOf(error))));
		return violations;
	}

	private static List<FieldViolation> violationsOf(HandlerMethodValidationException ex) {
		List<FieldViolation> violations = new ArrayList<>();
		for (ParameterValidationResult result : ex.getParameterValidationResults()) {
			if (result instanceof ParameterErrors errors) {
				violations.addAll(violationsOf(errors));
				continue;
			}
			String field = parameterName(result.getMethodParameter());
			result.getResolvableErrors().forEach(error -> violations.add(new FieldViolation(field, messageOf(error))));
		}
		ex.getCrossParameterValidationResults()
			.forEach(error -> violations.add(new FieldViolation(null, messageOf(error))));
		return violations;
	}

	// 요청 키 이름을 쓰도록 @RequestParam·@PathVariable에 지정한 이름을 우선한다.
	private static String parameterName(MethodParameter parameter) {
		RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
		if (requestParam != null) {
			return firstText(requestParam.name(), requestParam.value(), parameter.getParameterName());
		}
		PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
		if (pathVariable != null) {
			return firstText(pathVariable.name(), pathVariable.value(), parameter.getParameterName());
		}
		return parameter.getParameterName();
	}

	private static String firstText(String name, String value, String fallback) {
		if (StringUtils.hasText(name)) {
			return name;
		}
		return StringUtils.hasText(value) ? value : fallback;
	}

	// 검증 메시지는 DTO에 한글로 명시한다. 메시지가 없으면 공통 문구를 쓴다.
	private static String messageOf(MessageSourceResolvable error) {
		String message = error.getDefaultMessage();
		return StringUtils.hasText(message) ? message : CommonApiErrorCode.INVALID_REQUEST.message();
	}

	private static boolean isCommitted(WebRequest request) {
		if (request instanceof ServletWebRequest servletWebRequest) {
			HttpServletResponse response = servletWebRequest.getResponse();
			return response != null && response.isCommitted();
		}
		return false;
	}

	private static void logFailure(ApiErrorCode errorCode, HttpStatusCode status, Exception ex, boolean committed) {
		if (status.is5xxServerError()) {
			log.error("API error code={} status={} exception={} causes={} origin={} committed={}", errorCode.code(),
					status.value(), ex.getClass().getName(), causeClassNames(ex), appOrigin(ex).orElse("none"),
					committed);
		}
		else if (log.isDebugEnabled()) {
			log.debug("API error code={} status={} exception={} committed={}", errorCode.code(), status.value(),
					ex.getClass().getName(), committed);
		}
	}

	/** 원인 예외의 클래스 이름 목록. 메시지는 넣지 않는다. */
	static List<String> causeClassNames(Throwable ex) {
		return causeChain(ex).stream().skip(1).map(cause -> cause.getClass().getName()).toList();
	}

	/** 가장 안쪽 원인부터 찾은 첫 {@code com.beone} 스택 위치. 없으면 비어 있다. */
	static Optional<String> appOrigin(Throwable ex) {
		List<Throwable> chain = new ArrayList<>(causeChain(ex));
		Collections.reverse(chain);
		for (Throwable throwable : chain) {
			for (StackTraceElement frame : throwable.getStackTrace()) {
				if (frame.getClassName().startsWith(APP_PACKAGE_PREFIX)) {
					String location = frame.getClassName() + "#" + frame.getMethodName();
					return Optional.of(frame.getLineNumber() >= 0 ? location + ":" + frame.getLineNumber() : location);
				}
			}
		}
		return Optional.empty();
	}

	// 순환하는 원인 체인에서도 끝나도록 방문 여부와 깊이를 함께 제한한다.
	private static List<Throwable> causeChain(Throwable ex) {
		List<Throwable> chain = new ArrayList<>();
		Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		for (Throwable current = ex; current != null && chain.size() <= MAX_CAUSE_DEPTH
				&& seen.add(current); current = current.getCause()) {
			chain.add(current);
		}
		return chain;
	}

}
