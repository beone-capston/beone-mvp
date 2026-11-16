package com.beone.api.card.application;

import com.beone.api.benefit.rule.OfficialSource;

/**
 * 규칙의 공식 근거 하나(코드 {@link OfficialSource}).
 *
 * @param uri 공식 문서 주소
 * @param locator 쪽·항목 위치. 규칙에 없으면 {@code null}
 */
public record OfficialSourceView(String uri, String locator) {

	static OfficialSourceView from(OfficialSource source) {
		return new OfficialSourceView(source.uri(), source.locator().orElse(null));
	}

}
