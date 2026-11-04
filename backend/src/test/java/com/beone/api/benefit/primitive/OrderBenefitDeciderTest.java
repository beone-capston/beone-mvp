package com.beone.api.benefit.primitive;

import static com.beone.api.benefit.primitive.PrimitiveTestData.CAFE;
import static com.beone.api.benefit.primitive.PrimitiveTestData.CINEMA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.card.LimitBucketId;
import com.beone.api.benefit.card.PaymentMethod;
import com.beone.api.benefit.card.ServiceId;
import com.beone.api.benefit.card.TransactionNature;
import com.beone.api.benefit.input.CardMonthlyState;
import com.beone.api.benefit.input.Order;
import com.beone.api.benefit.input.PolicyTermEvidence;
import com.beone.api.benefit.money.BenefitAmount;
import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.result.Adoption;
import com.beone.api.benefit.result.ApplicationStatus;
import com.beone.api.benefit.result.CardServiceCandidates;
import com.beone.api.benefit.result.OrderBenefit;
import com.beone.api.benefit.result.OrderServiceBenefit;
import com.beone.api.benefit.result.PendingReason;
import com.beone.api.benefit.result.PerformanceRecheck;
import com.beone.api.benefit.result.ServiceCandidate;
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
import com.beone.api.benefit.rule.RuleCatalog;
import com.beone.api.benefit.rule.RulePolicy;
import com.beone.api.benefit.rule.RuleSelection;
import com.beone.api.benefit.rule.RuleTerm;
import com.beone.api.benefit.rule.ServiceTier;
import com.beone.api.benefit.rule.TransactionDateBasis;
import com.beone.api.benefit.rule.TriggerEvidence;
import com.beone.api.benefit.rule.ValidityPeriod;
import com.beone.api.benefit.rule.VerificationStatus;
import com.beone.api.benefit.state.BasisPeriod;
import com.beone.api.benefit.state.StateValue;

/**
 * 가상 카드·서비스·업종의 합성 규칙으로 카드 한 장·주문 한 건의 혜택 결정을 확인한다. 실제 카드 약관의 계산 결과를 주장하지 않는다.
 */
class OrderBenefitDeciderTest {

	private static final CardId CARD = CardId.of("SYN-CARD");

	private static final LocalDate DAY = LocalDate.of(2026, 10, 15);

	private static final LimitBucketId BUCKET = LimitBucketId.of("SYN-BUCKET");

	private static final String CALCULATION_REASON = "synthetic: calculation not confirmed";

	private static final String CANCELLATION_REASON = "synthetic: cancellation attribution not confirmed";

	private static final String SALES_REASON = "synthetic: benefited sales exclusion not confirmed";

	private static final Order ORDER = order(10_000);

	/** 1% 정률이면 0.3원이 되어 원 미만 절사로 확정 0원이 되는 주문. */
	private static final Order SMALL_ORDER = order(30);

	private static final RuleTerm<Set<ServiceId>> NOT_STACKABLE = RuleTerm.resolved(Set.of());

	private static final RuleTerm<Set<ServiceId>> STACKING_UNKNOWN = RuleTerm.unresolved("synthetic: stacking");

	private static Order order(long won) {
		return new Order(Optional.empty(), Optional.empty(), Won.of(won), DAY, Optional.empty(), CAFE,
				PaymentMethod.DIRECT);
	}

	// 서비스 생성 도구

	private static BenefitService service(String id, MerchantTarget target, List<ServiceTier> tiers,
			List<LimitBucketId> buckets, boolean requiresSelection, RuleTerm<Set<ServiceId>> stackableWith) {
		return new BenefitService(ServiceId.of(id), BenefitKind.BILLING_DISCOUNT, target, Set.of(),
				Set.of(PaymentMethod.DIRECT), Won.ZERO, Optional.empty(), tiers, buckets, requiresSelection,
				stackableWith, RuleTerm.resolved(FractionalWonPolicy.TRUNCATE));
	}

	/** 전 가맹점 정률, 실적 조건 없음. 10,000원 주문이면 확정 {@code bp ÷ 100}원. */
	private static BenefitService rate(String id, int basisPoints) {
		return rate(id, basisPoints, NOT_STACKABLE);
	}

	private static BenefitService rate(String id, int basisPoints, RuleTerm<Set<ServiceId>> stackableWith) {
		return service(id, MerchantTarget.allMerchants(), List.of(tier(0, BenefitCalculation.rate(basisPoints))),
				List.of(), false, stackableWith);
	}

	private static BenefitService stacking(String id, int basisPoints, String... stackableWith) {
		return rate(id, basisPoints, RuleTerm.resolved(Stream.of(stackableWith).map(ServiceId::of).collect(Collectors.toSet())));
	}

	private static BenefitService fixed(String id, long won) {
		return fixed(id, won, NOT_STACKABLE);
	}

	private static BenefitService fixed(String id, long won, RuleTerm<Set<ServiceId>> stackableWith) {
		return service(id, MerchantTarget.allMerchants(), List.of(tier(0, BenefitCalculation.fixed(won))), List.of(),
				false, stackableWith);
	}

	/** 최소 실적 {@code minimumPerformance} 이상에서만 정률. 실적 조건이 있는 서비스다. */
	private static BenefitService performanceRate(String id, long minimumPerformance, int basisPoints) {
		return service(id, MerchantTarget.allMerchants(),
				List.of(tier(minimumPerformance, BenefitCalculation.rate(basisPoints))), List.of(), false, NOT_STACKABLE);
	}

	/** 지시서 예: 0원 구간 1%, 30만원 구간 5%. */
	private static BenefitService twoTier(String id) {
		return service(id, MerchantTarget.allMerchants(),
				List.of(tier(0, BenefitCalculation.rate(100)), tier(300_000, BenefitCalculation.rate(500))), List.of(),
				false, NOT_STACKABLE);
	}

	private static BenefitService cinemaOnly(String id, long minimumPerformance) {
		return service(id, MerchantTarget.only(Set.of(CINEMA)),
				List.of(tier(minimumPerformance, BenefitCalculation.rate(100))), List.of(), false, NOT_STACKABLE);
	}

	private static BenefitService unresolvedCalculation(String id) {
		return service(id, MerchantTarget.allMerchants(),
				List.of(new ServiceTier(Won.ZERO, RuleTerm.unresolved(CALCULATION_REASON))), List.of(), false,
				NOT_STACKABLE);
	}

	private static BenefitService selectable(String id) {
		return service(id, MerchantTarget.allMerchants(), List.of(tier(0, BenefitCalculation.rate(100))), List.of(),
				true, NOT_STACKABLE);
	}

	private static ServiceTier tier(long minimumPerformance, BenefitCalculation calculation) {
		return new ServiceTier(Won.of(minimumPerformance), RuleTerm.resolved(calculation));
	}

	// 규칙·선택 결과 생성 도구

	private static RulePolicy policy(RuleTerm<CancellationAttribution> cancellation, RuleTerm<Boolean> benefitedSales) {
		return new RulePolicy(RuleTerm.resolved(PerformanceWindow.PREVIOUS_CALENDAR_MONTH),
				RuleTerm.resolved(TransactionDateBasis.APPROVAL), cancellation, Set.of(TransactionNature.TAX), Set.of(),
				benefitedSales, RuleTerm.resolved(LimitPeriod.CALENDAR_MONTH),
				RuleTerm.resolved(LimitDeductionOrder.APPROVAL_ORDER), RuleTerm.resolved(Optional.empty()));
	}

	private static final RulePolicy RESOLVED = policy(RuleTerm.resolved(CancellationAttribution.RECEIPT_MONTH),
			RuleTerm.resolved(true));

	private static final RulePolicy CANCELLATION_PENDING = policy(RuleTerm.unresolved(CANCELLATION_REASON),
			RuleTerm.resolved(true));

	private static final RulePolicy SALES_PENDING = policy(RuleTerm.resolved(CancellationAttribution.RECEIPT_MONTH),
			RuleTerm.unresolved(SALES_REASON));

	private static final RulePolicy BOTH_PENDING = policy(RuleTerm.unresolved(CANCELLATION_REASON),
			RuleTerm.unresolved(SALES_REASON));

	private static CardRuleVersion rule(RulePolicy policy, List<BenefitService> services) {
		boolean usesBucket = services.stream().anyMatch(service -> !service.limitBuckets().isEmpty());
		List<LimitBucket> buckets = usesBucket
				? List.of(new LimitBucket(BUCKET, List.of(new LimitTier(Won.ZERO, Won.of(10_000))), Optional.empty()))
				: List.of();
		return new CardRuleVersion(CARD, "SYN-2026", ValidityPeriod.startingAt(LocalDate.of(2026, 1, 1)),
				VerificationStatus.VERIFIED, false,
				List.of(OfficialSource.of("https://example.invalid/terms.pdf", "p.1")), policy, services, buckets);
	}

	private static RuleSelection select(RulePolicy policy, BenefitService... services) {
		return new RuleCatalog(List.of(rule(policy, List.of(services)))).select(CARD, DAY);
	}

	private static RuleSelection select(BenefitService... services) {
		return select(RESOLVED, services);
	}

	// 상태·근거 생성 도구

	private static CardMonthlyState state(CardId card, Optional<StateValue<Won>> performance) {
		return new CardMonthlyState(card, performance, Map.of(), StateValue.unknown(null, null), List.of());
	}

	private static CardMonthlyState performance(long won) {
		return state(CARD, Optional.of(StateValue.synthetic(Won.of(won), BasisPeriod.month(YearMonth.of(2026, 9)))));
	}

	private static PolicyTermEvidence evidence(TriggerEvidence cancellation, TriggerEvidence benefitedSales) {
		return new PolicyTermEvidence(CARD, cancellation, benefitedSales);
	}

	private static final PolicyTermEvidence UNDETERMINED = evidence(TriggerEvidence.UNDETERMINED,
			TriggerEvidence.UNDETERMINED);

	private static final PolicyTermEvidence ABSENT = evidence(TriggerEvidence.CONFIRMED_ABSENT,
			TriggerEvidence.CONFIRMED_ABSENT);

	private static OrderBenefit.Decided decide(RuleSelection selection, Order order, CardMonthlyState state,
			PolicyTermEvidence evidence) {
		return (OrderBenefit.Decided) OrderBenefitDecider.decide(selection, order, state, PrimitiveTestData.REGISTERED, evidence);
	}

	private static OrderBenefit.Decided decide(RuleSelection selection) {
		return decide(selection, ORDER, performance(0), UNDETERMINED);
	}

	// 기대값 생성 도구

	private static ServiceCandidate candidate(String id, long won, ApplicationStatus... unmet) {
		return new ServiceCandidate(ServiceId.of(id), BenefitAmount.confirmed(won), Set.of(unmet));
	}

	/** 원 미만을 버려 만든 후보. */
	private static ServiceCandidate truncated(String id, long won) {
		return new ServiceCandidate(ServiceId.of(id), BenefitAmount.confirmed(won), Set.of(), Optional.empty(),
				Optional.empty(), true);
	}

	private static OrderServiceBenefit notAdoptedTruncated(String id) {
		return new OrderServiceBenefit(truncated(id, 0), Adoption.NOT_ADOPTED, BenefitAmount.confirmed(0));
	}

	private static OrderServiceBenefit adopted(String id, long won) {
		return new OrderServiceBenefit(candidate(id, won), Adoption.ADOPTED, BenefitAmount.confirmed(won));
	}

	private static OrderServiceBenefit notAdopted(String id, long won, ApplicationStatus... unmet) {
		return new OrderServiceBenefit(candidate(id, won, unmet), Adoption.NOT_ADOPTED, BenefitAmount.confirmed(0));
	}

	private static List<String> kindsAndServices(OrderBenefit.Decided result) {
		return result.reasons().stream().map(reason -> reason.kind() + " " + reason.services()).toList();
	}

	/** 미확정 결과의 공통 조건: 카드 금액 확인 필요, 채택 없음, 양수 후보는 모두 미정·기여액 확인 필요. */
	private static void assertPending(OrderBenefit.Decided result) {
		assertThat(result.orderBenefit().isConfirmed()).isFalse();
		assertThat(result.reasons()).isNotEmpty();
		assertThat(result.services()).noneMatch(service -> service.adoption() == Adoption.ADOPTED);
		assertThat(result.services()).filteredOn(service -> service.candidate().standaloneAmount().confirmedWon().isPositive())
			.allSatisfy(service -> {
				assertThat(service.adoption()).isEqualTo(Adoption.UNDETERMINED);
				assertThat(service.contribution().isConfirmed()).isFalse();
			});
	}

	private static List<Object> decisionView(OrderBenefit.Decided result) {
		return List.of(result.services(), result.rechecks(), result.reasons(), result.orderBenefit());
	}

	@Nested
	class UnavailableRule {

		@ParameterizedTest
		@EnumSource(value = ApplicationStatus.class, names = { "RULE_EXPIRED", "NO_VERIFIED_RULE" })
		void unavailableSelectionStaysUnavailable(ApplicationStatus status) {
			RuleSelection selection = new RuleSelection.Unavailable(CARD, status, "synthetic: no rule");

			OrderBenefit result = OrderBenefitDecider.decide(selection, ORDER, performance(0), PrimitiveTestData.REGISTERED, UNDETERMINED);

			assertThat(result).isEqualTo(new OrderBenefit.Unavailable(CARD, status, "synthetic: no rule"));
			assertThat(result.orderBenefit()).isEqualTo(BenefitAmount.needsConfirmation("synthetic: no rule"));
		}

		@Test
		void expiredAndUnverifiedRulesFromTheCatalog() {
			CardRuleVersion expired = new CardRuleVersion(CARD, "SYN-2025",
					ValidityPeriod.between(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)),
					VerificationStatus.VERIFIED, false,
					List.of(OfficialSource.of("https://example.invalid/terms.pdf", "p.1")), RESOLVED,
					List.of(rate("SYN-A", 100)), List.of());
			CardRuleVersion candidate = new CardRuleVersion(CARD, "SYN-CANDIDATE",
					ValidityPeriod.startingAt(LocalDate.of(2026, 1, 1)), VerificationStatus.CANDIDATE, false, List.of(),
					RESOLVED, List.of(rate("SYN-A", 100)), List.of());

			OrderBenefit expiredResult = OrderBenefitDecider.decide(new RuleCatalog(List.of(expired)).select(CARD, DAY),
					ORDER, performance(0), PrimitiveTestData.REGISTERED, UNDETERMINED);
			OrderBenefit unverifiedResult = OrderBenefitDecider.decide(
					new RuleCatalog(List.of(candidate)).select(CARD, DAY), ORDER, performance(0), PrimitiveTestData.REGISTERED, UNDETERMINED);

			assertThat(expiredResult).isInstanceOfSatisfying(OrderBenefit.Unavailable.class,
					unavailable -> assertThat(unavailable.status()).isEqualTo(ApplicationStatus.RULE_EXPIRED));
			assertThat(unverifiedResult).isInstanceOfSatisfying(OrderBenefit.Unavailable.class,
					unavailable -> assertThat(unavailable.status()).isEqualTo(ApplicationStatus.NO_VERIFIED_RULE));
			assertThat(expiredResult.orderBenefit().isConfirmed()).isFalse();
			assertThat(unverifiedResult.orderBenefit().isConfirmed()).isFalse();
		}

	}

	@Nested
	class Adoptions {

		@Test
		void singlePositiveCandidateIsAdopted() {
			OrderBenefit.Decided result = decide(select(rate("SYN-A", 100)));

			assertThat(result.services()).containsExactly(adopted("SYN-A", 100));
			assertThat(result.rechecks()).isEmpty();
			assertThat(result.reasons()).isEmpty();
			assertThat(result.orderBenefit()).isEqualTo(BenefitAmount.confirmed(100));
		}

		@Test
		void largestNonStackableCandidateIsAdopted() {
			OrderBenefit.Decided result = decide(select(rate("SYN-A", 100), rate("SYN-B", 300), fixed("SYN-C", 200)));

			assertThat(result.services()).containsExactly(notAdopted("SYN-A", 100), adopted("SYN-B", 300),
					notAdopted("SYN-C", 200));
			assertThat(result.orderBenefit()).isEqualTo(BenefitAmount.confirmed(300));
		}

		@Test
		void tieAdoptsTheFirstServiceIdRegardlessOfInputOrder() {
			// "SYN-A" < "SYN-B" < "syn-a": 대문자가 소문자보다 앞선다.
			BenefitService upperA = rate("SYN-A", 300);
			BenefitService upperB = fixed("SYN-B", 300);
			BenefitService lowerA = fixed("syn-a", 300);

			OrderBenefit.Decided forward = decide(select(upperA, upperB, lowerA));
			OrderBenefit.Decided backward = decide(select(lowerA, upperB, upperA));

			assertThat(forward.services()).containsExactly(adopted("SYN-A", 300), notAdopted("SYN-B", 300),
					notAdopted("syn-a", 300));
			assertThat(forward.orderBenefit()).isEqualTo(BenefitAmount.confirmed(300));
			assertThat(decisionView(backward)).isEqualTo(decisionView(forward));
		}

		@Test
		void confirmedAmountIsTheSumOfContributions() {
			OrderBenefit.Decided result = decide(select(rate("SYN-A", 100), rate("SYN-B", 300), fixed("SYN-C", 200)));

			long sum = result.services().stream().mapToLong(service -> service.contribution().confirmedWon().value()).sum();
			assertThat(result.orderBenefit()).isEqualTo(BenefitAmount.confirmed(sum));
		}

	}

	@Nested
	class ZeroCandidates {

		@Test
		void independentUnmetAndReasonlessZeroAreNotAdopted() {
			// 실적 정책이 미확정·근거 불명이어도 실적 조건이 없는 서비스의 0원은 영향받지 않는다(U4 통과 후 U6).
			OrderBenefit.Decided result = decide(select(CANCELLATION_PENDING, cinemaOnly("SYN-CINEMA", 0),
					rate("SYN-ZERO", 100)), SMALL_ORDER, performance(0), UNDETERMINED);

			assertThat(result.services()).containsExactly(
					notAdopted("SYN-CINEMA", 0, ApplicationStatus.NOT_APPLICABLE_CATEGORY), notAdoptedTruncated("SYN-ZERO"));
			assertThat(result.services().get(1).candidate().unmetConditions()).isEmpty();
			assertThat(result.rechecks()).isEmpty();
			assertThat(result.reasons()).isEmpty();
			assertThat(result.orderBenefit()).isEqualTo(BenefitAmount.confirmed(0));
		}

		@Test
		void onlyReasonlessZeroGivesConfirmedZero() {
			OrderBenefit.Decided result = decide(select(rate("SYN-ZERO", 100)), SMALL_ORDER, performance(0),
					UNDETERMINED);

			assertThat(result.services()).containsExactly(notAdoptedTruncated("SYN-ZERO"));
			assertThat(result.orderBenefit()).isEqualTo(BenefitAmount.confirmed(0));
		}

		@Test
		void zeroIsNotCountedAsASecondPositiveCandidate() {
			// 중복 관계가 미확정이어도 양수 후보가 하나뿐이면 중복 판단이 필요 없다.
			OrderBenefit.Decided result = decide(
					select(fixed("SYN-FIXED", 10, STACKING_UNKNOWN), rate("SYN-ZERO", 100, STACKING_UNKNOWN)),
					SMALL_ORDER, performance(0), UNDETERMINED);

			assertThat(result.services()).containsExactly(adopted("SYN-FIXED", 10), notAdoptedTruncated("SYN-ZERO"));
			assertThat(result.reasons()).isEmpty();
			assertThat(result.orderBenefit()).isEqualTo(BenefitAmount.confirmed(10));
		}

	}

	@Nested
	class BlockingServices {

		@Test
		void needingConfirmationAndUnsupportedServicesBlockTheAmount() {
			OrderBenefit.Decided result = decide(select(rate("SYN-A", 100), unresolvedCalculation("SYN-P"),
					fixed("SYN-F", 20_000), selectable("SYN-L"), selectable("SYN-S")));

			assertPending(result);
			assertThat(result.services()).containsExactly(new OrderServiceBenefit(candidate("SYN-A", 100),
					Adoption.UNDETERMINED, result.orderBenefit()));
			assertThat(kindsAndServices(result)).containsExactly("NEEDS_CONFIRMATION [SYN-F]",
					"NEEDS_CONFIRMATION [SYN-P]", "UNSUPPORTED [SYN-L]", "UNSUPPORTED [SYN-S]");
			// 앞 단계 사유 원문을 그대로 포함한다.
			assertThat(result.reasons().get(1).detail()).contains("RULE_SELECTION").contains(CALCULATION_REASON);
			assertThat(result.reasons().get(0).detail()).contains("CANDIDATE_EVALUATION").contains("exceeds order amount");
			assertThat(result.reasons().get(2).detail()).contains("REQUIRES_SELECTION");
			assertThat(result.reasons().get(3).detail()).contains("REQUIRES_SELECTION");
		}

		@Test
		void missingPriorPerformanceIsNotZero() {
			OrderBenefit.Decided result = decide(select(rate("SYN-A", 100)), ORDER, state(CARD, Optional.empty()),
					UNDETERMINED);

			assertPending(result);
			assertThat(result.services()).isEmpty();
			assertThat(kindsAndServices(result)).containsExactly("NEEDS_CONFIRMATION [SYN-A]");
			assertThat(result.reasons().get(0).detail()).contains("PRIOR_PERFORMANCE");
		}

		@Test
		void unsupportedOnlyStillNeedsConfirmation() {
			OrderBenefit.Decided result = decide(select(selectable("SYN-L")));

			assertPending(result);
			assertThat(result.services()).isEmpty();
			assertThat(kindsAndServices(result)).containsExactly("UNSUPPORTED [SYN-L]");
		}

	}

	@Nested
	class Stacking {

		@Test
		void unresolvedStackingBetweenPositivesBlocks() {
			OrderBenefit.Decided result = decide(select(rate("SYN-A", 100, STACKING_UNKNOWN), rate("SYN-B", 200)));

			assertPending(result);
			assertThat(kindsAndServices(result)).containsExactly("STACKING_UNRESOLVED [SYN-A, SYN-B]");
		}

		@Test
		void stackingListedOnOneSideOnlyIsUnresolved() {
			OrderBenefit.Decided result = decide(select(stacking("SYN-A", 100, "SYN-B"), rate("SYN-B", 200)));

			assertPending(result);
			assertThat(kindsAndServices(result)).containsExactly("STACKING_UNRESOLVED [SYN-A, SYN-B]");
			assertThat(result.reasons().get(0).detail()).contains("one side");
		}

		@Test
		void confirmedStackingIsNotSummedOrAdopted() {
			OrderBenefit.Decided result = decide(select(stacking("SYN-A", 100, "SYN-B"), stacking("SYN-B", 200, "SYN-A")));

			assertPending(result);
			assertThat(kindsAndServices(result)).containsExactly("STACKING_CONFIRMED [SYN-A, SYN-B]");
			assertThat(result.orderBenefit()).isNotEqualTo(BenefitAmount.confirmed(300));
		}

		@Test
		void eachPairIsJudgedSeparately() {
			// A-B는 서로 중복 불가 확정, B-C는 서로 중복 허용 확정, A-C는 중복 불가 확정.
			OrderBenefit.Decided result = decide(
					select(rate("SYN-A", 100), stacking("SYN-B", 200, "SYN-C"), stacking("SYN-C", 300, "SYN-B")));

			assertPending(result);
			assertThat(kindsAndServices(result)).containsExactly("STACKING_CONFIRMED [SYN-B, SYN-C]");
			assertThat(result.services()).extracting(OrderServiceBenefit::adoption)
				.containsOnly(Adoption.UNDETERMINED);
		}

	}

	@Nested
	class PerformancePolicy {

		/** 최소 실적 30만원, 실적 10만원: 유예 부재가 확정이므로 실적 미충족만 있는 확정 0원 후보가 된다. */
		private final BenefitService belowTier = performanceRate("SYN-PERF", 300_000, 100);

		@ParameterizedTest
		@EnumSource(value = TriggerEvidence.class, names = { "PRESENT", "UNDETERMINED" })
		void performanceOnlyUnmetIsRecheckedWhenCancellationMayAffectIt(TriggerEvidence cancellation) {
			RuleSelection selection = select(CANCELLATION_PENDING, belowTier);
			CardServiceCandidates.Collected source = (CardServiceCandidates.Collected) CardServiceCandidateCollector
				.collect(selection, ORDER, performance(100_000), PrimitiveTestData.REGISTERED);

			OrderBenefit.Decided result = decide(selection, ORDER, performance(100_000),
					evidence(cancellation, TriggerEvidence.CONFIRMED_ABSENT));

			assertThat(result.rechecks()).hasSize(1);
			PerformanceRecheck recheck = result.rechecks().get(0);
			// 원본 후보의 0원·실적 미충족 사유를 그대로 보존한다.
			assertThat(recheck.candidate()).isEqualTo(source.candidates().get(0))
				.isEqualTo(candidate("SYN-PERF", 0, ApplicationStatus.NOT_APPLICABLE_PERFORMANCE));
			assertThat(recheck.reasons()).singleElement().asString().contains(CANCELLATION_REASON).contains(cancellation.name());
			// 확정 0원 기여나 확정 카드 금액으로 숨기지 않는다.
			assertThat(result.services()).isEmpty();
			assertThat(result.orderBenefit().isConfirmed()).isFalse();
			assertThat(kindsAndServices(result)).containsExactly("PERFORMANCE_POLICY [SYN-PERF]");
		}

		@ParameterizedTest
		@EnumSource(value = TriggerEvidence.class, names = { "PRESENT", "UNDETERMINED" })
		void benefitedSalesTermAlsoTriggersRecheck(TriggerEvidence benefitedSales) {
			OrderBenefit.Decided result = decide(select(SALES_PENDING, belowTier), ORDER, performance(100_000),
					evidence(TriggerEvidence.CONFIRMED_ABSENT, benefitedSales));

			assertThat(result.rechecks()).extracting(recheck -> recheck.candidate().serviceId())
				.containsExactly(ServiceId.of("SYN-PERF"));
			assertThat(result.rechecks().get(0).reasons()).singleElement().asString().contains(SALES_REASON);
			assertThat(result.orderBenefit().isConfirmed()).isFalse();
		}

		@Test
		void confirmedAbsenceOfBothTriggersSettlesPerformanceUnmet() {
			OrderBenefit.Decided result = decide(select(BOTH_PENDING, belowTier), ORDER, performance(100_000), ABSENT);

			assertThat(result.services())
				.containsExactly(notAdopted("SYN-PERF", 0, ApplicationStatus.NOT_APPLICABLE_PERFORMANCE));
			assertThat(result.rechecks()).isEmpty();
			assertThat(result.orderBenefit()).isEqualTo(BenefitAmount.confirmed(0));
		}

		@Test
		void absenceOfOnlyOneTriggerIsNotEnough() {
			OrderBenefit.Decided result = decide(select(BOTH_PENDING, belowTier), ORDER, performance(100_000),
					evidence(TriggerEvidence.CONFIRMED_ABSENT, TriggerEvidence.UNDETERMINED));

			assertThat(result.rechecks()).hasSize(1);
			assertThat(result.rechecks().get(0).reasons()).singleElement().asString().contains(SALES_REASON);
		}

		@Test
		void resolvedPolicyNeedsNoEvidence() {
			OrderBenefit.Decided result = decide(select(RESOLVED, belowTier), ORDER, performance(100_000), UNDETERMINED);

			assertThat(result.services())
				.containsExactly(notAdopted("SYN-PERF", 0, ApplicationStatus.NOT_APPLICABLE_PERFORMANCE));
			assertThat(result.rechecks()).isEmpty();
			assertThat(result.reasons()).isEmpty();
		}

		@Test
		void independentUnmetWinsOverPerformanceUnmet() {
			OrderBenefit.Decided result = decide(select(BOTH_PENDING, cinemaOnly("SYN-CINEMA", 300_000)), ORDER,
					performance(100_000), UNDETERMINED);

			assertThat(result.services()).containsExactly(notAdopted("SYN-CINEMA", 0,
					ApplicationStatus.NOT_APPLICABLE_CATEGORY, ApplicationStatus.NOT_APPLICABLE_PERFORMANCE));
			assertThat(result.rechecks()).isEmpty();
			assertThat(result.reasons()).isEmpty();
			assertThat(result.orderBenefit()).isEqualTo(BenefitAmount.confirmed(0));
		}

		@Test
		void reasonlessZeroThatMayRiseIsRechecked() {
			// 실적 10만원 → 1% 구간: 30원 × 1% = 0.3원 → 절사 0원. 취소 귀속으로 실적이 30만원이면 5%: 1.5원 → 1원이 될 수 있다.
			BenefitService two = twoTier("SYN-TWO");
			assertThat(CardServiceCandidateCollector.collect(select(RESOLVED, two), SMALL_ORDER, performance(300_000), PrimitiveTestData.REGISTERED))
				.isInstanceOfSatisfying(CardServiceCandidates.Collected.class,
						higher -> assertThat(higher.candidates()).containsExactly(truncated("SYN-TWO", 1)));

			OrderBenefit.Decided result = decide(select(CANCELLATION_PENDING, two), SMALL_ORDER, performance(100_000),
					UNDETERMINED);

			assertThat(result.rechecks()).extracting(PerformanceRecheck::candidate)
				.containsExactly(truncated("SYN-TWO", 0));
			assertThat(result.services()).isEmpty();
			assertThat(result.orderBenefit().isConfirmed()).isFalse();
			assertThat(kindsAndServices(result)).containsExactly("PERFORMANCE_POLICY [SYN-TWO]");
		}

		@Test
		void sameZeroWithoutPerformanceRequirementIsSettled() {
			OrderBenefit.Decided result = decide(select(CANCELLATION_PENDING, rate("SYN-ZERO", 100)), SMALL_ORDER,
					performance(100_000), UNDETERMINED);

			assertThat(result.services()).containsExactly(notAdoptedTruncated("SYN-ZERO"));
			assertThat(result.rechecks()).isEmpty();
			assertThat(result.orderBenefit()).isEqualTo(BenefitAmount.confirmed(0));
		}

		@Test
		void positiveCandidateDependingOnPerformanceIsUndetermined() {
			OrderBenefit.Decided result = decide(select(CANCELLATION_PENDING, performanceRate("SYN-PERF", 100_000, 100)),
					ORDER, performance(100_000), UNDETERMINED);

			assertPending(result);
			assertThat(result.services()).extracting(OrderServiceBenefit::candidate)
				.containsExactly(candidate("SYN-PERF", 100));
			assertThat(kindsAndServices(result)).containsExactly("PERFORMANCE_POLICY [SYN-PERF]");
		}

		@Test
		void positiveCandidateIsAdoptedWhenPolicyCannotAffectIt() {
			// 실적 조건 없음
			OrderBenefit.Decided noRequirement = decide(select(BOTH_PENDING, rate("SYN-A", 100)), ORDER,
					performance(100_000), UNDETERMINED);
			// 실적 조건 있음, 두 근거 모두 부재 확인
			OrderBenefit.Decided absent = decide(select(BOTH_PENDING, performanceRate("SYN-PERF", 100_000, 100)), ORDER,
					performance(100_000), ABSENT);

			assertThat(noRequirement.services()).containsExactly(adopted("SYN-A", 100));
			assertThat(noRequirement.orderBenefit()).isEqualTo(BenefitAmount.confirmed(100));
			assertThat(absent.services()).containsExactly(adopted("SYN-PERF", 100));
			assertThat(absent.orderBenefit()).isEqualTo(BenefitAmount.confirmed(100));
		}

		@Test
		void recheckBlocksOtherPositiveCandidates() {
			OrderBenefit.Decided result = decide(select(CANCELLATION_PENDING, rate("SYN-A", 100), belowTier), ORDER,
					performance(100_000), UNDETERMINED);

			assertPending(result);
			assertThat(result.services()).containsExactly(
					new OrderServiceBenefit(candidate("SYN-A", 100), Adoption.UNDETERMINED, result.orderBenefit()));
			assertThat(result.rechecks()).extracting(recheck -> recheck.candidate().serviceId())
				.containsExactly(ServiceId.of("SYN-PERF"));
		}

	}

	@Nested
	class SeveralReasons {

		private final List<BenefitService> services = List.of(performanceRate("SYN-A", 100_000, 200),
				rate("SYN-B", 100, STACKING_UNKNOWN), unresolvedCalculation("SYN-P"), selectable("SYN-L"));

		@Test
		void allReasonsAreKeptInDeterministicOrder() {
			OrderBenefit.Decided result = decide(select(CANCELLATION_PENDING, services.toArray(BenefitService[]::new)),
					ORDER, performance(100_000), UNDETERMINED);

			assertPending(result);
			assertThat(kindsAndServices(result)).containsExactly("NEEDS_CONFIRMATION [SYN-P]", "UNSUPPORTED [SYN-L]",
					"PERFORMANCE_POLICY [SYN-A]", "STACKING_UNRESOLVED [SYN-A, SYN-B]");
			assertThat(result.orderBenefit()).isInstanceOfSatisfying(BenefitAmount.NeedsConfirmation.class,
					pending -> assertThat(pending.reason()).isNotBlank()
						.contains("NEEDS_CONFIRMATION")
						.contains("STACKING_UNRESOLVED"));
		}

		@Test
		void inputOrderDoesNotChangeTheDecision() {
			List<BenefitService> reversed = new ArrayList<>(services);
			Collections.reverse(reversed);

			OrderBenefit.Decided forward = decide(select(CANCELLATION_PENDING, services.toArray(BenefitService[]::new)),
					ORDER, performance(100_000), UNDETERMINED);
			OrderBenefit.Decided backward = decide(select(CANCELLATION_PENDING, reversed.toArray(BenefitService[]::new)),
					ORDER, performance(100_000), UNDETERMINED);

			assertThat(decisionView(backward)).isEqualTo(decisionView(forward));
		}

	}

	@Nested
	class Preservation {

		@Test
		void sourceAndConditionalTermsAreKeptUnchanged() {
			RuleSelection.Selected selection = (RuleSelection.Selected) select(BOTH_PENDING,
					rate("SYN-A", 100, STACKING_UNKNOWN), performanceRate("SYN-PERF", 300_000, 100), selectable("SYN-L"));
			CardServiceCandidates.Collected expected = (CardServiceCandidates.Collected) CardServiceCandidateCollector
				.collect(selection, ORDER, performance(100_000), PrimitiveTestData.REGISTERED);

			OrderBenefit.Decided result = decide(selection, ORDER, performance(100_000), UNDETERMINED);

			assertThat(result.source()).isEqualTo(expected);
			assertThat(result.cardId()).isEqualTo(CARD);
			assertThat(result.source().unresolvedConditionalTerms()).isEqualTo(selection.unresolvedConditionalTerms())
				.hasSize(3);
			List<ServiceCandidate> decided = new ArrayList<>();
			result.services().forEach(service -> decided.add(service.candidate()));
			result.rechecks().forEach(recheck -> decided.add(recheck.candidate()));
			assertThat(decided).containsExactlyInAnyOrderElementsOf(expected.candidates());
		}

		@Test
		void resultListsAreImmutable() {
			OrderBenefit.Decided result = decide(select(rate("SYN-A", 100)));

			assertThatThrownBy(() -> result.services().clear()).isInstanceOf(UnsupportedOperationException.class);
			assertThatThrownBy(() -> result.reasons().clear()).isInstanceOf(UnsupportedOperationException.class);
			assertThatThrownBy(() -> result.rechecks().clear()).isInstanceOf(UnsupportedOperationException.class);
		}

	}

	@Nested
	class Inputs {

		private final RuleSelection selection = select(rate("SYN-A", 100));

		@Test
		void rejectsNullInputs() {
			assertThatThrownBy(() -> OrderBenefitDecider.decide(null, ORDER, performance(0), PrimitiveTestData.REGISTERED, UNDETERMINED))
				.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> OrderBenefitDecider.decide(selection, null, performance(0), PrimitiveTestData.REGISTERED, UNDETERMINED))
				.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> OrderBenefitDecider.decide(selection, ORDER, null, PrimitiveTestData.REGISTERED, UNDETERMINED))
				.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> OrderBenefitDecider.decide(selection, ORDER, performance(0), PrimitiveTestData.REGISTERED, null))
				.isInstanceOf(NullPointerException.class);
		}

		@Test
		void rejectsEvidenceOfAnotherCard() {
			PolicyTermEvidence other = new PolicyTermEvidence(CardId.of("SYN-OTHER"), TriggerEvidence.CONFIRMED_ABSENT,
					TriggerEvidence.CONFIRMED_ABSENT);
			RuleSelection unavailable = new RuleSelection.Unavailable(CARD, ApplicationStatus.NO_VERIFIED_RULE,
					"synthetic: no rule");

			assertThatThrownBy(() -> OrderBenefitDecider.decide(selection, ORDER, performance(0), PrimitiveTestData.REGISTERED, other))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("policy evidence card");
			assertThatThrownBy(() -> OrderBenefitDecider.decide(unavailable, ORDER, performance(0), PrimitiveTestData.REGISTERED, other))
				.hasMessageContaining("policy evidence card");
		}

		@Test
		void keepsCollectorInputChecks() {
			CardMonthlyState otherCard = state(CardId.of("SYN-OTHER"),
					Optional.of(StateValue.synthetic(Won.ZERO, BasisPeriod.month(YearMonth.of(2026, 9)))));

			assertThatThrownBy(() -> OrderBenefitDecider.decide(selection, ORDER, otherCard, PrimitiveTestData.REGISTERED, UNDETERMINED))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("monthly state card");
			assertThatThrownBy(() -> OrderBenefitDecider.decide(selection, ORDER, performance(-1), PrimitiveTestData.REGISTERED, UNDETERMINED))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("negative prior performance");
		}

	}

}
