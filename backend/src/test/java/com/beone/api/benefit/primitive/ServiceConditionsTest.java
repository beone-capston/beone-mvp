package com.beone.api.benefit.primitive;

import static com.beone.api.benefit.primitive.PrimitiveTestData.CAFE;
import static com.beone.api.benefit.primitive.PrimitiveTestData.CINEMA;
import static com.beone.api.benefit.primitive.PrimitiveTestData.GIFT_CARD;
import static com.beone.api.benefit.primitive.PrimitiveTestData.service;
import static com.beone.api.benefit.primitive.PrimitiveTestData.serviceWithMinimum;
import static com.beone.api.benefit.primitive.PrimitiveTestData.tier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.beone.api.benefit.card.PaymentMethod;
import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.rule.BenefitService;
import com.beone.api.benefit.rule.MerchantTarget;

class ServiceConditionsTest {

	@Test
	void minimumAmountIsInclusive() {
		BenefitService service = serviceWithMinimum(Won.of(50_000));

		assertThat(ServiceConditions.meetsMinimumAmount(service, Won.of(49_999))).isFalse();
		assertThat(ServiceConditions.meetsMinimumAmount(service, Won.of(50_000))).isTrue();
		assertThat(ServiceConditions.meetsMinimumAmount(service, Won.of(Long.MAX_VALUE))).isTrue();
	}

	@Test
	void zeroMinimumAcceptsAnyNonNegativeAmount() {
		BenefitService service = serviceWithMinimum(Won.ZERO);

		assertThat(ServiceConditions.meetsMinimumAmount(service, Won.ZERO)).isTrue();
		assertThat(ServiceConditions.meetsMinimumAmount(service, Won.of(1))).isTrue();
	}

	@Test
	void paymentMethodMustBeListed() {
		BenefitService autopayOnly = service(MerchantTarget.allMerchants(), Set.of(), Set.of(PaymentMethod.AUTOPAY),
				Won.ZERO, List.of(tier(0, 100)));

		assertThat(ServiceConditions.acceptsPaymentMethod(autopayOnly, PaymentMethod.AUTOPAY)).isTrue();
		assertThat(ServiceConditions.acceptsPaymentMethod(autopayOnly, PaymentMethod.DIRECT)).isFalse();
		assertThat(ServiceConditions.acceptsPaymentMethod(autopayOnly, PaymentMethod.ELIGIBLE_SIMPLE_PAY)).isFalse();
	}

	@Test
	void specificTargetMatchesOnlyListedClasses() {
		BenefitService cafeOnly = service(MerchantTarget.only(Set.of(CAFE)), Set.of(), Set.of(PaymentMethod.DIRECT),
				Won.ZERO, List.of(tier(0, 100)));

		assertThat(ServiceConditions.isTargetClass(cafeOnly, CAFE)).isTrue();
		assertThat(ServiceConditions.isTargetClass(cafeOnly, CINEMA)).isFalse();
	}

	@Test
	void allMerchantsTargetStillReportsExclusionSeparately() {
		// 전체 가맹점 대상이어도 제외 업종은 따로 판정한다.
		BenefitService allButGiftCards = service(MerchantTarget.allMerchants(), Set.of(GIFT_CARD),
				Set.of(PaymentMethod.DIRECT), Won.ZERO, List.of(tier(0, 100)));

		assertThat(ServiceConditions.isTargetClass(allButGiftCards, GIFT_CARD)).isTrue();
		assertThat(ServiceConditions.isExcludedClass(allButGiftCards, GIFT_CARD)).isTrue();
		assertThat(ServiceConditions.isExcludedClass(allButGiftCards, CAFE)).isFalse();
	}

	@Test
	void rejectsMissingInput() {
		BenefitService service = serviceWithMinimum(Won.ZERO);

		assertThatThrownBy(() -> ServiceConditions.meetsMinimumAmount(service, null))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> ServiceConditions.meetsMinimumAmount(service, Won.of(-1)))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ServiceConditions.acceptsPaymentMethod(service, null))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> ServiceConditions.isTargetClass(service, null))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> ServiceConditions.isExcludedClass(null, CAFE))
			.isInstanceOf(NullPointerException.class);
	}

}
