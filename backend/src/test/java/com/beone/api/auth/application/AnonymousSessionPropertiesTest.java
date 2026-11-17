package com.beone.api.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * 익명 세션 수명 설정값의 기본값과 검증을 확인한다. 기본값(미사용 7일·최대 30일)은 사용자 결정 대기 중인 관리자 판단 값이다.
 */
class AnonymousSessionPropertiesTest {

	@Test
	void missingValuesFallBackToPendingDefaults() {
		AnonymousSessionProperties properties = new AnonymousSessionProperties(null, null);

		assertThat(properties.idleTimeout()).isEqualTo(Duration.ofDays(7));
		assertThat(properties.absoluteTimeout()).isEqualTo(Duration.ofDays(30));
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
