package com.beone.api.card.application;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.card.LimitBucketId;
import com.beone.api.benefit.card.PaymentMethod;
import com.beone.api.benefit.card.ServiceId;
import com.beone.api.benefit.card.TransactionNature;
import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.rule.BenefitCalculation;
import com.beone.api.benefit.rule.BenefitKind;
import com.beone.api.benefit.rule.BenefitService;
import com.beone.api.benefit.rule.CancellationAttribution;
import com.beone.api.benefit.rule.CardRuleVersion;
import com.beone.api.benefit.rule.FractionalWonPolicy;
import com.beone.api.benefit.rule.LimitBucket;
import com.beone.api.benefit.rule.LimitDeductionOrder;
import com.beone.api.benefit.rule.LimitPeriod;
import com.beone.api.benefit.rule.LimitTier;
import com.beone.api.benefit.rule.MerchantTarget;
import com.beone.api.benefit.rule.OfficialSource;
import com.beone.api.benefit.rule.PerformanceWindow;
import com.beone.api.benefit.rule.RulePolicy;
import com.beone.api.benefit.rule.RuleTerm;
import com.beone.api.benefit.rule.ServiceDetails;
import com.beone.api.benefit.rule.ServiceTier;
import com.beone.api.benefit.rule.TransactionDateBasis;
import com.beone.api.benefit.rule.ValidityPeriod;
import com.beone.api.benefit.rule.VerificationStatus;

/**
 * 카드 상품 조회 단위 테스트용 합성 규칙. 값은 실제 약관을 주장하지 않는다.
 */
final class CardRuleFixtures {

	static final List<OfficialSource> SOURCES = List.of(OfficialSource.of("https://example.invalid/a.pdf", "p.1"),
			new OfficialSource("https://example.invalid/b", Optional.empty()));

	private CardRuleFixtures() {
	}

	static RulePolicy policy(RuleTerm<CancellationAttribution> cancellation, RuleTerm<LimitPeriod> limitPeriod,
			RuleTerm<LimitDeductionOrder> deductionOrder) {
		return new RulePolicy(RuleTerm.resolved(PerformanceWindow.PREVIOUS_CALENDAR_MONTH),
				RuleTerm.resolved(TransactionDateBasis.APPROVAL), cancellation, Set.of(TransactionNature.TAX), Set.of(),
				RuleTerm.resolved(true), limitPeriod, deductionOrder, RuleTerm.resolved(Optional.empty()));
	}

	static RulePolicy resolvedPolicy() {
		return policy(RuleTerm.resolved(CancellationAttribution.RECEIPT_MONTH), RuleTerm.resolved(LimitPeriod.CALENDAR_MONTH),
				RuleTerm.resolved(LimitDeductionOrder.PURCHASE_ORDER));
	}

	static BenefitService service(String id, List<String> buckets, boolean requiresSelection, String displayName) {
		ServiceDetails details = new ServiceDetails(Optional.ofNullable(displayName), Optional.empty(),
				Optional.empty(), Set.of(), List.of());
		return new BenefitService(ServiceId.of(id), BenefitKind.BILLING_DISCOUNT, MerchantTarget.allMerchants(),
				Set.of(), EnumSet.allOf(PaymentMethod.class), Won.ZERO, Optional.empty(),
				List.of(new ServiceTier(Won.of(300_000), RuleTerm.resolved(BenefitCalculation.rate(500)))),
				buckets.stream().map(LimitBucketId::of).toList(), requiresSelection, RuleTerm.resolved(Set.of()),
				RuleTerm.resolved(FractionalWonPolicy.TRUNCATE), details);
	}

	static LimitBucket bucket(String id, String parent, LimitTier... tiers) {
		return new LimitBucket(LimitBucketId.of(id), List.of(tiers), Optional.ofNullable(parent).map(LimitBucketId::of));
	}

	static LimitTier tier(long minimumPerformance, long cap) {
		return new LimitTier(Won.of(minimumPerformance), Won.of(cap));
	}

	static CardRuleVersion rule(String cardId, String version, ValidityPeriod validity, VerificationStatus status,
			RulePolicy policy, List<BenefitService> services, List<LimitBucket> buckets) {
		return new CardRuleVersion(CardId.of(cardId), version, validity, status, false, SOURCES, policy, services,
				buckets);
	}

	/** 한도 없는 서비스 하나만 가진 VERIFIED 규칙. */
	static CardRuleVersion simpleRule(String cardId, String version, ValidityPeriod validity) {
		return rule(cardId, version, validity, VerificationStatus.VERIFIED, resolvedPolicy(),
				List.of(service("BASE", List.of(), false, null)), List.of());
	}

	static ValidityPeriod between(String from, String to) {
		return ValidityPeriod.between(LocalDate.parse(from), LocalDate.parse(to));
	}

	static ValidityPeriod from(String from) {
		return ValidityPeriod.startingAt(LocalDate.parse(from));
	}

}
