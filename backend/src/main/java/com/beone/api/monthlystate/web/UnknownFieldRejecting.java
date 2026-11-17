package com.beone.api.monthlystate.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;

/**
 * 요청 본문의 알 수 없는 필드(서버가 정하는 {@code source}·{@code updatedAt} 등)를 거부한다. 400 {@code INVALID_REQUEST}
 * ({@code errors: []})다(결정 27 A3-9).
 *
 * <p>공유 Jackson 설정을 바꾸지 않고 월간 상태 요청 DTO에만 적용한다. 필드 이름과 값은 예외 메시지에 담지 않는다.
 */
interface UnknownFieldRejecting {

	/** Jackson이 선언되지 않은 필드마다 호출한다. 항상 역직렬화 오류로 만든다. */
	@JsonAnySetter
	default void rejectUnknownField(String name, Object value) {
		throw new IllegalArgumentException("unknown request field");
	}

}
