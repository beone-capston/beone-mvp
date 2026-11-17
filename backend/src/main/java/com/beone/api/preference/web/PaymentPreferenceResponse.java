package com.beone.api.preference.web;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.beone.api.preference.application.PaymentPreferenceView;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 결제 전 설정 조회·변경 응답의 {@code data}(docs/api/preferences.md). 값이 {@code null}인 필드도 생략하지 않는다.
 *
 * @param selectionMode {@code AUTO_RECOMMEND}·{@code FIXED_REPRESENTATIVE}
 * @param representativeUserCardId 대표 카드. 없으면 {@code null}
 * @param updatedAt 마지막 설정 시각({@code +09:00} ISO-8601). 설정한 적이 없으면 {@code null}
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PaymentPreferenceResponse(String selectionMode, String representativeUserCardId, String updatedAt) {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	// Jackson 기본 출력은 초가 0이면 초를 생략하므로 직접 만든다(docs/api.md 값 형식).
	private static final DateTimeFormatter INSTANT = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssXXX");

	static PaymentPreferenceResponse from(PaymentPreferenceView view) {
		return new PaymentPreferenceResponse(view.selectionMode().name(), view.representativeUserCardId(),
				seoulInstant(view.updatedAt()));
	}

	static String seoulInstant(LocalDateTime seoulTime) {
		return (seoulTime == null) ? null : INSTANT.format(seoulTime.atZone(SEOUL));
	}

}
