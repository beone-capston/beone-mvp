package com.beone.api.card.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;

/**
 * 요청 본문의 알 수 없는 필드를 거부한다. 카드번호·CVV·유효기간 같은 필드를 보내도 저장하지 않고 400 {@code INVALID_REQUEST}
 * ({@code errors: []})로 답한다(docs/api/cards.md, D-14 ⑨, 결정 27 A2-9).
 *
 * <p>공유 Jackson 설정({@code FAIL_ON_UNKNOWN_PROPERTIES})을 바꾸지 않고 보유 카드 요청 DTO에만 적용한다. 필드 이름과 값은 예외
 * 메시지에 담지 않는다.
 */
interface UnknownFieldRejecting {

	/** Jackson이 선언되지 않은 필드마다 호출한다. 항상 역직렬화 오류로 만든다. */
	@JsonAnySetter
	default void rejectUnknownField(String name, Object value) {
		throw new IllegalArgumentException("unknown request field");
	}

}
