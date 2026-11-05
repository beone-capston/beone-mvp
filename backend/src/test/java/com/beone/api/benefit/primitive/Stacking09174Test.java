package com.beone.api.benefit.primitive;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.card.LimitBucketId;
import com.beone.api.benefit.card.MerchantClass;
import com.beone.api.benefit.card.PaymentMethod;
import com.beone.api.benefit.card.ServiceId;
import com.beone.api.benefit.input.CardMonthlyState;
import com.beone.api.benefit.input.Order;
import com.beone.api.benefit.input.PolicyTermEvidence;
import com.beone.api.benefit.money.BenefitAmount;
import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.result.Adoption;
import com.beone.api.benefit.result.ApplicationStatus;
import com.beone.api.benefit.result.LimitReason;
import com.beone.api.benefit.result.OrderBenefit;
import com.beone.api.benefit.result.OrderServiceBenefit;
import com.beone.api.benefit.result.PendingReason;
import com.beone.api.benefit.rule.RuleCatalog;
import com.beone.api.benefit.rule.TriggerEvidence;
import com.beone.api.benefit.rule.loading.RuleCatalogLoader;
import com.beone.api.benefit.state.BasisPeriod;
import com.beone.api.benefit.state.StateValue;

/**
 * 규칙 JSON의 09174로 중복 합산 예외를 확인한다(D-13 #35 8번). 기대값은 규칙 근거 표와 결정 기록에서 손으로 계산했다.
 */
class Stacking09174Test {

	private static final RuleCatalog CATALOG = RuleCatalogLoader.load();

	private static final CardId CARD = CardId.of("09174");

	private static final LocalDate DAY = LocalDate.of(2026, 10, 15);

	private static final BasisPeriod OCTOBER = BasisPeriod.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

	private static final BasisPeriod SEPTEMBER = BasisPeriod.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

	private static final LocalDate OLD = LocalDate.of(2026, 1, 1);

	private static Order order(String merchantClass, long won, PaymentMethod method) {
		return new Order(Optional.empty(), Optional.empty(), Won.of(won), DAY, Optional.empty(),
				MerchantClass.of(merchantClass), method);
	}

	/** 모든 버킷 사용액은 0원이고, 지정한 버킷만 바꾼다. 값이 null이면 UNKNOWN이다. */
	private static CardMonthlyState state(Object... bucketThenWon) {
		Map<LimitBucketId, StateValue<Won>> used = new HashMap<>();
		for (String bucket : List.of("GREAT", "ENJOY", "CHECK", "BASIC")) {
			used.put(LimitBucketId.of(bucket), StateValue.synthetic(Won.ZERO, OCTOBER));
		}
		for (int i = 0; i < bucketThenWon.length; i += 2) {
			Object value = bucketThenWon[i + 1];
			used.put(LimitBucketId.of((String) bucketThenWon[i]),
					value == null ? StateValue.unknown(OCTOBER, null)
							: StateValue.synthetic(Won.of(((Number) value).longValue()), OCTOBER));
		}
		return new CardMonthlyState(CARD, Optional.of(StateValue.synthetic(Won.of(300_000), SEPTEMBER)), used,
				StateValue.synthetic(Won.ZERO, OCTOBER), List.of());
	}

	private static OrderBenefit.Decided decide(Order order, CardMonthlyState state, TriggerEvidence policy) {
		return (OrderBenefit.Decided) OrderBenefitResolver.resolve(CATALOG, CARD, order.date(), order, state,
				StateValue.synthetic(OLD, BasisPeriod.day(OLD)), new PolicyTermEvidence(CARD, policy, policy));
	}

	private static OrderBenefit.Decided decide(Order order, CardMonthlyState state) {
		return decide(order, state, TriggerEvidence.CONFIRMED_ABSENT);
	}

	private static OrderServiceBenefit service(OrderBenefit.Decided result, String id) {
		return result.services()
			.stream()
			.filter(service -> service.candidate().serviceId().equals(ServiceId.of(id)))
			.findFirst()
			.orElseThrow(() -> new AssertionError("no decided service " + id + " in " + result));
	}

	private static void assertAdopted(OrderBenefit.Decided result, String id, long won, ApplicationStatus status) {
		OrderServiceBenefit service = service(result, id);
		assertThat(service.adoption()).as(id).isEqualTo(Adoption.ADOPTED);
		assertThat(service.contribution()).as(id).isEqualTo(BenefitAmount.confirmed(won));
		assertThat(service.appliedStatus()).as(id).contains(status);
	}

	private static void assertUndetermined(OrderBenefit.Decided result, String id) {
		OrderServiceBenefit service = service(result, id);
		assertThat(service.adoption()).as(id).isEqualTo(Adoption.UNDETERMINED);
		assertThat(service.contribution().isConfirmed()).as(id).isFalse();
	}

	private static List<String> pending(OrderBenefit.Decided result) {
		return result.source().needingConfirmation().stream().map(item -> item.serviceId().toString()).toList();
	}

	private static List<PendingReason.Kind> kinds(OrderBenefit.Decided result) {
		return result.reasons().stream().map(PendingReason::kind).distinct().toList();
	}

	@Test
	void greatAndCheckFollowTheDocumentExample() {
		OrderBenefit.Decided result = decide(order("STARBUCKS_STORE", 10_000, PaymentMethod.ELIGIBLE_SIMPLE_PAY), state());

		assertThat(result.orderBenefit()).isEqualTo(BenefitAmount.confirmed(6_000));
		assertAdopted(result, "GREAT", 5_000, ApplicationStatus.APPLIED);
		assertAdopted(result, "CHECK", 1_000, ApplicationStatus.APPLIED);
	}

	@Test
	void enjoyStacksWithCheck() {
		OrderBenefit.Decided result = decide(order("BURGER_FASTFOOD", 10_000, PaymentMethod.ELIGIBLE_SIMPLE_PAY), state());

		assertThat(result.orderBenefit()).isEqualTo(BenefitAmount.confirmed(3_000));
		assertAdopted(result, "ENJOY", 2_000, ApplicationStatus.APPLIED);
		assertAdopted(result, "CHECK", 1_000, ApplicationStatus.APPLIED);
	}

	@Test
	void basicTransitStacksWithCheck() {
		OrderBenefit.Decided result = decide(order("TAXI", 10_000, PaymentMethod.ELIGIBLE_SIMPLE_PAY), state());

		assertThat(result.orderBenefit()).isEqualTo(BenefitAmount.confirmed(2_000));
		assertAdopted(result, "BASIC_TRANSIT", 1_000, ApplicationStatus.APPLIED);
		assertAdopted(result, "CHECK", 1_000, ApplicationStatus.APPLIED);
	}

	@Test
	void basicTelecomAndCheckAreNeverAppliedTogetherBecauseOfPaymentMethods() {
		OrderBenefit.Decided autopay = decide(order("TELECOM_SKT", 50_000, PaymentMethod.AUTOPAY), state());

		assertThat(autopay.orderBenefit()).isEqualTo(BenefitAmount.confirmed(5_000));
		assertAdopted(autopay, "BASIC_TELECOM", 5_000, ApplicationStatus.APPLIED);
		assertThat(service(autopay, "CHECK").adoption()).isEqualTo(Adoption.NOT_ADOPTED);
		assertThat(service(autopay, "CHECK").candidate().unmetConditions())
			.containsExactly(ApplicationStatus.NOT_APPLICABLE_PAYMENT_METHOD);

		OrderBenefit.Decided simplePay = decide(order("TELECOM_SKT", 50_000, PaymentMethod.ELIGIBLE_SIMPLE_PAY), state());

		assertThat(simplePay.orderBenefit()).isEqualTo(BenefitAmount.confirmed(5_000));
		assertAdopted(simplePay, "CHECK", 5_000, ApplicationStatus.APPLIED);
		assertThat(service(simplePay, "BASIC_TELECOM").candidate().unmetConditions())
			.containsExactly(ApplicationStatus.NOT_APPLICABLE_PAYMENT_METHOD);
	}

	@Test
	void eachServiceIsTruncatedBeforeTheTotalAndTheTotalIsNotTruncatedAgain() {
		// 5,002.5 + 1,000.5. 합계 뒤 절사였다면 6,003원이다.
		OrderBenefit.Decided result = decide(order("STARBUCKS_STORE", 10_005, PaymentMethod.ELIGIBLE_SIMPLE_PAY), state());

		assertThat(result.orderBenefit()).isEqualTo(BenefitAmount.confirmed(6_002));
		assertAdopted(result, "GREAT", 5_002, ApplicationStatus.APPLIED);
		assertAdopted(result, "CHECK", 1_000, ApplicationStatus.APPLIED);
		assertThat(service(result, "GREAT").candidate().fractionalWonTruncated()).isTrue();
		assertThat(service(result, "CHECK").candidate().fractionalWonTruncated()).isTrue();
	}

	@Test
	void eachServiceIsCutByItsOwnChain() {
		OrderBenefit.Decided result = decide(order("STARBUCKS_STORE", 20_000, PaymentMethod.ELIGIBLE_SIMPLE_PAY),
				state("GREAT", 9_000, "CHECK", 4_500));

		assertThat(result.orderBenefit()).isEqualTo(BenefitAmount.confirmed(1_500));
		assertAdopted(result, "GREAT", 1_000, ApplicationStatus.APPLIED_CAP_REMAINING);
		assertAdopted(result, "CHECK", 500, ApplicationStatus.APPLIED_CAP_REMAINING);
	}

	@Test
	void unknownCheckUsageLeavesGreatUndetermined() {
		OrderBenefit.Decided result = decide(order("STARBUCKS_STORE", 20_000, PaymentMethod.ELIGIBLE_SIMPLE_PAY),
				state("CHECK", null));

		assertThat(result.orderBenefit().isConfirmed()).isFalse();
		assertThat(pending(result)).containsExactly("CHECK");
		assertThat(kinds(result)).containsExactly(PendingReason.Kind.NEEDS_CONFIRMATION);
		assertUndetermined(result, "GREAT");
	}

	@Test
	void greatUsageAboveItsLimitLeavesCheckUndetermined() {
		OrderBenefit.Decided result = decide(order("STARBUCKS_STORE", 20_000, PaymentMethod.ELIGIBLE_SIMPLE_PAY),
				state("GREAT", 10_001));

		assertThat(result.orderBenefit().isConfirmed()).isFalse();
		assertThat(pending(result)).containsExactly("GREAT");
		assertThat(result.source().needingConfirmation().get(0).limitReason()).contains(LimitReason.USAGE_EXCEEDS_LIMIT);
		assertUndetermined(result, "CHECK");
	}

	@Test
	void exhaustedGreatLeavesCheckAlone() {
		OrderBenefit.Decided result = decide(order("STARBUCKS_STORE", 20_000, PaymentMethod.ELIGIBLE_SIMPLE_PAY),
				state("GREAT", 10_000));

		assertThat(result.orderBenefit()).isEqualTo(BenefitAmount.confirmed(2_000));
		assertThat(service(result, "GREAT").adoption()).isEqualTo(Adoption.NOT_ADOPTED);
		assertThat(service(result, "GREAT").candidate().unmetConditions()).containsExactly(ApplicationStatus.LIMIT_EXHAUSTED);
		assertAdopted(result, "CHECK", 2_000, ApplicationStatus.APPLIED);
	}

	@Test
	void directPaymentLeavesGreatAloneEvenWithUnknownCheckUsage() {
		OrderBenefit.Decided result = decide(order("STARBUCKS_STORE", 20_000, PaymentMethod.DIRECT), state("CHECK", null));

		assertThat(result.orderBenefit()).isEqualTo(BenefitAmount.confirmed(10_000));
		assertAdopted(result, "GREAT", 10_000, ApplicationStatus.APPLIED);
		assertThat(service(result, "CHECK").candidate().unmetConditions())
			.containsExactly(ApplicationStatus.NOT_APPLICABLE_PAYMENT_METHOD);
	}

	@Test
	void unresolvedPerformancePolicyLeavesBothUndetermined() {
		OrderBenefit.Decided result = decide(order("STARBUCKS_STORE", 20_000, PaymentMethod.ELIGIBLE_SIMPLE_PAY), state(),
				TriggerEvidence.UNDETERMINED);

		assertThat(result.orderBenefit().isConfirmed()).isFalse();
		assertThat(kinds(result)).containsExactly(PendingReason.Kind.PERFORMANCE_POLICY);
		assertUndetermined(result, "GREAT");
		assertUndetermined(result, "CHECK");
	}

	@Test
	void unconfirmedInclusionOfGreatLeavesCheckUndetermined() {
		OrderBenefit.Decided result = decide(order("COFFEE_BEVERAGE", 20_000, PaymentMethod.ELIGIBLE_SIMPLE_PAY), state());

		assertThat(result.orderBenefit().isConfirmed()).isFalse();
		assertThat(pending(result)).containsExactly("GREAT");
		assertUndetermined(result, "CHECK");
	}

}
