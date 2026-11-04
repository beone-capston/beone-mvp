package com.beone.api.common.web;

import org.springframework.http.HttpStatus;

/**
 * API 오류 코드의 공통 형태. 기능별 오류 코드 enum이 이 인터페이스를 구현한다.
 *
 * <p>HTTP 상태를 포함하므로 웹 경계(컨트롤러·예외 처리기)에서만 사용한다. 계산·도메인 코드는 이 타입에 의존하지 않는다.
 */
public interface ApiErrorCode {

	/** 응답 {@code code}로 공개하는 값. enum 상수명과 독립적으로 관리한다. */
	String code();

	/** 기본 HTTP 상태. */
	HttpStatus status();

	/** 응답 {@code message}로 쓰는 고정 문구. 입력값·예외 메시지를 넣지 않는다. */
	String message();

}
