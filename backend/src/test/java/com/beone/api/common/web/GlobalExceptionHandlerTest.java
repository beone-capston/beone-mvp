package com.beone.api.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * 상태 포괄 매핑과 500 진단 정보 생성을 직접 호출로 확인한다. HTTP 동작은 GlobalExceptionHandlerWebTest가 확인한다.
 */
class GlobalExceptionHandlerTest {

	@Test
	void listedStatusesHaveTheirOwnCodes() {
		assertThat(GlobalExceptionHandler.errorCodeFor(HttpStatus.NOT_FOUND)).isEqualTo(CommonApiErrorCode.NOT_FOUND);
		assertThat(GlobalExceptionHandler.errorCodeFor(HttpStatus.METHOD_NOT_ALLOWED))
			.isEqualTo(CommonApiErrorCode.METHOD_NOT_ALLOWED);
		assertThat(GlobalExceptionHandler.errorCodeFor(HttpStatus.UNSUPPORTED_MEDIA_TYPE))
			.isEqualTo(CommonApiErrorCode.UNSUPPORTED_MEDIA_TYPE);
	}

	@Test
	void unlistedStatusesAreGroupedByClass() {
		assertThat(GlobalExceptionHandler.errorCodeFor(HttpStatus.BAD_REQUEST))
			.isEqualTo(CommonApiErrorCode.INVALID_REQUEST);
		assertThat(GlobalExceptionHandler.errorCodeFor(HttpStatus.CONTENT_TOO_LARGE))
			.isEqualTo(CommonApiErrorCode.INVALID_REQUEST);
		assertThat(GlobalExceptionHandler.errorCodeFor(HttpStatusCode.valueOf(499)))
			.isEqualTo(CommonApiErrorCode.INVALID_REQUEST);
		assertThat(GlobalExceptionHandler.errorCodeFor(HttpStatus.SERVICE_UNAVAILABLE))
			.isEqualTo(CommonApiErrorCode.INTERNAL_SERVER_ERROR);
		assertThat(GlobalExceptionHandler.errorCodeFor(HttpStatus.INTERNAL_SERVER_ERROR))
			.isEqualTo(CommonApiErrorCode.INTERNAL_SERVER_ERROR);
	}

	@Test
	void causeClassNamesExcludeMessages() {
		Exception ex = new IllegalStateException("secret", new UncheckedIOException("secret", new IOException("secret")));

		assertThat(GlobalExceptionHandler.causeClassNames(ex)).containsExactly("java.io.UncheckedIOException",
				"java.io.IOException");
	}

	@Test
	void cyclicCauseChainTerminates() {
		IllegalStateException first = new IllegalStateException("first");
		IllegalArgumentException second = new IllegalArgumentException("second");
		first.initCause(second);
		second.initCause(first);

		assertThat(GlobalExceptionHandler.causeClassNames(first)).containsExactly("java.lang.IllegalArgumentException");
		assertThat(GlobalExceptionHandler.appOrigin(first)).isPresent();
	}

	@Test
	void originPrefersInnermostApplicationFrame() {
		IOException cause = new IOException("inner");
		cause.setStackTrace(new StackTraceElement[] {
				new StackTraceElement("java.io.FileInputStream", "open", "FileInputStream.java", 10),
				new StackTraceElement("com.beone.api.card.CardReader", "read", "CardReader.java", 42) });
		IllegalStateException ex = new IllegalStateException("outer", cause);
		ex.setStackTrace(new StackTraceElement[] {
				new StackTraceElement("com.beone.api.card.CardService", "load", "CardService.java", 7) });

		assertThat(GlobalExceptionHandler.appOrigin(ex)).contains("com.beone.api.card.CardReader#read:42");
	}

	@Test
	void originIsEmptyWithoutApplicationFrame() {
		IllegalStateException ex = new IllegalStateException("outer");
		ex.setStackTrace(new StackTraceElement[] {
				new StackTraceElement("org.example.Library", "call", "Library.java", 3) });

		assertThat(GlobalExceptionHandler.appOrigin(ex)).isEmpty();
	}

	@Test
	void originOmitsUnknownLineNumber() {
		IllegalStateException ex = new IllegalStateException("outer");
		ex.setStackTrace(new StackTraceElement[] {
				new StackTraceElement("com.beone.api.card.CardService", "load", null, -1) });

		assertThat(GlobalExceptionHandler.appOrigin(ex)).contains("com.beone.api.card.CardService#load");
	}

}
