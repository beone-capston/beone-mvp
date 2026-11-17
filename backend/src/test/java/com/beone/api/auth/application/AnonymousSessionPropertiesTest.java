package com.beone.api.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * 익명 세션 수명 설정값의 기본값과 검증을 확인한다. 미사용 2시간은 2026-11-18 사용자 직접 결정, 절대 상한 2시간은 관리자 판단이다.
 */
class AnonymousSessionPropertiesTest {

	@Test
	void missingValuesFallBackToDecidedDefaults() {
		AnonymousSessionProperties properties = new AnonymousSessionProperties(null, null);

		assertThat(properties.idleTimeout()).isEqualTo(Duration.ofHours(2));
		assertThat(properties.absoluteTimeout()).isEqualTo(Duration.ofHours(2));
	}

	@Test
	void explicitValuesAreKept() {
		AnonymousSessionProperties properties = new AnonymousSessionProperties(Duration.ofHours(1), Duration.ofHours(1));

		assertThat(properties.idleTimeout()).isEqualTo(Duration.ofHours(1));
		assertThat(properties.absoluteTimeout()).isEqualTo(Duration.ofHours(1));
	}

	@Test
	void rejectsNonPositiveOrSubSecondTimeouts() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new AnonymousSessionProperties(Duration.ZERO, Duration.ofHours(1)));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new AnonymousSessionProperties(Duration.ofHours(1), Duration.ofSeconds(-1)));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new AnonymousSessionProperties(Duration.ofMillis(500), Duration.ofHours(1)));
	}

	@Test
	void rejectsIdleLongerThanAbsolute() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new AnonymousSessionProperties(Duration.ofHours(2), Duration.ofHours(1)));
	}

}
