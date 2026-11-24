package com.beone.api.auth.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;

/**
 * 요청 본문의 정의하지 않은 필드(확인용 비밀번호·이름·닉네임 등)를 거부하고 400 {@code INVALID_REQUEST}로 답한다
 * (docs/api/auth.md 「값 검증」).
 *
 * <p>공유 Jackson 설정({@code FAIL_ON_UNKNOWN_PROPERTIES})을 바꾸지 않고 인증 요청 DTO에만 적용한다. 필드 이름과 값은 예외
 * 메시지에 담지 않는다.
 */
interface UnknownFieldRejecting {

	/**
	 * Jackson이 선언되지 않은 필드마다 호출한다. 항상 역직렬화 오류로 만든다.
	 * @param name 선언되지 않은 필드 이름
	 * @param value 그 필드의 값. 쓰지 않는다
	 */
	@JsonAnySetter
	default void rejectUnknownField(String name, Object value) {
		throw new IllegalArgumentException("unknown request field");
	}

}
