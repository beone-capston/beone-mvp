package com.beone.api.codef.application;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 호출 총량 설정(LP-2, N14)은 없거나 잘못되면 닫힘이고, 창은 시작 포함·끝 제외다.
 */
class CodefCallBudgetSettingsTest {

	private static final Instant START = Instant.parse("2026-12-01T00:00:00Z");

	private static final Instant END = Instant.parse("2026-12-08T00:00:00Z");

	@Test
	void missingSettingsAreClosed() {
		assertThat(CodefCallBudgetSettings.NONE.closedReason(START)).hasValue("NOT_CONFIGURED");
		assertThat(CodefCallBudgetSettings.from(new MockEnvironment()).closedReason(START)).hasValue("NOT_CONFIGURED");
		assertThat(settings(null, "2026-12-01T00:00:00Z", "2026-12-08T00:00:00Z").closedReason(START))
			.hasValue("NOT_CONFIGURED");
		assertThat(settings("3", null, "2026-12-08T00:00:00Z").closedReason(START)).hasValue("NOT_CONFIGURED");
	}

	@Test
	void unreadableValuesAreTreatedAsMissing() {
		assertThat(settings("three", "2026-12-01T00:00:00Z", "2026-12-08T00:00:00Z").closedReason(START))
			.hasValue("NOT_CONFIGURED");
		assertThat(settings("3", "2026-12-01", "2026-12-08T00:00:00Z").closedReason(START)).hasValue("NOT_CONFIGURED");
	}

	@Test
	void zeroLimitOrReversedWindowIsInvalid() {
		assertThat(settings("0", "2026-12-01T00:00:00Z", "2026-12-08T00:00:00Z").closedReason(START))
			.hasValue("INVALID_SETTINGS");
		assertThat(settings("3", "2026-12-08T00:00:00Z", "2026-12-01T00:00:00Z").closedReason(START))
			.hasValue("INVALID_SETTINGS");
	}

	@Test
	void windowIncludesStartAndExcludesEnd() {
		CodefCallBudgetSettings settings = settings("3", "2026-12-01T09:00:00+09:00", "2026-12-08T09:00:00+09:00");

		assertThat(settings.closedReason(START.minusMillis(1))).hasValue("OUTSIDE_WINDOW");
		assertThat(settings.closedReason(START)).isEmpty();
		assertThat(settings.closedReason(END.minusMillis(1))).isEmpty();
		assertThat(settings.closedReason(END)).hasValue("OUTSIDE_WINDOW");
		assertThat(settings.perApi()).hasValue(3);
	}

	private static CodefCallBudgetSettings settings(String perApi, String start, String end) {
		MockEnvironment environment = new MockEnvironment();
		if (perApi != null) {
			environment.setProperty(CodefCallBudgetSettings.PER_API, perApi);
		}
		if (start != null) {
			environment.setProperty(CodefCallBudgetSettings.WINDOW_START, start);
		}
		if (end != null) {
			environment.setProperty(CodefCallBudgetSettings.WINDOW_END, end);
		}
		return CodefCallBudgetSettings.from(environment);
	}

}
