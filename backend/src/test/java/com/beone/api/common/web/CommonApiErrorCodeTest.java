package com.beone.api.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class CommonApiErrorCodeTest {

	@Test
	void codesStatusesAndMessagesMatchContract() {
		assertThat(CommonApiErrorCode.values())
			.extracting(ApiErrorCode::code, ApiErrorCode::status, ApiErrorCode::message)
			.containsExactlyInAnyOrder(
					tuple("INVALID_REQUEST", HttpStatus.BAD_REQUEST, "요청 값을 확인해 주세요."),
					tuple("NOT_FOUND", HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
					tuple("METHOD_NOT_ALLOWED", HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
					tuple("UNSUPPORTED_MEDIA_TYPE", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 콘텐츠 형식입니다."),
					tuple("INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "요청 처리 중 오류가 발생했습니다."));
	}

	@Test
	void publicCodesAreUnique() {
		assertThat(Arrays.stream(CommonApiErrorCode.values()).map(ApiErrorCode::code)).doesNotHaveDuplicates();
	}

}
