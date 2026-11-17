package com.beone.api.codef.web;

import com.beone.api.common.web.ApiErrorCode;
import org.springframework.http.HttpStatus;

/**
 * CODEF 연결 API의 업무 오류 코드(docs/api.md 업무 오류 코드, docs/api/codef.md). 문구는 고정이며 CODEF 결과 코드·기관 문구를 담지
 * 않는다.
 */
enum CodefErrorCode implements ApiErrorCode {

	CODEF_NOT_CONNECTED("CODEF_NOT_CONNECTED", HttpStatus.CONFLICT, "CODEF가 연결되어 있지 않습니다."),

	CODEF_RECONNECT_REQUIRED("CODEF_RECONNECT_REQUIRED", HttpStatus.CONFLICT, "CODEF 재연결이 필요합니다."),

	CODEF_LOGIN_FAILED("CODEF_LOGIN_FAILED", HttpStatus.BAD_REQUEST, "카드사 로그인에 실패했습니다."),

	CODEF_CARD_VERIFICATION_NOT_SUPPORTED("CODEF_CARD_VERIFICATION_NOT_SUPPORTED", HttpStatus.CONFLICT,
			"카드소지확인이 필요한 계정은 지원하지 않습니다. 수동 입력을 이용해 주세요."),

	CODEF_UNAVAILABLE("CODEF_UNAVAILABLE", HttpStatus.BAD_GATEWAY, "CODEF 응답을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요."),

	PORTFOLIO_READ_ONLY("PORTFOLIO_READ_ONLY", HttpStatus.CONFLICT, "가상 포트폴리오에서는 CODEF 연결을 바꿀 수 없습니다.");

	private final String code;

	private final HttpStatus status;

	private final String message;

	CodefErrorCode(String code, HttpStatus status, String message) {
		this.code = code;
		this.status = status;
		this.message = message;
	}

	@Override
	public String code() {
		return this.code;
	}

	@Override
	public HttpStatus status() {
		return this.status;
	}

	@Override
	public String message() {
		return this.message;
	}

}
