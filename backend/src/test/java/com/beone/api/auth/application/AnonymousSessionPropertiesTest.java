package com.beone.api.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * 익명 세션 수명 설정값의 기본값과 검증을 확인한다. 미사용 2시간·절대 상한 없음은 2026-11-18 사용자 직접 결정이다.
 */
class AnonymousSessionPropertiesTest {

	@Test
	void missingIdleTimeoutFallsBackToTwoHours() {
		assertThat(new AnonymousSessionProperties(null).idleTimeout()).isEqualTo(Duration.ofHours(2));
	}

	@Test
	void explicitIdleTimeoutIsKept() {
		assertThat(new AnonymousSessionProperties(Duration.ofHours(1)).idleTimeout()).isEqualTo(Duration.ofHours(1));
	}

	@Test
	void rejectsNonPositiveOrSubSecondIdleTimeout() {
		assertThatIllegalArgumentException().isThrownBy(() -> new AnonymousSessionProperties(Duration.ZERO));
		assertThatIllegalArgumentException().isThrownBy(() -> new AnonymousSessionProperties(Duration.ofSeconds(-1)));
		assertThatIllegalArgumentException().isThrownBy(() -> new AnonymousSessionProperties(Duration.ofMillis(500)));
	}

}
