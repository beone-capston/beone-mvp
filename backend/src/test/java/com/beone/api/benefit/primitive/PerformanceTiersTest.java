package com.beone.api.benefit.primitive;

import static com.beone.api.benefit.primitive.PrimitiveTestData.bucket;
import static com.beone.api.benefit.primitive.PrimitiveTestData.limit;
import static com.beone.api.benefit.primitive.PrimitiveTestData.service;
import static com.beone.api.benefit.primitive.PrimitiveTestData.tier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.beone.api.benefit.card.PaymentMethod;
import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.rule.BenefitService;
import com.beone.api.benefit.rule.LimitBucket;
import com.beone.api.benefit.rule.MerchantTarget;
import com.beone.api.benefit.rule.RuleTerm;
import com.beone.api.benefit.rule.ServiceTier;

class PerformanceTiersTest {

	// 합성 구간: 30만 이상 1%, 60만 이상 2%
	private final BenefitService tiered = service(MerchantTarget.allMerchants(), Set.of(),
			Set.of(PaymentMethod.DIRECT), Won.ZERO, List.of(tier(300_000, 100), tier(600_000, 200)));

	// 합성 한도: 20만 이상 1만, 30만 이상 2만
	private final LimitBucket bucket = bucket(limit(200_000, 10_000), limit(300_000, 20_000));

	@Test
	void belowLowestTierIsNotReached() {
		assertThat(PerformanceTiers.serviceTier(tiered, Won.of(299_999))).isEmpty();
		assertThat(PerformanceTiers.limitTier(bucket, Won.of(199_999))).isEmpty();
	}

	@Test
	void tierBoundaryIsInclusive() {
		assertThat(PerformanceTiers.serviceTier(tiered, Won.of(300_000))).contains(tier(300_000, 100));
		assertThat(PerformanceTiers.serviceTier(tiered, Won.of(599_999))).contains(tier(300_000, 100));
		assertThat(PerformanceTiers.serviceTier(tiered, Won.of(600_000))).contains(tier(600_000, 200));
		assertThat(PerformanceTiers.limitTier(bucket, Won.of(200_000))).contains(limit(200_000, 10_000));
		assertThat(PerformanceTiers.limitTier(bucket, Won.of(300_000))).contains(limit(300_000, 20_000));
	}

	@Test
	void largestPerformanceReachesTopTier() {
		assertThat(PerformanceTiers.serviceTier(tiered, Won.of(Long.MAX_VALUE))).contains(tier(600_000, 200));
		assertThat(PerformanceTiers.limitTier(bucket, Won.of(Long.MAX_VALUE))).contains(limit(300_000, 20_000));
	}

	@Test
	void zeroMinimumTierCoversZeroPerformance() {
		BenefitService noRequirement = service(MerchantTarget.allMerchants(), Set.of(), Set.of(PaymentMethod.DIRECT),
				Won.ZERO, List.of(tier(0, 100)));

		assertThat(PerformanceTiers.serviceTier(noRequirement, Won.ZERO)).contains(tier(0, 100));
	}

	@Test
	void negativePerformanceIsRejectedInsteadOfGuessed() {
		// 음수 실적을 구간 미충족으로 볼지 실적 조건 없음으로 볼지 근거가 없다.
		assertThatThrownBy(() -> PerformanceTiers.serviceTier(tiered, Won.of(-1)))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> PerformanceTiers.limitTier(bucket, Won.of(-1)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void unresolvedCalculationTierIsReturnedForCallerToCheck() {
		ServiceTier unresolved = new ServiceTier(Won.of(300_000), RuleTerm.unresolved("synthetic: rate not confirmed"));
		BenefitService service = service(MerchantTarget.allMerchants(), Set.of(), Set.of(PaymentMethod.DIRECT),
				Won.ZERO, List.of(unresolved));

		assertThat(PerformanceTiers.serviceTier(service, Won.of(300_000))).contains(unresolved);
	}

	@Test
	void rejectsMissingPerformance() {
		assertThatThrownBy(() -> PerformanceTiers.serviceTier(tiered, null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> PerformanceTiers.limitTier(bucket, null)).isInstanceOf(NullPointerException.class);
	}

}
