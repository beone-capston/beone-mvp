package com.beone.api.benefit.engine;

import static com.beone.api.benefit.engine.EngineTestData.ORDER_DATE;
import static com.beone.api.benefit.engine.EngineTestData.compared;
import static com.beone.api.benefit.engine.EngineTestData.excluded;
import static com.beone.api.benefit.engine.EngineTestData.rule;
import static com.beone.api.benefit.engine.EngineTestData.single;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class EngineResultTest {

	private static EngineResult selected(boolean conditional, List<ExcludedCard> excluded) {
		return new EngineResult(ORDER_DATE, "SELECTED", conditional, single("uc-1", "09174"),
				List.of(compared("uc-1", "09174", 1000)), excluded, null, List.of(), List.of());
	}

	@Test
	void anOutCardMakesTheResultConditionalExceptUserExclusion() {
		assertThat(selected(false, List.of()).conditional()).isFalse();
		assertThat(selected(false, List.of(excluded("uc-2", "USER_EXCLUDED"))).conditional()).isFalse();
		assertThat(selected(true, List.of(excluded("uc-2", "NO_SUPPORTED_RULE"))).conditional()).isTrue();
		assertThat(selected(true, List.of(excluded("uc-2", "USER_EXCLUDED"), excluded("uc-3", "RULE_UNAVAILABLE")))
			.conditional()).isTrue();
		assertThatThrownBy(() -> selected(true, List.of(excluded("uc-2", "USER_EXCLUDED"))))
			.hasMessageContaining("conditional");
		assertThatThrownBy(() -> selected(false, List.of(excluded("uc-2", "MAPPING_UNCONFIRMED"))))
			.hasMessageContaining("conditional");
	}

	@Test
	void notDeterminedHasNoComparedCardOrSelection() {
		EngineResult result = new EngineResult(ORDER_DATE, "NOT_DETERMINED", false, null, List.of(),
				List.of(excluded("uc-1", "RULE_UNAVAILABLE")), null, List.of(), List.of());
		assertThat(result.selected()).isNull();
		assertThatThrownBy(() -> new EngineResult(ORDER_DATE, "NOT_DETERMINED", false, null,
				List.of(compared("uc-1", "09174", 1)), List.of(), null, List.of(), List.of()))
			.hasMessageContaining("NOT_DETERMINED");
		assertThatThrownBy(() -> new EngineResult(ORDER_DATE, "NOT_DETERMINED", true, null, List.of(),
				List.of(excluded("uc-1", "RULE_UNAVAILABLE")), null, List.of(), List.of()))
			.hasMessageContaining("NOT_DETERMINED");
		assertThatThrownBy(() -> new EngineResult(ORDER_DATE, "DONE", false, null, List.of(), List.of(), null, List.of(),
				List.of()))
			.hasMessageContaining("outcome");
	}

	@Test
	void selectedCardMustBeCompared() {
		assertThatThrownBy(() -> new EngineResult(ORDER_DATE, "SELECTED", false, single("uc-9", "09174"),
				List.of(compared("uc-1", "09174", 1)), List.of(), null, List.of(), List.of()))
			.hasMessageContaining("compared");
		assertThatThrownBy(() -> new EngineResult(ORDER_DATE, "SELECTED", false, single("uc-1", "09271"),
				List.of(compared("uc-1", "09174", 1)), List.of(), null, List.of(), List.of()))
			.hasMessageContaining("product");
	}

	@Test
	void singleComparedCardHasNullBasisAndSeveralHaveOne() {
		List<ComparedCard> two = List.of(compared("uc-1", "09174", 2), compared("uc-2", "09271", 1));
		SelectedCard decided = new SelectedCard("uc-1", "09174", "MONTHLY_TOTAL", false, List.of(), 1L);
		assertThat(new EngineResult(ORDER_DATE, "SELECTED", false, decided, two, List.of(), null, List.of(), List.of())
			.selected()
			.gapToRunnerUpWon()).isEqualTo(1L);
		assertThatThrownBy(() -> new EngineResult(ORDER_DATE, "SELECTED", false, single("uc-1", "09174"), two, List.of(),
				null, List.of(), List.of()))
			.hasMessageContaining("decidedBy");
		assertThatThrownBy(() -> new EngineResult(ORDER_DATE, "SELECTED", false, decided,
				List.of(compared("uc-1", "09174", 2)), List.of(), null, List.of(), List.of()))
			.hasMessageContaining("decidedBy");
	}

	@Test
	void selectedCardFollowsTheTieRules() {
		assertThat(new SelectedCard("uc-1", "01914", "CARD_ID_ASC", true, List.of("uc-1", "uc-2"), 0L).arbitraryTieBreak())
			.isTrue();
		assertThatThrownBy(() -> new SelectedCard("uc-1", "01914", "CARD_ID_ASC", false, List.of("uc-1", "uc-2"), 0L))
			.hasMessageContaining("arbitrary");
		assertThatThrownBy(() -> new SelectedCard("uc-1", "01914", "CARD_ID_ASC", true, List.of("uc-2", "uc-3"), 0L))
			.hasMessageContaining("tie");
		assertThatThrownBy(() -> new SelectedCard("uc-1", "01914", "CARD_ID_ASC", true, List.of("uc-1", "uc-2"), 5L))
			.hasMessageContaining("tie");
		assertThatThrownBy(() -> new SelectedCard("uc-1", "01914", "MONTHLY_TOTAL", false, List.of(), 0L))
			.hasMessageContaining("positive");
		assertThatThrownBy(() -> new SelectedCard("uc-1", "01914", "MONTHLY_TOTAL", false, List.of(), null))
			.hasMessageContaining("gap");
		assertThatThrownBy(() -> new SelectedCard("uc-1", "01914", "MONTHLY_TOTAL", false, List.of("uc-1", "uc-2"), 1L))
			.hasMessageContaining("arbitrary");
		assertThatThrownBy(() -> new SelectedCard("uc-1", "01914", "BEST", false, List.of(), 1L))
			.hasMessageContaining("decidedBy");
		assertThatThrownBy(() -> new SelectedCard("uc-1", "01914", null, false, List.of(), 1L))
			.hasMessageContaining("basis");
	}

	@Test
	void fixedComparisonPointsAtTheSelectedCardAndHidesDifferenceWhenUnconfirmed() {
		List<ExcludedCard> representativeOut = List.of(excluded("uc-2", "RULE_UNAVAILABLE"));
		EngineResult result = new EngineResult(ORDER_DATE, "SELECTED", true, single("uc-1", "09174"),
				List.of(compared("uc-1", "09174", 1)), representativeOut, new FixedComparison("uc-2", "uc-1", null),
				List.of(), List.of());
		assertThat(result.fixedComparison().differenceWon()).isNull();
		assertThatThrownBy(() -> new EngineResult(ORDER_DATE, "SELECTED", true, single("uc-1", "09174"),
				List.of(compared("uc-1", "09174", 1)), representativeOut, new FixedComparison("uc-2", "uc-1", 1L), List.of(),
				List.of()))
			.hasMessageContaining("difference");
		assertThatThrownBy(() -> new EngineResult(ORDER_DATE, "SELECTED", true, single("uc-1", "09174"),
				List.of(compared("uc-1", "09174", 1)), representativeOut, new FixedComparison("uc-2", "uc-2", null), List.of(),
				List.of()))
			.hasMessageContaining("optimal");
		assertThatThrownBy(() -> new EngineResult(ORDER_DATE, "SELECTED", false, single("uc-1", "09174"),
				List.of(compared("uc-1", "09174", 1)), List.of(), new FixedComparison("uc-9", "uc-1", null), List.of(),
				List.of()))
			.hasMessageContaining("representative");
		assertThatThrownBy(() -> new FixedComparison("uc-1", null, 1L)).hasMessageContaining("optimal");
	}

	@Test
	void representativeWithoutARuleStaysInTheResultForApproval() {
		EngineResult result = new EngineResult(ORDER_DATE, "NOT_DETERMINED", false, null, List.of(),
				List.of(excluded("uc-2", "RULE_UNAVAILABLE")), new FixedComparison("uc-2", null, null), List.of(), List.of());
		assertThat(result.fixedComparison().optimalUserCardId()).isNull();
		assertThat(result.excludedCards().get(0).monthlyTotalBenefit().amountWon()).isNull();
	}

	@Test
	void usageEffectKeepsNullAsUnknownAndBelongsToACard() {
		EngineResult result = new EngineResult(ORDER_DATE, "SELECTED", false, single("uc-1", "09174"),
				List.of(compared("uc-1", "09174", 1)), List.of(), null, List.of(),
				List.of(new BucketUsageEffect("uc-1", "GREAT", null)));
		assertThat(result.usageEffects().get(0).deductionWon()).isNull();
		assertThatThrownBy(() -> new BucketUsageEffect("uc-1", "GREAT", -1L)).hasMessageContaining("negative");
		assertThatThrownBy(() -> new EngineResult(ORDER_DATE, "SELECTED", false, single("uc-1", "09174"),
				List.of(compared("uc-1", "09174", 1)), List.of(), null, List.of(),
				List.of(new BucketUsageEffect("uc-9", "GREAT", 1L))))
			.hasMessageContaining("belong");
		BucketUsageEffect effect = new BucketUsageEffect("uc-1", "GREAT", 1L);
		assertThatThrownBy(() -> new EngineResult(ORDER_DATE, "SELECTED", false, single("uc-1", "09174"),
				List.of(compared("uc-1", "09174", 1)), List.of(), null, List.of(), List.of(effect, effect)))
			.hasMessageContaining("duplicated");
	}

	@Test
	void aCardCannotBeBothComparedAndExcluded() {
		assertThatThrownBy(() -> selected(true, List.of(excluded("uc-1", "RULE_UNAVAILABLE"))))
			.hasMessageContaining("userCardId");
	}

	@Test
	void comparedCardHasAConfirmedBenefitEqualToTheMonthlyTotal() {
		BenefitAmountOutput one = BenefitAmountOutput.confirmed(1);
		assertThatThrownBy(() -> new ComparedCard("uc", "09174", "APPLIED", rule(), BenefitAmountOutput.needsConfirmation(),
				BenefitAmountOutput.needsConfirmation(), List.of(), null, List.of(), List.of()))
			.hasMessageContaining("confirmed");
		assertThatThrownBy(() -> new ComparedCard("uc", "09174", "APPLIED", rule(), one, BenefitAmountOutput.confirmed(2),
				List.of(), null, List.of(), List.of()))
			.hasMessageContaining("K4");
		assertThatThrownBy(() -> new ComparedCard("uc", "09174", "APPLIED", rule(), one, one, List.of(),
				"CONFIRMED_NOT_MET", List.of(), List.of()))
			.hasMessageContaining("noBenefitReason");
		BenefitAmountOutput zero = BenefitAmountOutput.confirmed(0);
		assertThatThrownBy(() -> new ComparedCard("uc", "09174", "NOT_APPLICABLE_CATEGORY", rule(), zero, zero, List.of(),
				null, List.of(), List.of()))
			.hasMessageContaining("noBenefitReason");
	}

	@Test
	void excludedCardNeverCarriesAConfirmedAmount() {
		assertThatThrownBy(() -> new ExcludedCard("uc", null, "USER_EXCLUDED", "제외", null, BenefitAmountOutput.confirmed(0),
				null, null, List.of()))
			.hasMessageContaining("confirmed");
	}

	@Test
	void excludedCardFieldsFollowTheReason() {
		BenefitAmountOutput unknown = BenefitAmountOutput.needsConfirmation();
		assertThat(new ExcludedCard("uc", "01664", "NEEDS_CONFIRMATION", "실적 미입력", null, unknown, "NEEDS_CONFIRMATION",
				rule(), List.of(new PendingReasonOutput("NEEDS_CONFIRMATION", List.of("TELECOM"), null))).appliedRule())
			.isNotNull();
		assertThatThrownBy(() -> new ExcludedCard("uc", "01664", "RULE_UNAVAILABLE", "규칙 없음", null, unknown, "APPLIED",
				null, List.of()))
			.hasMessageContaining("applicationStatus");
		assertThatThrownBy(() -> new ExcludedCard("uc", "01664", "NEEDS_CONFIRMATION", "실적", null, unknown,
				"NEEDS_CONFIRMATION", null, List.of()))
			.hasMessageContaining("rule");
		assertThatThrownBy(() -> new ExcludedCard("uc", null, "USER_EXCLUDED", "제외", null, unknown, "APPLIED", null,
				List.of()))
			.hasMessageContaining("not calculated");
		assertThatThrownBy(() -> new ExcludedCard("uc", null, "USER_EXCLUDED", "제외", "09174", unknown, null, null,
				List.of()))
			.hasMessageContaining("MAPPING_UNCONFIRMED");
		assertThatThrownBy(() -> new ExcludedCard("uc", null, "OTHER", "제외", null, unknown, null, null, List.of()))
			.hasMessageContaining("reason");
		assertThat(new ExcludedCard("uc", null, "MAPPING_UNCONFIRMED", "매핑 확인 필요", "09174", unknown, null, null,
				List.of()).candidateCardProductId()).isEqualTo("09174");
	}

	@Test
	void amountIsNullExactlyWhenUnconfirmed() {
		assertThat(BenefitAmountOutput.needsConfirmation().amountWon()).isNull();
		assertThat(BenefitAmountOutput.confirmed(0).amountWon()).isZero();
		assertThatThrownBy(() -> new BenefitAmountOutput(null, "CONFIRMED")).hasMessageContaining("CONFIRMED");
		assertThatThrownBy(() -> new BenefitAmountOutput(0L, "NEEDS_CONFIRMATION")).hasMessageContaining("CONFIRMED");
		assertThatThrownBy(() -> BenefitAmountOutput.confirmed(-1)).hasMessageContaining("negative");
	}

	@Test
	void serviceResultKeepsAdoptionAndContributionConsistent() {
		assertThat(service("ADOPTED", BenefitAmountOutput.confirmed(100)).adoption()).isEqualTo("ADOPTED");
		assertThat(service("NOT_ADOPTED", BenefitAmountOutput.confirmed(0)).contribution().amountWon()).isZero();
		assertThat(service("UNDETERMINED", BenefitAmountOutput.needsConfirmation()).contribution().amountWon()).isNull();
		assertThatThrownBy(() -> service("NOT_ADOPTED", BenefitAmountOutput.confirmed(1))).hasMessageContaining("zero");
		assertThatThrownBy(() -> service("NOT_ADOPTED", BenefitAmountOutput.needsConfirmation()))
			.hasMessageContaining("zero");
		assertThatThrownBy(() -> service("UNDETERMINED", BenefitAmountOutput.confirmed(1)))
			.hasMessageContaining("UNDETERMINED");
		assertThatThrownBy(() -> service("ADOPTED", BenefitAmountOutput.needsConfirmation()))
			.hasMessageContaining("UNDETERMINED");
		assertThatThrownBy(() -> service("MAYBE", BenefitAmountOutput.confirmed(0))).hasMessageContaining("adoption");
	}

	@Test
	void limitedServiceShowsTheUsageNoteAndUnlimitedDoesNot() {
		BenefitAmountOutput amount = BenefitAmountOutput.confirmed(100);
		ServiceLimitLine line = new ServiceLimitLine("GREAT", null, 10_000L, 9_000L, "MANUAL", 900L);
		assertThat(new ServiceResult("GREAT", "APPLIED", 100L, amount, "ADOPTED", List.of(line), null, "USER_ENTERED", true,
				"입력한 사용액 기준", false, null, List.of()).limits()).hasSize(1);
		assertThatThrownBy(() -> new ServiceResult("GREAT", "APPLIED", 100L, amount, "ADOPTED", List.of(line), null, null,
				true, "입력한 사용액 기준", false, null, List.of()))
			.hasMessageContaining("usageBasis");
		assertThatThrownBy(() -> new ServiceResult("GREAT", "APPLIED", 100L, amount, "ADOPTED", List.of(line), null,
				"USER_ENTERED", false, "입력한 사용액 기준", false, null, List.of()))
			.hasMessageContaining("differ");
		assertThatThrownBy(() -> new ServiceResult("GREAT", "APPLIED", 100L, amount, "ADOPTED", List.of(), null,
				"USER_ENTERED", true, "입력한 사용액 기준", false, null, List.of()))
			.hasMessageContaining("limit");
	}

	@Test
	void truncationNeedsItsBasis() {
		BenefitAmountOutput amount = BenefitAmountOutput.confirmed(100);
		assertThat(new ServiceResult("BASE", "APPLIED", 100L, amount, "ADOPTED", List.of(), null, null, false, null, true,
				new FractionalWonBasisOutput("TRUNCATE", "약관 근거 없음, 사용자 결정"), List.of()).fractionalWonTruncated()).isTrue();
		assertThatThrownBy(() -> new ServiceResult("BASE", "APPLIED", 100L, amount, "ADOPTED", List.of(), null, null, false,
				null, true, null, List.of()))
			.hasMessageContaining("truncated");
	}

	@Test
	void limitLineKeepsUnknownRemainingAsNull() {
		assertThat(new ServiceLimitLine("GREAT", null, 10_000L, null, "UNKNOWN", null).remainingAfterWon()).isNull();
		assertThat(new ServiceLimitLine("GREAT", null, 10_000L, 12_000L, "MANUAL", null).remainingAfterWon()).isNull();
		assertThat(new ServiceLimitLine("GREAT", null, 10_000L, 9_000L, "MANUAL", 0L).remainingAfterWon()).isZero();
		assertThatThrownBy(() -> new ServiceLimitLine("GREAT", null, null, 1L, "MANUAL", 0L))
			.hasMessageContaining("known");
		assertThatThrownBy(() -> new ServiceLimitLine("GREAT", null, 10_000L, null, "UNKNOWN", 0L))
			.hasMessageContaining("known");
		assertThatThrownBy(() -> new ServiceLimitLine("GREAT", null, 10_000L, 9_000L, "MANUAL", 1_001L))
			.hasMessageContaining("exceed");
		assertThatThrownBy(() -> new ServiceLimitLine("GREAT", null, 10_000L, 9_000L, "MANUAL", -1L))
			.hasMessageContaining("negative");
	}

	private static ServiceResult service(String adoption, BenefitAmountOutput contribution) {
		return new ServiceResult("GREAT", "NEEDS_CONFIRMATION", null, contribution, adoption, List.of(), null, null, false,
				null, false, null, List.of());
	}

}
