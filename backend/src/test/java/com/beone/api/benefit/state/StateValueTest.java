package com.beone.api.benefit.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.YearMonth;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.rule.RuleVersionRef;
import org.junit.jupiter.api.Test;

class StateValueTest {

	private static final BasisPeriod SEPTEMBER = BasisPeriod.month(YearMonth.of(2026, 9));

	private static final OffsetDateTime REFRESHED = OffsetDateTime.of(2026, 10, 15, 9, 0, 0, 0, ZoneOffset.ofHours(9));

	@Test
	void unknownIsDifferentFromKnownZero() {
		StateValue<Won> unknown = StateValue.unknown(SEPTEMBER, null);
		StateValue<Won> zero = StateValue.manual(Won.ZERO, SEPTEMBER, REFRESHED);

		assertThat(unknown).isNotEqualTo(zero);
		assertThat(unknown.isKnown()).isFalse();
		assertThatThrownBy(unknown::knownValue).isInstanceOf(IllegalStateException.class);
		assertThat(zero.knownValue()).isEqualTo(Won.ZERO);
	}

	@Test
	void keepsSourceBasisPeriodAndRefreshTime() {
		StateValue<Won> codef = StateValue.codef(Won.of(300_000), SEPTEMBER, REFRESHED);

		assertThat(codef.source()).isEqualTo(StateSource.CODEF);
		assertThat(codef.basisPeriod()).contains(SEPTEMBER);
		assertThat(codef.refreshedAt()).contains(REFRESHED);
	}

	@Test
	void observedOrComputedSourcesRequireRefreshTime() {
		assertThatThrownBy(() -> StateValue.codef(Won.ZERO, SEPTEMBER, null)).hasMessageContaining("refresh time");
		assertThatThrownBy(() -> StateValue.manual(Won.ZERO, SEPTEMBER, null)).hasMessageContaining("refresh time");
	}

	@Test
	void syntheticMayOmitRefreshTimeButNotValueOrPeriod() {
		assertThat(StateValue.synthetic(Won.ZERO, SEPTEMBER).refreshedAt()).isEmpty();
		assertThatThrownBy(() -> StateValue.synthetic(null, SEPTEMBER)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> StateValue.synthetic(Won.ZERO, null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void inferredRequiresTheRuleVersionItWasComputedWith() {
		RuleVersionRef rule = new RuleVersionRef(CardId.of("09174"), "V1");

		assertThat(StateValue.inferred(Won.of(300_000), SEPTEMBER, REFRESHED, rule).derivedFrom()).contains(rule);
		assertThatThrownBy(() -> StateValue.inferred(Won.of(300_000), SEPTEMBER, REFRESHED, null))
			.hasMessageContaining("rule version");
	}

	@Test
	void basisPeriodMustNotBeReversed() {
		assertThatThrownBy(() -> BasisPeriod.of(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1)))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
