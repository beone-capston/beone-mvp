package com.beone.api.benefit.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.card.LimitBucketId;
import com.beone.api.benefit.card.ServiceId;
import com.beone.api.benefit.money.BenefitAmount;
import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.rule.RuleVersionRef;
import org.junit.jupiter.api.Test;

class RecommendationTest {

	private static final CardId CHECK = CardId.of("01914");

	private static final CardId TITANIUM = CardId.of("09271");

	@Test
	void statusMustAgreeWithAmount() {
		assertThat(ApplicationStatus.APPLIED.isConsistentWith(BenefitAmount.confirmed(1_000))).isTrue();
		assertThat(ApplicationStatus.APPLIED.isConsistentWith(BenefitAmount.confirmed(0))).isFalse();
		assertThat(ApplicationStatus.NOT_APPLICABLE_CATEGORY.isConsistentWith(BenefitAmount.confirmed(0))).isTrue();
		assertThat(ApplicationStatus.RULE_EXPIRED.isConsistentWith(BenefitAmount.confirmed(0))).isFalse();
		assertThat(ApplicationStatus.NEEDS_CONFIRMATION.isConsistentWith(BenefitAmount.needsConfirmation("x"))).isTrue();
		assertThatThrownBy(() -> new ServiceBenefit(ServiceId.of("Great"), ApplicationStatus.LIMIT_EXHAUSTED,
				BenefitAmount.confirmed(1_000)))
			.hasMessageContaining("does not match");
	}

	@Test
	void ruleUnavailableResultHasUnconfirmedAmountAndNoRule() {
		CardBenefit expired = new CardBenefit(TITANIUM, Optional.empty(), ApplicationStatus.RULE_EXPIRED,
				BenefitAmount.needsConfirmation("rule expired"), BenefitAmount.needsConfirmation("rule expired"),
				List.of(), Map.of());

		assertThat(expired.currentOrderBenefit()).isNotEqualTo(BenefitAmount.confirmed(0));
		assertThatThrownBy(() -> new CardBenefit(TITANIUM, Optional.empty(), ApplicationStatus.RULE_EXPIRED,
				BenefitAmount.confirmed(0), BenefitAmount.confirmed(0), List.of(), Map.of()))
			.hasMessageContaining("does not match");
		assertThatThrownBy(() -> new CardBenefit(TITANIUM, Optional.empty(), ApplicationStatus.APPLIED,
				BenefitAmount.confirmed(1_000), BenefitAmount.confirmed(1_000), List.of(), Map.of()))
			.hasMessageContaining("rule version");
	}

	@Test
	void ruleUnavailableResultCannotReportConfirmedMonthlyTotalOrLimits() {
		BenefitAmount unknown = BenefitAmount.needsConfirmation("rule expired");

		assertThatThrownBy(() -> new CardBenefit(TITANIUM, Optional.empty(), ApplicationStatus.RULE_EXPIRED, unknown,
				BenefitAmount.confirmed(0), List.of(), Map.of()))
			.hasMessageContaining("monthly total is unconfirmed");
		assertThatThrownBy(() -> new CardBenefit(TITANIUM, Optional.empty(), ApplicationStatus.NO_VERIFIED_RULE, unknown,
				unknown, List.of(), Map.of(LimitBucketId.of("BASE"), BenefitAmount.confirmed(0))))
			.hasMessageContaining("remaining limits");
	}

	@Test
	void cardWithUnconfirmedMonthlyTotalCannotBeSelected() {
		CardBenefit expired = new CardBenefit(TITANIUM, Optional.empty(), ApplicationStatus.RULE_EXPIRED,
				BenefitAmount.needsConfirmation("rule expired"), BenefitAmount.needsConfirmation("rule expired"),
				List.of(), Map.of());

		assertThatThrownBy(() -> new Recommendation(List.of(expired),
				new RecommendationOutcome.Selected(TITANIUM, Optional.empty(), List.of(), Optional.empty(), false)))
			.hasMessageContaining("unconfirmed monthly total");
		assertThat(new Recommendation(List.of(expired), new RecommendationOutcome.NotDetermined("no usable rule"))
			.outcome()).isInstanceOf(RecommendationOutcome.NotDetermined.class);
	}

	@Test
	void tieIsListedWithAnArbitraryBasis() {
		Recommendation recommendation = new Recommendation(List.of(applied(CHECK), applied(TITANIUM)),
				new RecommendationOutcome.Selected(CHECK, Optional.of(SelectionBasis.CARD_ID_ASC), List.of(CHECK, TITANIUM),
						Optional.of(Won.ZERO), false));

		assertThat(SelectionBasis.CARD_ID_ASC.isArbitrary()).isTrue();
		assertThat(SelectionBasis.MONTHLY_TOTAL.isArbitrary()).isFalse();
		assertThat(recommendation.outcome()).isInstanceOf(RecommendationOutcome.Selected.class);
	}

	@Test
	void rejectsInconsistentTie() {
		assertThatThrownBy(() -> new RecommendationOutcome.Selected(CHECK, Optional.of(SelectionBasis.CARD_ID_ASC),
				List.of(), Optional.empty(), false))
			.hasMessageContaining("arbitrary");
		assertThatThrownBy(() -> new RecommendationOutcome.Selected(CHECK, Optional.of(SelectionBasis.MONTHLY_TOTAL),
				List.of(CHECK, TITANIUM), Optional.empty(), false))
			.hasMessageContaining("arbitrary");
		assertThatThrownBy(() -> new RecommendationOutcome.Selected(CHECK, Optional.of(SelectionBasis.REPRESENTATIVE_CARD),
				List.of(TITANIUM, CardId.of("09174")), Optional.empty(), false))
			.hasMessageContaining("one of the tied");
	}

	@Test
	void selectedCardMustHaveAResult() {
		assertThatThrownBy(() -> new Recommendation(List.of(applied(CHECK)),
				new RecommendationOutcome.Selected(TITANIUM, Optional.empty(), List.of(), Optional.empty(), false)))
			.hasMessageContaining("must have results");
	}

	@Test
	void notDeterminedNeedsAReason() {
		assertThat(new RecommendationOutcome.NotDetermined("no usable rule").reason()).isEqualTo("no usable rule");
		assertThatThrownBy(() -> new RecommendationOutcome.NotDetermined("")).isInstanceOf(IllegalArgumentException.class);
	}

	private static CardBenefit applied(CardId card) {
		return new CardBenefit(card, Optional.of(new RuleVersionRef(card, "V1")), ApplicationStatus.APPLIED,
				BenefitAmount.confirmed(1_000), BenefitAmount.confirmed(1_000), List.of(), Map.of());
	}

}
