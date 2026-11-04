package com.beone.api.benefit.primitive;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.beone.api.benefit.card.LimitBucketId;
import com.beone.api.benefit.card.MerchantClass;
import com.beone.api.benefit.card.PaymentMethod;
import com.beone.api.benefit.card.ServiceId;
import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.rule.BenefitCalculation;
import com.beone.api.benefit.rule.BenefitKind;
import com.beone.api.benefit.rule.BenefitService;
import com.beone.api.benefit.state.BasisPeriod;
import com.beone.api.benefit.state.StateValue;
import com.beone.api.benefit.rule.LimitBucket;
import com.beone.api.benefit.rule.LimitTier;
import com.beone.api.benefit.rule.MerchantTarget;
import com.beone.api.benefit.rule.RuleTerm;
import com.beone.api.benefit.rule.ServiceTier;

/**
 * 가상 서비스·업종으로 만든 합성 테스트 데이터. 실제 카드 약관을 주장하지 않는다.
 */
final class PrimitiveTestData {

	/** 유예 기간이 지난 합성 등록일. 유예가 없는 규칙에서는 쓰이지 않는다. */
	static final StateValue<LocalDate> REGISTERED = StateValue.synthetic(LocalDate.of(2020, 1, 1),
			BasisPeriod.day(LocalDate.of(2020, 1, 1)));

	static final MerchantClass CAFE = MerchantClass.of("SYNTHETIC_CAFE");

	static final MerchantClass CINEMA = MerchantClass.of("SYNTHETIC_CINEMA");

	static final MerchantClass GIFT_CARD = MerchantClass.of("SYNTHETIC_GIFT_CARD");

	private PrimitiveTestData() {
	}

	static BenefitService service(MerchantTarget target, Set<MerchantClass> excluded, Set<PaymentMethod> methods,
			Won minimumAmount, List<ServiceTier> tiers) {
		return new BenefitService(ServiceId.of("SYN-SVC"), BenefitKind.BILLING_DISCOUNT, target, excluded, methods,
				minimumAmount, Optional.empty(), tiers, List.of(), false, RuleTerm.resolved(Set.of()),
				RuleTerm.unresolved("synthetic: fractional won policy not confirmed"));
	}

	static BenefitService serviceWithMinimum(Won minimumAmount) {
		return service(MerchantTarget.allMerchants(), Set.of(), Set.of(PaymentMethod.DIRECT), minimumAmount,
				List.of(tier(0, 100)));
	}

	static ServiceTier tier(long minimumPerformance, int basisPoints) {
		return new ServiceTier(Won.of(minimumPerformance), RuleTerm.resolved(BenefitCalculation.rate(basisPoints)));
	}

	static LimitBucket bucket(LimitTier... tiers) {
		return new LimitBucket(LimitBucketId.of("SYN-BUCKET"), List.of(tiers), Optional.empty());
	}

	static LimitTier limit(long minimumPerformance, long monthlyCap) {
		return new LimitTier(Won.of(minimumPerformance), Won.of(monthlyCap));
	}

}
