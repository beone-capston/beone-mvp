package com.beone.api.codef.web;

import java.time.LocalDate;

import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * {@code POST /api/v1/me/codef/syncs} 요청 본문(docs/api/codef.md). 두 필드 모두 선택이며 {@code YYYY-MM-DD} 문자열만 받는다. 본문을 생략해도
 * 된다. 기간 규칙(오늘 이후·역순·12개월 초과)은 서비스가 오늘 날짜로 검사한다(관리자 판단 P9).
 */
public class CodefSyncRequest {

	@JsonDeserialize(using = StrictDateDeserializer.class)
	private LocalDate from;

	@JsonDeserialize(using = StrictDateDeserializer.class)
	private LocalDate to;

	public void setFrom(LocalDate from) {
		this.from = from;
	}

	public void setTo(LocalDate to) {
		this.to = to;
	}

	LocalDate from() {
		return this.from;
	}

	LocalDate to() {
		return this.to;
	}

}
