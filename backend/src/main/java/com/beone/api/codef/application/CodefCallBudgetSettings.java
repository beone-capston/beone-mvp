package com.beone.api.codef.application;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import org.springframework.core.env.Environment;

/**
 * CODEF 호출 총량 설정(LP-2). 값은 사용자 결정 N14(결정 39-4)를 운영자가 설정으로 넣는다. 코드에는 숫자 기본값을 두지 않는다.
 *
 * <p>설정이 없거나 읽을 수 없거나, 지금이 창 밖이면 닫힘(호출 0)이다. 비공개 검증 환경의 권장값은 API별 3회이며
 * docs/domains/codef.md에 적는다.
 *
 * @param perApi API별 최대 호출 수. 없으면 빈 값
 * @param windowStart 창 시작(포함). 없으면 빈 값
 * @param windowEnd 창 끝(제외). 없으면 빈 값
 */
public record CodefCallBudgetSettings(Optional<Integer> perApi, Optional<Instant> windowStart,
		Optional<Instant> windowEnd) {

	/** API별 최대 호출 수. 환경 변수 {@code BEONE_CODEF_BUDGET_PER_API}로도 넣을 수 있다. */
	public static final String PER_API = "beone.codef.budget.per-api";

	/** 창 시작. ISO-8601 오프셋 시각(예: {@code 2026-12-01T00:00:00+09:00}). */
	public static final String WINDOW_START = "beone.codef.budget.window-start";

	/** 창 끝(제외). ISO-8601 오프셋 시각. */
	public static final String WINDOW_END = "beone.codef.budget.window-end";

	/** 아무것도 설정하지 않은 상태. 항상 닫힘이다. */
	public static final CodefCallBudgetSettings NONE = new CodefCallBudgetSettings(Optional.empty(), Optional.empty(),
			Optional.empty());

	/**
	 * 설정을 읽는다. 읽을 수 없는 값은 없는 값으로 본다(닫힘).
	 * @param environment 실행 환경
	 * @return 설정
	 */
	public static CodefCallBudgetSettings from(Environment environment) {
		Optional<Integer> perApi;
		try {
			perApi = Optional.ofNullable(environment.getProperty(PER_API, Integer.class));
		}
		catch (RuntimeException ex) {
			perApi = Optional.empty();
		}
		return new CodefCallBudgetSettings(perApi, instant(environment.getProperty(WINDOW_START)),
				instant(environment.getProperty(WINDOW_END)));
	}

	/**
	 * 지금 호출을 셀 수 있는지. 닫힘 이유를 돌려준다.
	 * @param now 지금
	 * @return 열려 있으면 빈 값, 닫혀 있으면 이유
	 */
	public Optional<String> closedReason(Instant now) {
		if (this.perApi.isEmpty() || this.windowStart.isEmpty() || this.windowEnd.isEmpty()) {
			return Optional.of("NOT_CONFIGURED");
		}
		if (this.perApi.get() <= 0 || !this.windowStart.get().isBefore(this.windowEnd.get())) {
			return Optional.of("INVALID_SETTINGS");
		}
		if (now.isBefore(this.windowStart.get()) || !now.isBefore(this.windowEnd.get())) {
			return Optional.of("OUTSIDE_WINDOW");
		}
		return Optional.empty();
	}

	private static Optional<Instant> instant(String value) {
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}
		try {
			return Optional.of(OffsetDateTime.parse(value.strip()).toInstant());
		}
		catch (DateTimeParseException ex) {
			return Optional.empty();
		}
	}

}
