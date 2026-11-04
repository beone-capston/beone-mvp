package com.beone.api.benefit.rule;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.card.LimitBucketId;
import com.beone.api.benefit.card.MerchantClass;
import com.beone.api.benefit.card.PaymentMethod;
import com.beone.api.benefit.card.ServiceId;
import com.beone.api.benefit.card.TransactionNature;
import com.beone.api.benefit.money.Won;

/**
 * 규칙 형태의 테스트 데이터 생성 도구. 값은 합성이며 실제 약관을 주장하지 않는다.
 */
final class RuleTestData {

	static final CardId CARD = CardId.of("09174");

	private RuleTestData() {
	}

	static RulePolicy resolvedPolicy() {
		return new RulePolicy(RuleTerm.resolved(PerformanceWindow.PREVIOUS_CALENDAR_MONTH),
				RuleTerm.resolved(TransactionDateBasis.APPROVAL), RuleTerm.resolved(CancellationAttribution.RECEIPT_MONTH),
				Set.of(TransactionNature.TAX), Set.of(), RuleTerm.resolved(true),
				RuleTerm.resolved(LimitPeriod.CALENDAR_MONTH), RuleTerm.resolved(LimitDeductionOrder.APPROVAL_ORDER),
				RuleTerm.resolved(Optional.empty()));
	}

	static BenefitService service(String id, Won minimumPerformance, RuleTerm<BenefitCalculation> calculation,
			List<LimitBucketId> buckets) {
		return new BenefitService(ServiceId.of(id), BenefitKind.BILLING_DISCOUNT,
				MerchantTarget.only(Set.of(MerchantClass.of("STARBUCKS_STORE"))), Set.of(),
				EnumSet.allOf(PaymentMethod.class), Won.ZERO, Optional.empty(),
				List.of(new ServiceTier(minimumPerformance, calculation)), buckets, false, RuleTerm.resolved(Set.of()),
				RuleTerm.resolved(FractionalWonPolicy.TRUNCATE));
	}

	static BenefitService rateService(String id) {
		return service(id, Won.of(300_000), RuleTerm.resolved(BenefitCalculation.rate(5_000)),
				List.of(LimitBucketId.of(id)));
	}

	static LimitBucket bucket(String id) {
		return new LimitBucket(LimitBucketId.of(id), List.of(new LimitTier(Won.of(300_000), Won.of(10_000))),
				Optional.empty());
	}

	static CardRuleVersion rule(String version, ValidityPeriod validity, VerificationStatus status,
			boolean evaluationOnly) {
		return new CardRuleVersion(CARD, version, validity, status, evaluationOnly,
				List.of(OfficialSource.of("https://example.invalid/terms.pdf", "p.1")), resolvedPolicy(),
				List.of(rateService("Great")), List.of(bucket("Great")));
	}

	static ValidityPeriod between(String from, String to) {
		return ValidityPeriod.between(LocalDate.parse(from), LocalDate.parse(to));
	}

}
