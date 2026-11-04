package com.beone.api.benefit.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ConditionalTermTest {

	private final ConditionalTerm term = new ConditionalTerm("cancellationAttribution",
			"cancellation month not confirmed", TermTrigger.CANCELLATION_AFFECTING_PERFORMANCE_WINDOW);

	@Test
	void mayBeDisregardedOnlyWhenTriggerIsConfirmedAbsent() {
		assertThat(term.mayBeDisregarded(TriggerEvidence.CONFIRMED_ABSENT)).isTrue();
		assertThat(term.mayBeDisregarded(TriggerEvidence.PRESENT)).isFalse();
		assertThat(term.mayBeDisregarded(TriggerEvidence.UNDETERMINED)).isFalse();
	}

	@Test
	void missingEvidenceIsNotAbsence() {
		assertThatThrownBy(() -> term.mayBeDisregarded(null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void requiresNameReasonAndTrigger() {
		assertThatThrownBy(() -> new ConditionalTerm(" ", "r", TermTrigger.FRACTIONAL_WON_RESULT))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ConditionalTerm("n", "", TermTrigger.FRACTIONAL_WON_RESULT))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ConditionalTerm("n", "r", null)).isInstanceOf(NullPointerException.class);
	}

}
