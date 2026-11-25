package com.beone.api.codef.web;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.beone.api.codef.application.CodefConnectionView;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * CODEF 연결 상태 응답의 {@code data}(docs/api/codef.md). 값이 {@code null}인 필드도 생략하지 않는다. connectedId·아이디·
 * 비밀번호는 없다.
 *
 * @param organization 기관 코드
 * @param status {@code NOT_CONNECTED}·{@code CONNECTED}·{@code RECONNECT_REQUIRED}
 * @param connectedAt 연결 시각({@code +09:00} ISO-8601). 연결이 없으면 {@code null}
 * @param lastSyncedAt 마지막 동기화 시각. 없으면 {@code null}
 * @param reconnectRequired 재연결이 필요하면 참
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CodefConnectionResponse(String organization, String status, String connectedAt, String lastSyncedAt,
		boolean reconnectRequired) {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	// Jackson 기본 출력은 초가 0이면 초를 생략하므로 직접 만든다(docs/api.md 값 형식).
	private static final DateTimeFormatter INSTANT = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssXXX");

	static CodefConnectionResponse from(CodefConnectionView view) {
		return new CodefConnectionResponse(view.organization(), view.status().name(), seoulInstant(view.connectedAt()),
				seoulInstant(view.lastSyncedAt()), view.reconnectRequired());
	}

	private static String seoulInstant(LocalDateTime seoulTime) {
		return (seoulTime == null) ? null : INSTANT.format(seoulTime.atZone(SEOUL));
	}

}
