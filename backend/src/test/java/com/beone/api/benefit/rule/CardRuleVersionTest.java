package com.beone.api.benefit.rule;

import static com.beone.api.benefit.rule.RuleTestData.CARD;
import static com.beone.api.benefit.rule.RuleTestData.between;
import static com.beone.api.benefit.rule.RuleTestData.bucket;
import static com.beone.api.benefit.rule.RuleTestData.rateService;
import static com.beone.api.benefit.rule.RuleTestData.resolvedPolicy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.beone.api.benefit.card.LimitBucketId;
import com.beone.api.benefit.card.PaymentMethod;
import com.beone.api.benefit.card.ServiceId;
import com.beone.api.benefit.money.Won;
import org.junit.jupiter.api.Test;

class CardRuleVersionTest {

	private static final List<OfficialSource> SOURCES = List.of(OfficialSource.of("https://example.invalid/t.pdf", "p.1"));

	@Test
	void verifiedRuleNeedsSourceAndService() {
		assertThatThrownBy(() -> new CardRuleVersion(CARD, "V1", between("2026-01-01", "2026-12-31"),
				VerificationStatus.VERIFIED, false, List.of(), resolvedPolicy(), List.of(rateService("Great")),
				List.of(bucket("Great"))))
			.hasMessageContaining("official source");
		assertThatThrownBy(() -> new CardRuleVersion(CARD, "V1", between("2026-01-01", "2026-12-31"),
				VerificationStatus.VERIFIED, false, SOURCES, resolvedPolicy(), List.of(), List.of()))
			.hasMessageContaining("benefit service");
	}

	@Test
	void verifiedRuleCannotBeEvaluationOnly() {
		assertThatThrownBy(() -> RuleTestData.rule("V1", between("2026-01-01", "2026-12-31"),
				VerificationStatus.VERIFIED, true))
			.hasMessageContaining("evaluation-only");
	}

	@Test
	void rejectsMissingVersionAndDuplicateOrUnknownReferences() {
		assertThatThrownBy(() -> RuleTestData.rule(" ", between("2026-01-01", "2026-12-31"),
				VerificationStatus.CANDIDATE, true))
			.hasMessageContaining("rule version");
		assertThatThrownBy(() -> new CardRuleVersion(CARD, "V1", between("2026-01-01", "2026-12-31"),
				VerificationStatus.CANDIDATE, true, SOURCES, resolvedPolicy(),
				List.of(rateService("Great"), rateService("Great")), List.of(bucket("Great"))))
			.hasMessageContaining("duplicate service");
		assertThatThrownBy(() -> new CardRuleVersion(CARD, "V1", between("2026-01-01", "2026-12-31"),
				VerificationStatus.CANDIDATE, true, SOURCES, resolvedPolicy(), List.of(rateService("Great")), List.of()))
			.hasMessageContaining("unknown limit bucket");
	}

	@Test
	void rejectsLimitBucketParentCycle() {
		LimitBucket area = new LimitBucket(LimitBucketId.of("AREA"),
				List.of(new LimitTier(Won.of(300_000), Won.of(4_000))), Optional.of(LimitBucketId.of("TOTAL")));
		LimitBucket total = new LimitBucket(LimitBucketId.of("TOTAL"),
				List.of(new LimitTier(Won.of(300_000), Won.of(10_000))), Optional.of(LimitBucketId.of("AREA")));

		assertThatThrownBy(() -> new CardRuleVersion(CARD, "V1", between("2026-01-01", "2026-12-31"),
				VerificationStatus.CANDIDATE, true, SOURCES, resolvedPolicy(), List.of(), List.of(area, total)))
			.hasMessageContaining("cycle");
	}

	@Test
	void serviceRejectsInvalidShapes() {
		assertThatThrownBy(() -> new ServiceTier(Won.of(-1), RuleTerm.resolved(BenefitCalculation.rate(100))))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> BenefitCalculation.rate(0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> BenefitCalculation.rate(10_001)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> BenefitCalculation.fixed(0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new LimitTier(Won.ZERO, Won.ZERO)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> MerchantTarget.only(Set.of())).isInstanceOf(IllegalArgumentException.class);
		BenefitService valid = rateService("Great");
		assertThatThrownBy(() -> new BenefitService(valid.id(), valid.kind(), valid.target(), Set.of(),
				Set.<PaymentMethod>of(), Won.ZERO, Optional.empty(), valid.tiers(), valid.limitBuckets(), false,
				valid.stackableWith(), valid.fractionalWon()))
			.hasMessageContaining("payment method");
		assertThatThrownBy(() -> new BenefitService(valid.id(), valid.kind(), valid.target(), Set.of(),
				valid.paymentMethods(), Won.of(20_000), Optional.of(Won.of(10_000)), valid.tiers(), valid.limitBuckets(),
				false, valid.stackableWith(), valid.fractionalWon()))
			.hasMessageContaining("below its minimum");
		assertThatThrownBy(() -> new BenefitService(valid.id(), valid.kind(), valid.target(), Set.of(),
				valid.paymentMethods(), Won.ZERO, Optional.empty(), valid.tiers(), valid.limitBuckets(), false,
				RuleTerm.resolved(Set.of(ServiceId.of("Great"))), valid.fractionalWon()))
			.hasMessageContaining("stack with itself");
	}

	@Test
	void tiersMustBeStrictlyAscending() {
		List<ServiceTier> descending = List.of(
				new ServiceTier(Won.of(600_000), RuleTerm.resolved(BenefitCalculation.fixed(2_000))),
				new ServiceTier(Won.of(300_000), RuleTerm.resolved(BenefitCalculation.fixed(1_000))));
		BenefitService valid = rateService("CU");

		assertThatThrownBy(() -> new BenefitService(valid.id(), valid.kind(), valid.target(), Set.of(),
				valid.paymentMethods(), Won.ZERO, Optional.empty(), descending, List.of(), false, valid.stackableWith(),
				valid.fractionalWon()))
			.hasMessageContaining("strictly ascending");
		assertThatThrownBy(() -> new LimitBucket(LimitBucketId.of("CU_AREA"),
				List.of(new LimitTier(Won.of(300_000), Won.of(2_000)), new LimitTier(Won.of(300_000), Won.of(4_000))),
				Optional.empty()))
			.hasMessageContaining("strictly ascending");
	}

	@Test
	void newCardGraceShapeIsValidated() {
		Set<ServiceId> services = Set.of(ServiceId.of("Great"));

		assertThatThrownBy(() -> new NewCardGrace(GraceSpan.DAYS_FROM_REGISTRATION, Optional.empty(), Won.of(200_000),
				services, GracePartialPerformance.DEEMED_TIER))
			.hasMessageContaining("number of days");
		assertThatThrownBy(() -> new NewCardGrace(GraceSpan.UNTIL_END_OF_NEXT_MONTH, Optional.of(60), Won.of(300_000),
				services, GracePartialPerformance.DEEMED_TIER))
			.hasMessageContaining("only to a day-based");
		assertThat(new NewCardGrace(GraceSpan.DAYS_FROM_REGISTRATION, Optional.of(60), Won.of(200_000), services, GracePartialPerformance.DEEMED_TIER).days())
			.contains(60);
	}

	@Test
	void unresolvedTermHasNoDefault() {
		RuleTerm<LimitDeductionOrder> unresolved = RuleTerm.unresolved("purchase order not confirmed");

		assertThat(unresolved.isResolved()).isFalse();
		assertThatThrownBy(unresolved::resolvedValue).isInstanceOf(IllegalStateException.class);
	}

}
