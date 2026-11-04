package com.beone.api.benefit.engine;

import static com.beone.api.benefit.engine.EngineTestData.NOW;
import static com.beone.api.benefit.engine.EngineTestData.ORDER_DATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StateInputTest {

	@Test
	void notEnteredKeepsNullInsteadOfZero() {
		StateInput<Long> value = StateInput.notEntered();
		assertThat(value.value()).isNull();
		assertThat(value.usable()).isFalse();
		assertThat(StateInput.manual(0L, ORDER_DATE, ORDER_DATE, NOW).value()).isZero();
	}

	@Test
	void periodMismatchIsNotAcceptedBecauseTheEngineJudgesThePeriod() {
		assertThatThrownBy(() -> new StateInput<>(1L, "MANUAL", ORDER_DATE, ORDER_DATE, NOW, false, "PERIOD_MISMATCH"))
			.hasMessageContaining("PERIOD_MISMATCH");
		assertThatThrownBy(() -> new StateInput<>(null, "UNKNOWN", null, null, null, false, "PERIOD_MISMATCH"))
			.hasMessageContaining("PERIOD_MISMATCH");
	}

	@Test
	void sourceConflictCanMarkAValueOrAnUnknownUnusable() {
		assertThat(new StateInput<>(1L, "MANUAL", ORDER_DATE, ORDER_DATE, NOW, false, "SOURCE_CONFLICT").usable()).isFalse();
		assertThat(new StateInput<>(null, "UNKNOWN", null, null, null, false, "SOURCE_CONFLICT").reason())
			.isEqualTo("SOURCE_CONFLICT");
	}

	@Test
	void unusableNeedsAnAllowedReasonAndUsableHasNone() {
		assertThatThrownBy(() -> new StateInput<>(1L, "MANUAL", ORDER_DATE, ORDER_DATE, NOW, false, null))
			.hasMessageContaining("reason");
		assertThatThrownBy(() -> new StateInput<>(1L, "MANUAL", ORDER_DATE, ORDER_DATE, NOW, false, "NOT_ENTERED"))
			.hasMessageContaining("reason");
		assertThatThrownBy(() -> new StateInput<>(1L, "MANUAL", ORDER_DATE, ORDER_DATE, NOW, true, "SOURCE_CONFLICT"))
			.hasMessageContaining("reason");
	}

	@Test
	void unknownCarriesNoValueAndIsNotUsable() {
		assertThatThrownBy(() -> new StateInput<>(1L, "UNKNOWN", null, null, null, false, "NOT_ENTERED"))
			.hasMessageContaining("UNKNOWN");
		assertThatThrownBy(() -> new StateInput<>(null, "UNKNOWN", null, null, null, true, null))
			.hasMessageContaining("UNKNOWN");
	}

	@Test
	void knownValueNeedsPeriodAndRefreshTime() {
		assertThatThrownBy(() -> new StateInput<>(null, "MANUAL", ORDER_DATE, ORDER_DATE, NOW, true, null))
			.hasMessageContaining("value");
		assertThatThrownBy(() -> new StateInput<>(1L, "MANUAL", null, null, NOW, true, null))
			.hasMessageContaining("period");
		assertThatThrownBy(() -> new StateInput<>(1L, "MANUAL", ORDER_DATE, null, NOW, true, null))
			.hasMessageContaining("both ends");
		assertThatThrownBy(() -> new StateInput<>(1L, "MANUAL", ORDER_DATE, ORDER_DATE.minusDays(1), NOW, true, null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new StateInput<>(1L, "MANUAL", ORDER_DATE, ORDER_DATE, null, true, null))
			.hasMessageContaining("updatedAt");
		assertThat(StateInput.synthetic(1L, ORDER_DATE, ORDER_DATE).updatedAt()).isNull();
	}

	@Test
	void rejectsInferredAndUnknownSourceNames() {
		assertThatThrownBy(() -> new StateInput<>(1L, "INFERRED", ORDER_DATE, ORDER_DATE, NOW, true, null))
			.hasMessageContaining("INFERRED");
		assertThatThrownBy(() -> new StateInput<>(1L, "GUESS", ORDER_DATE, ORDER_DATE, NOW, true, null))
			.hasMessageContaining("source");
		assertThatThrownBy(() -> new StateInput<>(1L, null, ORDER_DATE, ORDER_DATE, NOW, true, null))
			.hasMessageContaining("source");
	}

}
