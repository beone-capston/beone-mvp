package com.beone.api.benefit.primitive;

import static com.beone.api.benefit.primitive.PrimitiveTestData.CAFE;
import static com.beone.api.benefit.primitive.PrimitiveTestData.CINEMA;
import static com.beone.api.benefit.primitive.PrimitiveTestData.GIFT_CARD;
import static com.beone.api.benefit.primitive.PrimitiveTestData.tier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.beone.api.benefit.card.LimitBucketId;
import com.beone.api.benefit.card.MerchantClass;
import com.beone.api.benefit.card.PaymentMethod;
import com.beone.api.benefit.card.ServiceId;
import com.beone.api.benefit.input.Order;
import com.beone.api.benefit.money.BenefitAmount;
import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.result.ApplicationStatus;
import com.beone.api.benefit.result.ServiceCandidate;
import com.beone.api.benefit.rule.BenefitCalculation;
import com.beone.api.benefit.rule.BenefitKind;
import com.beone.api.benefit.rule.BenefitService;
import com.beone.api.benefit.rule.FractionalWonPolicy;
import com.beone.api.benefit.rule.MerchantTarget;
import com.beone.api.benefit.rule.RuleTerm;
import com.beone.api.benefit.rule.ServiceTier;
import com.beone.api.benefit.rule.TriggerEvidence;

/**
 * 가상 서비스·업종의 합성 값으로 단일 서비스 후보 판정을 확인한다. 실제 카드 약관의 계산 결과를 주장하지 않는다.
 */
class ServiceCandidateEvaluatorTest {

	private static final ServiceId SERVICE = ServiceId.of("SYN-SVC");

	private static final String FRACTION_REASON = "synthetic: fractional won policy not confirmed";

	private static final TriggerEvidence NO_GRACE = TriggerEvidence.CONFIRMED_ABSENT;

	private static final Won NO_PERFORMANCE = Won.ZERO;

	private static ServiceCandidate evaluate(BenefitService service, Order order) {
		return ServiceCandidateEvaluator.evaluate(service, order, NO_PERFORMANCE, NO_GRACE, ServiceLimits.NONE);
	}

	@Nested
	class Calculation {

		@Test
		void rateWithoutFractionIsConfirmedEvenIfFractionPolicyIsUnresolved() {
			// 10,000 × 100bp = 100원. 원 미만이 없으므로 원 미만 처리 미확정은 결과를 막지 않는다.
			ServiceCandidate candidate = evaluate(rateService(100), order(10_000));

			assertConfirmed(candidate, 100);
		}

		@Test
		void fixedAmountDoesNotDependOnOrderAmount() {
			BenefitService fixed = service().minimumAmount(50_000).tiers(fixedTier(0, 2_500)).build();

			assertConfirmed(evaluate(fixed, order(50_000)), 2_500);
			assertConfirmed(evaluate(fixed, order(80_000)), 2_500);
		}

		@Test
		void rateAppliesOnlyUpToMaximumTargetAmount() {
			// 상한 20,000원: 30,000 × 3,500bp → 20,000 × 3,500bp = 7,000원
			BenefitService capped = service().maximumTargetAmount(20_000).tiers(tier(0, 3_500)).build();

			assertConfirmed(evaluate(capped, order(30_000)), 7_000);
			assertConfirmed(evaluate(capped, order(20_000)), 7_000);
			assertConfirmed(evaluate(capped, order(10_000)), 3_500);
		}

		@Test
		void fixedAmountEqualToOrderAmountIsConfirmed() {
			ServiceCandidate candidate = evaluate(fixedService(3_000), order(3_000));

			assertConfirmed(candidate, 3_000);
		}

		@Test
		void fixedAmountAboveOrderAmountNeedsConfirmation() {
			// 결제 금액으로 줄이거나 초과 금액·0원으로 확정하지 않는다.
			ServiceCandidate candidate = evaluate(fixedService(3_000), order(2_999));

			assertNeedsConfirmation(candidate, "exceeds order amount");
		}

		@Test
		void unresolvedStackingDoesNotBlockStandaloneCandidate() {
			// 후보는 이 서비스 하나만 고를 때의 금액이므로 중복 적용 조건은 채택 단계의 몫이다.
			BenefitService service = service().stackableWith(RuleTerm.unresolved("synthetic: stacking not confirmed"))
				.build();

			assertConfirmed(evaluate(service, order(10_000)), 100);
		}

		@Test
		void unresolvedStackingDoesNotBlockWhenFractionOccurs() {
			// 원 미만이 생겨도 원 미만 처리 조건만 보고 중복 적용 조건은 보지 않는다. 10,001 × 150bp = 150.015원
			BenefitService service = service().tiers(tier(0, 150))
				.fractionalWon(FractionalWonPolicy.TRUNCATE)
				.stackableWith(RuleTerm.unresolved("synthetic: stacking not confirmed"))
				.build();

			assertConfirmed(evaluate(service, order(10_001)), 150);
		}

	}

	@Nested
	class FractionalWon {

		@Test
		void truncatesWhenPolicyIsConfirmed() {
			// 10,001 × 150bp = 150.015원
			BenefitService service = service().tiers(tier(0, 150)).fractionalWon(FractionalWonPolicy.TRUNCATE).build();

			assertConfirmed(evaluate(service, order(10_001)), 150);
		}

		@Test
		void roundsHalfUpWhenPolicyIsConfirmed() {
			// 10,035 × 150bp = 150.525원
			BenefitService service = service().tiers(tier(0, 150))
				.fractionalWon(FractionalWonPolicy.ROUND_HALF_UP)
				.build();

			assertConfirmed(evaluate(service, order(10_035)), 151);
		}

		@Test
		void needsConfirmationWhenFractionOccursAndPolicyIsUnresolved() {
			ServiceCandidate candidate = evaluate(rateService(150), order(10_001));

			assertNeedsConfirmation(candidate, FRACTION_REASON);
		}

	}

	@Nested
	class Conditions {

		@Test
		void minimumAmountIsInclusive() {
			BenefitService service = service().minimumAmount(50_000).build();

			assertConfirmed(evaluate(service, order(50_000)), 500);
			assertUnmet(evaluate(service, order(49_999)), ApplicationStatus.NOT_APPLICABLE_MIN_AMOUNT);
		}

		@Test
		void performanceTierBoundaryIsInclusive() {
			BenefitService service = service().tiers(tier(300_000, 100)).build();

			assertConfirmed(ServiceCandidateEvaluator.evaluate(service, order(10_000), Won.of(300_000), NO_GRACE, ServiceLimits.NONE), 100);
			assertUnmet(ServiceCandidateEvaluator.evaluate(service, order(10_000), Won.of(299_999), NO_GRACE, ServiceLimits.NONE),
					ApplicationStatus.NOT_APPLICABLE_PERFORMANCE);
		}

		@Test
		void higherTierUsesItsOwnCalculation() {
			BenefitService service = service().tiers(tier(0, 100), tier(300_000, 200)).build();

			assertConfirmed(ServiceCandidateEvaluator.evaluate(service, order(10_000), Won.of(299_999), NO_GRACE, ServiceLimits.NONE), 100);
			assertConfirmed(ServiceCandidateEvaluator.evaluate(service, order(10_000), Won.of(300_000), NO_GRACE, ServiceLimits.NONE), 200);
		}

		@Test
		void excludedClass() {
			BenefitService service = service().excluded(GIFT_CARD).build();

			assertUnmet(evaluate(service, order(10_000, GIFT_CARD, PaymentMethod.DIRECT)),
					ApplicationStatus.NOT_APPLICABLE_EXCLUDED);
		}

		@Test
		void nonTargetClass() {
			BenefitService service = service().target(MerchantTarget.only(Set.of(CAFE))).build();

			assertConfirmed(evaluate(service, order(10_000, CAFE, PaymentMethod.DIRECT)), 100);
			assertUnmet(evaluate(service, order(10_000, CINEMA, PaymentMethod.DIRECT)),
					ApplicationStatus.NOT_APPLICABLE_CATEGORY);
		}

		@Test
		void unlistedPaymentMethod() {
			BenefitService service = service().paymentMethods(PaymentMethod.AUTOPAY).build();

			assertUnmet(evaluate(service, order(10_000, CAFE, PaymentMethod.DIRECT)),
					ApplicationStatus.NOT_APPLICABLE_PAYMENT_METHOD);
		}

		@Test
		void everyConfirmedUnmetConditionIsCollected() {
			BenefitService service = service().target(MerchantTarget.only(Set.of(CAFE)))
				.excluded(GIFT_CARD)
				.paymentMethods(PaymentMethod.AUTOPAY)
				.minimumAmount(50_000)
				.tiers(tier(300_000, 100))
				.build();

			ServiceCandidate candidate = ServiceCandidateEvaluator.evaluate(service,
					order(10_000, GIFT_CARD, PaymentMethod.DIRECT), Won.of(100_000), NO_GRACE, ServiceLimits.NONE);

			assertUnmet(candidate, ApplicationStatus.NOT_APPLICABLE_EXCLUDED, ApplicationStatus.NOT_APPLICABLE_CATEGORY,
					ApplicationStatus.NOT_APPLICABLE_PAYMENT_METHOD, ApplicationStatus.NOT_APPLICABLE_MIN_AMOUNT,
					ApplicationStatus.NOT_APPLICABLE_PERFORMANCE);
		}

	}

	@Nested
	class NewCardGrace {

		@ParameterizedTest
		@EnumSource(value = TriggerEvidence.class, names = { "PRESENT", "UNDETERMINED" })
		void performanceShortfallNeedsConfirmationUnlessGraceIsConfirmedAbsent(TriggerEvidence grace) {
			BenefitService service = service().tiers(tier(300_000, 100)).build();

			ServiceCandidate candidate = ServiceCandidateEvaluator.evaluate(service, order(10_000), Won.of(299_999),
					grace, ServiceLimits.NONE);

			assertNeedsConfirmation(candidate, "below every tier");
		}

		@ParameterizedTest
		@EnumSource(value = TriggerEvidence.class, names = { "PRESENT", "UNDETERMINED" })
		void lowerTierNeedsConfirmationUnlessGraceIsConfirmedAbsent(TriggerEvidence grace) {
			// 유예의 간주 실적이 더 높은 구간을 만들 수 있으므로 낮은 구간 금액을 확정하지 않는다.
			BenefitService service = service().tiers(tier(0, 100), tier(300_000, 200)).build();

			ServiceCandidate candidate = ServiceCandidateEvaluator.evaluate(service, order(10_000), Won.of(100_000),
					grace, ServiceLimits.NONE);

			assertNeedsConfirmation(candidate, "higher tier");
		}

		@ParameterizedTest
		@EnumSource(TriggerEvidence.class)
		void highestTierOrNoPerformanceRequirementIgnoresGrace(TriggerEvidence grace) {
			BenefitService tiered = service().tiers(tier(0, 100), tier(300_000, 200)).build();
			BenefitService flat = rateService(100);

			assertConfirmed(ServiceCandidateEvaluator.evaluate(tiered, order(10_000), Won.of(300_000), grace, ServiceLimits.NONE), 200);
			assertConfirmed(ServiceCandidateEvaluator.evaluate(flat, order(10_000), Won.ZERO, grace, ServiceLimits.NONE), 100);
		}

	}

	@Nested
	class UnmetWithUnresolved {

		@Test
		void unresolvedCalculationNeedsConfirmation() {
			BenefitService service = service()
				.tiers(new ServiceTier(Won.ZERO, RuleTerm.unresolved("synthetic: calculation not confirmed")))
				.build();

			assertNeedsConfirmation(evaluate(service, order(10_000)), "synthetic: calculation not confirmed");
		}

		@Test
		void unresolvedCalculationInAnotherTierNeedsConfirmation() {
			BenefitService service = service().tiers(tier(0, 100),
					new ServiceTier(Won.of(300_000), RuleTerm.unresolved("synthetic: upper tier not confirmed")))
				.build();

			assertNeedsConfirmation(evaluate(service, order(10_000)), "synthetic: upper tier not confirmed");
		}

		@Test
		void confirmedUnmetWinsOverUnresolvedCalculation() {
			BenefitService service = service().paymentMethods(PaymentMethod.AUTOPAY)
				.tiers(new ServiceTier(Won.ZERO, RuleTerm.unresolved("synthetic: calculation not confirmed")))
				.build();

			assertUnmet(evaluate(service, order(10_000)), ApplicationStatus.NOT_APPLICABLE_PAYMENT_METHOD);
		}

		@Test
		void confirmedUnmetWinsOverUndeterminedGrace() {
			// 유예 근거가 미확정이므로 실적 사유는 넣지 않고, 확인된 업종 사유만으로 확정 0원이다.
			BenefitService service = service().target(MerchantTarget.only(Set.of(CAFE)))
				.tiers(tier(300_000, 100))
				.build();

			ServiceCandidate candidate = ServiceCandidateEvaluator.evaluate(service,
					order(10_000, CINEMA, PaymentMethod.DIRECT), Won.of(100_000), TriggerEvidence.UNDETERMINED, ServiceLimits.NONE);

			assertUnmet(candidate, ApplicationStatus.NOT_APPLICABLE_CATEGORY);
		}

		@Test
		void confirmedUnmetWinsOverLowerTierUnderUndeterminedGrace() {
			BenefitService service = service().paymentMethods(PaymentMethod.AUTOPAY)
				.tiers(tier(0, 100), tier(300_000, 200))
				.build();

			ServiceCandidate candidate = ServiceCandidateEvaluator.evaluate(service, order(10_000), Won.of(100_000),
					TriggerEvidence.UNDETERMINED, ServiceLimits.NONE);

			assertUnmet(candidate, ApplicationStatus.NOT_APPLICABLE_PAYMENT_METHOD);
		}

		@Test
		void confirmedUnmetWinsOverFractionAndExcessFixedAmount() {
			BenefitService fraction = service().tiers(tier(0, 150)).minimumAmount(20_000).build();
			BenefitService excess = service().paymentMethods(PaymentMethod.AUTOPAY).tiers(fixedTier(0, 3_000)).build();

			assertUnmet(evaluate(fraction, order(10_001)), ApplicationStatus.NOT_APPLICABLE_MIN_AMOUNT);
			assertUnmet(evaluate(excess, order(2_999)), ApplicationStatus.NOT_APPLICABLE_PAYMENT_METHOD);
		}

		@Test
		void performanceAndCalculationReasonsAreReportedTogether() {
			BenefitService service = service().tiers(new ServiceTier(Won.of(300_000), RuleTerm.unresolved("synthetic: calc")))
				.build();

			ServiceCandidate candidate = ServiceCandidateEvaluator.evaluate(service, order(10_000), Won.ZERO,
					TriggerEvidence.UNDETERMINED, ServiceLimits.NONE);

			assertNeedsConfirmation(candidate, "below every tier");
			assertNeedsConfirmation(candidate, "synthetic: calc");
		}

	}

	@Nested
	class Scope {

		@Test
		void serviceWithLimitBucketIsOutOfScope() {
			// 서비스에는 버킷 ID만 있으며 한도 값의 확인 여부와 관계없이 버킷이 있으면 범위 밖이다.
			BenefitService limited = service().limitBuckets(LimitBucketId.of("SYN-BUCKET")).build();

			assertThatThrownBy(() -> evaluate(limited, order(10_000))).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("limit buckets");
		}

		@Test
		void outOfScopeIsRejectedEvenWhenConditionsAreUnmet() {
			BenefitService limited = service().limitBuckets(LimitBucketId.of("SYN-BUCKET"))
				.paymentMethods(PaymentMethod.AUTOPAY)
				.build();

			assertThatThrownBy(() -> evaluate(limited, order(10_000))).hasMessageContaining("limit buckets");
		}

		@Test
		void selectableServiceIsOutOfScope() {
			BenefitService selectable = service().requiresSelection().build();

			assertThatThrownBy(() -> evaluate(selectable, order(10_000))).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("requires selection");
		}

	}

	@Nested
	class InputContract {

		@Test
		void nullInputsAreRejected() {
			BenefitService service = rateService(100);
			Order order = order(10_000);

			assertThatThrownBy(() -> ServiceCandidateEvaluator.evaluate(null, order, Won.ZERO, NO_GRACE, ServiceLimits.NONE))
				.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> ServiceCandidateEvaluator.evaluate(service, null, Won.ZERO, NO_GRACE, ServiceLimits.NONE))
				.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> ServiceCandidateEvaluator.evaluate(service, order, null, NO_GRACE, ServiceLimits.NONE))
				.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> ServiceCandidateEvaluator.evaluate(service, order, Won.ZERO, null, ServiceLimits.NONE))
				.isInstanceOf(NullPointerException.class);
		}

		@Test
		void negativePerformanceIsRejectedEvenWhenConditionsAreUnmet() {
			BenefitService unmet = service().paymentMethods(PaymentMethod.AUTOPAY).build();

			assertThatThrownBy(() -> ServiceCandidateEvaluator.evaluate(rateService(100), order(10_000), Won.of(-1),
					NO_GRACE, ServiceLimits.NONE))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> ServiceCandidateEvaluator.evaluate(unmet, order(10_000), Won.of(-1), NO_GRACE, ServiceLimits.NONE))
				.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		void nonPositiveOrderAmountCannotReachEvaluator() {
			// 주문 금액 계약은 Order가 생성 시 검증한다.
			assertThatThrownBy(() -> order(0)).isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> order(-1)).isInstanceOf(IllegalArgumentException.class);
		}

	}

	@Nested
	class ResultContract {

		@Test
		void sameInputGivesEqualResult() {
			BenefitService service = service().paymentMethods(PaymentMethod.AUTOPAY).minimumAmount(50_000).build();

			assertThat(evaluate(service, order(10_000))).isEqualTo(evaluate(service, order(10_000)));
			assertThat(evaluate(rateService(150), order(10_001))).isEqualTo(evaluate(rateService(150), order(10_001)));
		}

		@Test
		void unmetConditionsCannotBeModified() {
			BenefitService service = service().paymentMethods(PaymentMethod.AUTOPAY).build();
			Set<ApplicationStatus> unmet = evaluate(service, order(10_000)).unmetConditions();

			assertThatThrownBy(() -> unmet.add(ApplicationStatus.NOT_APPLICABLE_CATEGORY))
				.isInstanceOf(UnsupportedOperationException.class);
			assertThatThrownBy(unmet::clear).isInstanceOf(UnsupportedOperationException.class);
		}

		@Test
		void existingCandidateContractStillRejectsNonZeroUnmetCandidate() {
			assertThatThrownBy(() -> new ServiceCandidate(SERVICE, BenefitAmount.confirmed(100),
					Set.of(ApplicationStatus.NOT_APPLICABLE_PAYMENT_METHOD)))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> new ServiceCandidate(SERVICE, BenefitAmount.needsConfirmation("x"),
					Set.of(ApplicationStatus.NOT_APPLICABLE_PAYMENT_METHOD)))
				.isInstanceOf(IllegalArgumentException.class);
		}

	}

	private static void assertConfirmed(ServiceCandidate candidate, long won) {
		assertThat(candidate.serviceId()).isEqualTo(SERVICE);
		assertThat(candidate.standaloneAmount()).isEqualTo(BenefitAmount.confirmed(won));
		assertThat(candidate.unmetConditions()).isEmpty();
	}

	private static void assertUnmet(ServiceCandidate candidate, ApplicationStatus... conditions) {
		assertThat(candidate.standaloneAmount()).isEqualTo(BenefitAmount.confirmed(0));
		assertThat(candidate.unmetConditions()).containsExactlyInAnyOrder(conditions);
	}

	private static void assertNeedsConfirmation(ServiceCandidate candidate, String reasonPart) {
		// 확인 필요 사유는 미충족 사유 집합에 넣지 않는다.
		assertThat(candidate.unmetConditions()).isEmpty();
		assertThat(candidate.standaloneAmount().isConfirmed()).isFalse();
		assertThat(((BenefitAmount.NeedsConfirmation) candidate.standaloneAmount()).reason()).contains(reasonPart);
	}

	private static Order order(long amount) {
		return order(amount, CAFE, PaymentMethod.DIRECT);
	}

	private static Order order(long amount, MerchantClass merchantClass, PaymentMethod paymentMethod) {
		return new Order(Optional.empty(), Optional.empty(), Won.of(amount), LocalDate.of(2026, 10, 15),
				Optional.empty(), merchantClass, paymentMethod);
	}

	private static BenefitService rateService(int basisPoints) {
		return service().tiers(tier(0, basisPoints)).build();
	}

	private static BenefitService fixedService(long won) {
		return service().tiers(fixedTier(0, won)).build();
	}

	private static ServiceTier fixedTier(long minimumPerformance, long won) {
		return new ServiceTier(Won.of(minimumPerformance), RuleTerm.resolved(BenefitCalculation.fixed(won)));
	}

	private static ServiceBuilder service() {
		return new ServiceBuilder();
	}

	/** 기본값: 전체 가맹점, 제외 없음, 직접 결제, 최소 금액 없음, 상한 없음, 0원 구간 1%, 한도 없음, 비선택형, 원 미만 처리 미확정. */
	private static final class ServiceBuilder {

		private MerchantTarget target = MerchantTarget.allMerchants();

		private Set<MerchantClass> excluded = Set.of();

		private Set<PaymentMethod> paymentMethods = Set.of(PaymentMethod.DIRECT);

		private Won minimumAmount = Won.ZERO;

		private Optional<Won> maximumTargetAmount = Optional.empty();

		private List<ServiceTier> tiers = List.of(tier(0, 100));

		private List<LimitBucketId> limitBuckets = List.of();

		private boolean requiresSelection;

		private RuleTerm<Set<ServiceId>> stackableWith = RuleTerm.resolved(Set.of());

		private RuleTerm<FractionalWonPolicy> fractionalWon = RuleTerm.unresolved(FRACTION_REASON);

		ServiceBuilder target(MerchantTarget value) {
			target = value;
			return this;
		}

		ServiceBuilder excluded(MerchantClass... values) {
			excluded = Set.of(values);
			return this;
		}

		ServiceBuilder paymentMethods(PaymentMethod... values) {
			paymentMethods = Set.of(values);
			return this;
		}

		ServiceBuilder minimumAmount(long won) {
			minimumAmount = Won.of(won);
			return this;
		}

		ServiceBuilder maximumTargetAmount(long won) {
			maximumTargetAmount = Optional.of(Won.of(won));
			return this;
		}

		ServiceBuilder tiers(ServiceTier... values) {
			tiers = List.of(values);
			return this;
		}

		ServiceBuilder limitBuckets(LimitBucketId... values) {
			limitBuckets = List.of(values);
			return this;
		}

		ServiceBuilder requiresSelection() {
			requiresSelection = true;
			return this;
		}

		ServiceBuilder stackableWith(RuleTerm<Set<ServiceId>> value) {
			stackableWith = value;
			return this;
		}

		ServiceBuilder fractionalWon(FractionalWonPolicy value) {
			fractionalWon = RuleTerm.resolved(value);
			return this;
		}

		BenefitService build() {
			return new BenefitService(SERVICE, BenefitKind.BILLING_DISCOUNT, target, excluded, paymentMethods,
					minimumAmount, maximumTargetAmount, tiers, limitBuckets, requiresSelection, stackableWith,
					fractionalWon);
		}

	}

}
