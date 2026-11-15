package com.beone.api.benefit.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.rule.TermTrigger;
import com.beone.api.benefit.rule.TriggerEvidence;

/**
 * 실적 정책 근거 입력의 생성 검증. 카드는 합성 값이다.
 */
class PolicyTermEvidenceTest {

	private static final CardId CARD = CardId.of("SYN-CARD");

	@Test
	void requiresEveryValueWithoutDefaults() {
		assertThatThrownBy(() -> new PolicyTermEvidence(null, TriggerEvidence.UNDETERMINED, TriggerEvidence.UNDETERMINED))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new PolicyTermEvidence(CARD, null, TriggerEvidence.UNDETERMINED))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new PolicyTermEvidence(CARD, TriggerEvidence.UNDETERMINED, null))
			.isInstanceOf(NullPointerException.class);
	}

	@Test
	void mapsOnlyThePolicyTriggers() {
		PolicyTermEvidence evidence = new PolicyTermEvidence(CARD, TriggerEvidence.PRESENT,
				TriggerEvidence.CONFIRMED_ABSENT);

		assertThat(evidence.evidenceFor(TermTrigger.CANCELLATION_AFFECTING_PERFORMANCE_WINDOW))
			.contains(TriggerEvidence.PRESENT);
		assertThat(evidence.evidenceFor(TermTrigger.BENEFITED_SALE_IN_PERFORMANCE_WINDOW))
			.contains(TriggerEvidence.CONFIRMED_ABSENT);
		assertThatThrownBy(() -> evidence.evidenceFor(null)).isInstanceOf(NullPointerException.class);
	}

	@ParameterizedTest
	@EnumSource(value = TermTrigger.class, names = { "SHARED_LIMIT_CONSUMPTION", "WITHIN_NEW_CARD_GRACE",
			"MULTIPLE_SERVICES_QUALIFY", "FRACTIONAL_WON_RESULT" })
	void otherTriggersHaveNoEvidenceHere(TermTrigger trigger) {
		PolicyTermEvidence evidence = new PolicyTermEvidence(CARD, TriggerEvidence.CONFIRMED_ABSENT,
				TriggerEvidence.CONFIRMED_ABSENT);

		assertThat(evidence.evidenceFor(trigger)).isEmpty();
	}

}
