package com.beone.api.benefit.rule;

import static com.beone.api.benefit.rule.RuleTestData.CARD;
import static com.beone.api.benefit.rule.RuleTestData.between;
import static com.beone.api.benefit.rule.RuleTestData.bucket;
import static com.beone.api.benefit.rule.RuleTestData.rateService;
import static com.beone.api.benefit.rule.RuleTestData.rule;
import static com.beone.api.benefit.rule.RuleTestData.service;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.card.LimitBucketId;
import com.beone.api.benefit.card.PaymentMethod;
import com.beone.api.benefit.card.ServiceId;
import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.result.ApplicationStatus;
import org.junit.jupiter.api.Test;

class RuleCatalogTest {

	private static final LocalDate ORDER_DATE = LocalDate.of(2026, 10, 15);

	private static final List<OfficialSource> SOURCES = List.of(OfficialSource.of("https://example.invalid/t.pdf", "p.1"));

	@Test
	void selectsVerifiedRuleValidOnTheDecisionDate() {
		CardRuleVersion old = rule("V1", between("2026-01-01", "2026-10-14"), VerificationStatus.VERIFIED, false);
		CardRuleVersion current = rule("V2", between("2026-10-15", "2026-12-31"), VerificationStatus.VERIFIED, false);
		RuleCatalog catalog = new RuleCatalog(List.of(old, current));

		assertThat(selectedVersion(catalog.select(CARD, LocalDate.of(2026, 10, 14))))
			.isEqualTo("V1");
		assertThat(selectedVersion(catalog.select(CARD, ORDER_DATE))).isEqualTo("V2");
		assertThat(selectedVersion(catalog.select(CARD, LocalDate.of(2026, 12, 31))))
			.isEqualTo("V2");
	}

	@Test
	void expiredOrNotYetEffectiveRuleIsRuleExpiredWithUnconfirmedAmount() {
		RuleCatalog catalog = new RuleCatalog(
				List.of(rule("V1", between("2026-01-01", "2026-10-14"), VerificationStatus.VERIFIED, false)));

		assertUnavailable(catalog.select(CARD, ORDER_DATE), ApplicationStatus.RULE_EXPIRED);
		assertUnavailable(catalog.select(CARD, LocalDate.of(2025, 12, 31)),
				ApplicationStatus.RULE_EXPIRED);
	}

	@Test
	void validityIsJudgedBeforeVerification() {
		RuleCatalog catalog = new RuleCatalog(List.of(rule("SNAPSHOT", between("2026-01-01", "2026-10-14"),
				VerificationStatus.SYNTHETIC_TEST_SNAPSHOT, true)));

		assertUnavailable(catalog.select(CARD, ORDER_DATE), ApplicationStatus.RULE_EXPIRED);
	}

	@Test
	void neverSelectsUnverifiedRulesIncludingEvaluationOnlySnapshots() {
		for (VerificationStatus status : List.of(VerificationStatus.AI_DRAFT, VerificationStatus.CANDIDATE,
				VerificationStatus.SYNTHETIC_TEST_SNAPSHOT)) {
			RuleCatalog catalog = new RuleCatalog(List.of(rule("V1", between("2026-01-01", "2026-12-31"), status, false),
					rule("EVAL", between("2026-01-01", "2026-12-31"), status, true)));

			assertUnavailable(catalog.select(CARD, ORDER_DATE),
					ApplicationStatus.NO_VERIFIED_RULE);
		}
	}

	@Test
	void cardWithoutAnyRuleHasNoVerifiedRule() {
		RuleCatalog catalog = new RuleCatalog(List.of());

		assertUnavailable(catalog.select(CardId.of("01914"), ORDER_DATE),
				ApplicationStatus.NO_VERIFIED_RULE);
	}

	@Test
	void rejectsOverlappingSelectableVersionsAndDuplicateVersions() {
		CardRuleVersion first = rule("V1", between("2026-01-01", "2026-10-15"), VerificationStatus.VERIFIED, false);
		CardRuleVersion second = rule("V2", between("2026-10-15", "2026-12-31"), VerificationStatus.VERIFIED, false);

		assertThatThrownBy(() -> new RuleCatalog(List.of(first, second))).hasMessageContaining("overlap");
		assertThatThrownBy(() -> new RuleCatalog(List.of(first, first))).hasMessageContaining("duplicate");
	}

	@Test
	void unverifiedVersionsMayOverlapAVerifiedOneWhichAloneIsSelected() {
		CardRuleVersion verified = rule("V1", between("2026-01-01", "2026-12-31"), VerificationStatus.VERIFIED, false);
		CardRuleVersion draft = rule("DRAFT", between("2026-01-01", "2026-12-31"), VerificationStatus.AI_DRAFT, false);
		CardRuleVersion snapshot = rule("EVAL", between("2026-06-01", "2026-12-31"), VerificationStatus.CANDIDATE, true);

		assertThat(selectedVersion(new RuleCatalog(List.of(verified, draft, snapshot)).select(CARD, ORDER_DATE)))
			.isEqualTo("V1");
	}

	@Test
	void serviceWithUnresolvedCalculationIsExcludedFromCalculation() {
		BenefitService fuel = service("FUEL", Won.of(400_000), RuleTerm.unresolved("reference fuel price missing"),
				List.of(LimitBucketId.of("FUEL")));
		CardRuleVersion rule = new CardRuleVersion(CARD, "V1", between("2026-01-01", "2026-12-31"),
				VerificationStatus.VERIFIED, false,
				SOURCES, RuleTestData.resolvedPolicy(),
				List.of(rateService("Great"), fuel), List.of(bucket("Great"), bucket("FUEL")));

		RuleSelection selection = new RuleCatalog(List.of(rule)).select(CARD, ORDER_DATE);

		assertThat(selection).isInstanceOfSatisfying(RuleSelection.Selected.class, selected -> {
			assertThat(selected.calculableServices()).extracting(BenefitService::id).containsExactly(ServiceId.of("Great"));
			assertThat(selected.servicesNeedingConfirmation())
				.containsEntry(ServiceId.of("FUEL"), List.of("reference fuel price missing"));
		});
	}

	@Test
	void unresolvedPerformanceOrLimitTermsBlockOnlyServicesThatDependOnThem() {
		BenefitService base = new BenefitService(ServiceId.of("BASE"), BenefitKind.POINT_ACCRUAL,
				MerchantTarget.allMerchants(), Set.of(), Set.of(PaymentMethod.DIRECT), Won.ZERO,
				Optional.empty(), List.of(new ServiceTier(Won.ZERO, RuleTerm.resolved(BenefitCalculation.rate(100)))),
				List.of(), false, RuleTerm.resolved(Set.of()), RuleTerm.unresolved("sub-won handling not confirmed"));
		RulePolicy resolved = RuleTestData.resolvedPolicy();
		RulePolicy policy = new RulePolicy(RuleTerm.unresolved("performance window not confirmed"),
				resolved.performanceDateBasis(), RuleTerm.unresolved("cancellation month not confirmed"),
				resolved.performanceExcludedNatures(), resolved.performanceExcludedClasses(),
				resolved.excludesBenefitedSales(), resolved.limitPeriod(), resolved.limitDeductionOrder(),
				resolved.newCardGrace());
		CardRuleVersion rule = new CardRuleVersion(CARD, "V1", between("2026-01-01", "2026-12-31"),
				VerificationStatus.VERIFIED, false, SOURCES, policy, List.of(base, rateService("Great")),
				List.of(bucket("Great")));

		RuleSelection selection = new RuleCatalog(List.of(rule)).select(CARD, ORDER_DATE);

		assertThat(selection).isInstanceOfSatisfying(RuleSelection.Selected.class, selected -> {
			assertThat(selected.calculableServices()).extracting(BenefitService::id).containsExactly(ServiceId.of("BASE"));
			assertThat(selected.servicesNeedingConfirmation()).containsOnlyKeys(ServiceId.of("Great"));
			assertThat(selected.unresolvedConditionalTerms())
				.extracting(ConditionalTerm::name, ConditionalTerm::trigger)
				.containsExactly(
						tuple("cancellationAttribution",
								TermTrigger.CANCELLATION_AFFECTING_PERFORMANCE_WINDOW),
						tuple("BASE.fractionalWon", TermTrigger.FRACTIONAL_WON_RESULT));
		});
	}

	@Test
	void expiredVerifiedPlusCoveringUnflaggedCandidateIsNoVerifiedRule() {
		RuleCatalog catalog = new RuleCatalog(
				List.of(rule("V1", between("2026-01-01", "2026-10-14"), VerificationStatus.VERIFIED, false),
						rule("CAND", between("2026-10-15", "2026-12-31"), VerificationStatus.CANDIDATE, false)));

		assertUnavailable(catalog.select(CARD, ORDER_DATE), ApplicationStatus.NO_VERIFIED_RULE);
	}

	@Test
	void tieredServiceStartingAtZeroOrParentLimitDependingOnPerformanceIsBlocked() {
		BenefitService tiered = new BenefitService(ServiceId.of("TIERED"), BenefitKind.POINT_ACCRUAL,
				MerchantTarget.allMerchants(), Set.of(), Set.of(PaymentMethod.DIRECT), Won.ZERO, Optional.empty(),
				List.of(new ServiceTier(Won.ZERO, RuleTerm.resolved(BenefitCalculation.rate(100))),
						new ServiceTier(Won.of(400_000), RuleTerm.resolved(BenefitCalculation.rate(200)))),
				List.of(), false, RuleTerm.resolved(Set.of()), RuleTerm.resolved(FractionalWonPolicy.TRUNCATE));
		LimitBucket total = new LimitBucket(LimitBucketId.of("TOTAL"),
				List.of(new LimitTier(Won.ZERO, Won.of(5_000)), new LimitTier(Won.of(300_000), Won.of(10_000))),
				Optional.empty());
		LimitBucket area = new LimitBucket(LimitBucketId.of("AREA"), List.of(new LimitTier(Won.ZERO, Won.of(4_000))),
				Optional.of(LimitBucketId.of("TOTAL")));
		BenefitService areaService = service("AREA_SERVICE", Won.ZERO, RuleTerm.resolved(BenefitCalculation.fixed(1_000)),
				List.of(LimitBucketId.of("AREA")));
		RulePolicy resolved = RuleTestData.resolvedPolicy();
		RulePolicy policy = new RulePolicy(RuleTerm.unresolved("performance window not confirmed"),
				resolved.performanceDateBasis(), resolved.cancellationAttribution(),
				resolved.performanceExcludedNatures(), resolved.performanceExcludedClasses(),
				resolved.excludesBenefitedSales(), resolved.limitPeriod(), resolved.limitDeductionOrder(),
				resolved.newCardGrace());
		CardRuleVersion rule = new CardRuleVersion(CARD, "V1", between("2026-01-01", "2026-12-31"),
				VerificationStatus.VERIFIED, false, SOURCES, policy, List.of(tiered, areaService), List.of(total, area));

		RuleSelection selection = new RuleCatalog(List.of(rule)).select(CARD, ORDER_DATE);

		assertThat(selection).isInstanceOfSatisfying(RuleSelection.Selected.class, selected -> {
			assertThat(selected.calculableServices()).isEmpty();
			assertThat(selected.servicesNeedingConfirmation()).containsOnlyKeys(ServiceId.of("TIERED"),
					ServiceId.of("AREA_SERVICE"));
		});
	}

	@Test
	void unresolvedStackingKeepsServicesCalculableButNeededUnlessConfirmedAbsent() {
		BenefitService great = rateService("Great");
		BenefitService check = new BenefitService(ServiceId.of("Check"), BenefitKind.BILLING_DISCOUNT, great.target(),
				Set.of(), Set.of(PaymentMethod.ELIGIBLE_SIMPLE_PAY), Won.ZERO, Optional.empty(), great.tiers(),
				List.of(LimitBucketId.of("Check")), false, RuleTerm.unresolved("stacking with Great not confirmed"),
				RuleTerm.resolved(FractionalWonPolicy.TRUNCATE));
		CardRuleVersion rule = new CardRuleVersion(CARD, "V1", between("2026-01-01", "2026-12-31"),
				VerificationStatus.VERIFIED, false, SOURCES, RuleTestData.resolvedPolicy(), List.of(great, check),
				List.of(bucket("Great"), bucket("Check")));

		RuleSelection selection = new RuleCatalog(List.of(rule)).select(CARD, ORDER_DATE);

		assertThat(selection).isInstanceOfSatisfying(RuleSelection.Selected.class, selected -> {
			// 중복 허용은 두 서비스가 함께 해당될 때만 의미가 있으므로 어느 서비스도 계산 대상에서 빠지지 않는다.
			assertThat(selected.calculableServices()).extracting(BenefitService::id)
				.containsExactly(ServiceId.of("Great"), ServiceId.of("Check"));
			assertThat(selected.servicesNeedingConfirmation()).isEmpty();
			ConditionalTerm stacking = selected.unresolvedConditionalTerms().get(0);
			assertThat(selected.unresolvedConditionalTerms()).hasSize(1);
			assertThat(stacking.name()).isEqualTo("Check.stackableWith");
			assertThat(stacking.trigger()).isEqualTo(TermTrigger.MULTIPLE_SERVICES_QUALIFY);
			assertThat(stacking.mayBeDisregarded(TriggerEvidence.PRESENT)).isFalse();
			assertThat(stacking.mayBeDisregarded(TriggerEvidence.UNDETERMINED)).isFalse();
			assertThat(stacking.mayBeDisregarded(TriggerEvidence.CONFIRMED_ABSENT)).isTrue();
		});
	}

	private static String selectedVersion(RuleSelection selection) {
		assertThat(selection).isInstanceOf(RuleSelection.Selected.class);
		return ((RuleSelection.Selected) selection).rule().ruleVersion();
	}

	private static void assertUnavailable(RuleSelection selection, ApplicationStatus status) {
		assertThat(selection).isInstanceOfSatisfying(RuleSelection.Unavailable.class, unavailable -> {
			assertThat(unavailable.status()).isEqualTo(status);
			assertThat(unavailable.amount().isConfirmed()).isFalse();
		});
	}

}
