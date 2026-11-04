package com.beone.api.benefit.primitive;

import static com.beone.api.benefit.primitive.PrimitiveTestData.CAFE;
import static com.beone.api.benefit.primitive.PrimitiveTestData.CINEMA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.card.LimitBucketId;
import com.beone.api.benefit.card.PaymentMethod;
import com.beone.api.benefit.card.ServiceId;
import com.beone.api.benefit.card.TransactionNature;
import com.beone.api.benefit.input.CardMonthlyState;
import com.beone.api.benefit.input.Order;
import com.beone.api.benefit.money.BenefitAmount;
import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.result.ApplicationStatus;
import com.beone.api.benefit.result.CardServiceCandidates;
import com.beone.api.benefit.result.ConfirmationStage;
import com.beone.api.benefit.result.ServiceCandidate;
import com.beone.api.benefit.result.ServiceNeedingConfirmation;
import com.beone.api.benefit.result.UnsupportedReason;
import com.beone.api.benefit.result.UnsupportedService;
import com.beone.api.benefit.rule.BenefitCalculation;
import com.beone.api.benefit.rule.BenefitKind;
import com.beone.api.benefit.rule.BenefitService;
import com.beone.api.benefit.rule.CancellationAttribution;
import com.beone.api.benefit.rule.CardRuleVersion;
import com.beone.api.benefit.rule.FractionalWonPolicy;
import com.beone.api.benefit.rule.GracePartialPerformance;
import com.beone.api.benefit.rule.GraceSpan;
import com.beone.api.benefit.rule.LimitBucket;
import com.beone.api.benefit.rule.LimitDeductionOrder;
import com.beone.api.benefit.rule.LimitPeriod;
import com.beone.api.benefit.rule.LimitTier;
import com.beone.api.benefit.rule.MerchantTarget;
import com.beone.api.benefit.rule.NewCardGrace;
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
 * 가상 카드·서비스·업종의 합성 규칙으로 카드 한 장의 서비스 후보 취합을 확인한다. 실제 카드 약관의 계산 결과를 주장하지 않는다.
 */
class CardServiceCandidateCollectorTest {

	private static final CardId CARD = CardId.of("SYN-CARD");

	private static final LocalDate DAY = LocalDate.of(2026, 10, 15);

	private static final LimitBucketId BUCKET = LimitBucketId.of("SYN-BUCKET");

	private static final String CALCULATION_REASON = "synthetic: calculation not confirmed";

	private static final String LIMIT_PERIOD_REASON = "synthetic: limit period not confirmed";

	private static final Order ORDER = new Order(Optional.empty(), Optional.empty(), Won.of(10_000), DAY,
			Optional.empty(), CAFE, PaymentMethod.DIRECT);

	// 서비스 생성 도구

	private static BenefitService service(String id, MerchantTarget target, List<ServiceTier> tiers,
			List<LimitBucketId> buckets, boolean requiresSelection) {
		return new BenefitService(ServiceId.of(id), BenefitKind.BILLING_DISCOUNT, target, Set.of(),
				Set.of(PaymentMethod.DIRECT), Won.ZERO, Optional.empty(), tiers, buckets, requiresSelection,
				RuleTerm.resolved(Set.of()), RuleTerm.resolved(FractionalWonPolicy.TRUNCATE));
	}

	/** 전 가맹점 정률, 실적 조건 없음. 10,000원 주문이면 확정 {@code 10,000 × bp ÷ 10,000}원. */
	private static BenefitService rate(String id, int basisPoints) {
		return service(id, MerchantTarget.allMerchants(), List.of(tier(0, BenefitCalculation.rate(basisPoints))),
				List.of(), false);
	}

	private static BenefitService fixed(String id, long won) {
		return service(id, MerchantTarget.allMerchants(), List.of(tier(0, BenefitCalculation.fixed(won))), List.of(),
				false);
	}

	/** 대상 업종이 아니어서 확정 0원이 되는 서비스. */
	private static BenefitService cinemaOnly(String id) {
		return service(id, MerchantTarget.only(Set.of(CINEMA)), List.of(tier(0, BenefitCalculation.rate(100))),
				List.of(), false);
	}

	/** 0원 구간 1%, 30만원 구간 2%. 실적 0원이면 최상위가 아닌 구간이라 유예 근거에 따라 결과가 갈린다. */
	private static BenefitService twoTier(String id) {
		return service(id, MerchantTarget.allMerchants(),
				List.of(tier(0, BenefitCalculation.rate(100)), tier(300_000, BenefitCalculation.rate(200))), List.of(),
				false);
	}

	private static BenefitService unresolvedCalculation(String id) {
		return service(id, MerchantTarget.allMerchants(),
				List.of(new ServiceTier(Won.ZERO, RuleTerm.unresolved(CALCULATION_REASON))), List.of(), false);
	}

	private static BenefitService limited(String id) {
		return service(id, MerchantTarget.allMerchants(), List.of(tier(0, BenefitCalculation.rate(100))),
				List.of(BUCKET), false);
	}

	private static BenefitService selectable(String id) {
		return service(id, MerchantTarget.allMerchants(), List.of(tier(0, BenefitCalculation.rate(100))), List.of(),
				true);
	}

	private static ServiceTier tier(long minimumPerformance, BenefitCalculation calculation) {
		return new ServiceTier(Won.of(minimumPerformance), RuleTerm.resolved(calculation));
	}

	// 규칙·선택 결과 생성 도구

	private static RulePolicy policy(RuleTerm<LimitPeriod> limitPeriod, RuleTerm<Optional<NewCardGrace>> grace) {
		return new RulePolicy(RuleTerm.resolved(PerformanceWindow.PREVIOUS_CALENDAR_MONTH),
				RuleTerm.resolved(TransactionDateBasis.APPROVAL), RuleTerm.resolved(CancellationAttribution.RECEIPT_MONTH),
				Set.of(TransactionNature.TAX), Set.of(), RuleTerm.resolved(true), limitPeriod,
				RuleTerm.resolved(LimitDeductionOrder.APPROVAL_ORDER), grace);
	}

	/** 한도 기간이 미확정이라 한도 서비스가 지원 범위 밖으로 남는 정책(D-18 K1). */
	private static RulePolicy limitPeriodUnresolved() {
		return policy(RuleTerm.unresolved(LIMIT_PERIOD_REASON), RuleTerm.resolved(Optional.empty()));
	}

	private static RulePolicy resolvedPolicy() {
		return policy(RuleTerm.resolved(LimitPeriod.CALENDAR_MONTH), RuleTerm.resolved(Optional.empty()));
	}

	private static CardRuleVersion rule(RulePolicy policy, List<BenefitService> services) {
		boolean usesBucket = services.stream().anyMatch(service -> !service.limitBuckets().isEmpty());
		List<LimitBucket> buckets = usesBucket
				? List.of(new LimitBucket(BUCKET, List.of(new LimitTier(Won.ZERO, Won.of(10_000))), Optional.empty()))
				: List.of();
		return new CardRuleVersion(CARD, "SYN-2026", ValidityPeriod.startingAt(LocalDate.of(2026, 1, 1)),
				VerificationStatus.VERIFIED, false,
				List.of(OfficialSource.of("https://example.invalid/terms.pdf", "p.1")), policy, services, buckets);
	}

	private static RuleSelection.Selected select(CardRuleVersion rule) {
		return (RuleSelection.Selected) new RuleCatalog(List.of(rule)).select(CARD, DAY);
	}

	private static RuleSelection.Selected select(BenefitService... services) {
		return select(rule(resolvedPolicy(), List.of(services)));
	}

	// 상태 생성 도구

	private static CardMonthlyState state(CardId card, Optional<StateValue<Won>> performance) {
		return new CardMonthlyState(card, performance, Map.of(), StateValue.unknown(null, null), List.of());
	}

	private static CardMonthlyState performance(long won) {
		return state(CARD, Optional.of(StateValue.synthetic(Won.of(won), BasisPeriod.month(YearMonth.of(2026, 9)))));
	}

	private static final CardMonthlyState NOT_PROVIDED = state(CARD, Optional.empty());

	private static final CardMonthlyState UNKNOWN = state(CARD, Optional.of(StateValue.unknown(null, null)));

	private static CardServiceCandidates.Collected collect(RuleSelection selection, CardMonthlyState state) {
		return (CardServiceCandidates.Collected) CardServiceCandidateCollector.collect(selection, ORDER, state, PrimitiveTestData.REGISTERED);
	}

	private static CardServiceCandidates.Collected collect(RuleSelection selection) {
		return collect(selection, performance(0));
	}

	private static ServiceCandidate evaluate(BenefitService service, long performance, TriggerEvidence grace) {
		return ServiceCandidateEvaluator.evaluate(service, ORDER, Won.of(performance), grace, ServiceLimits.NONE);
	}

	private static String evaluationReason(BenefitService service, long performance, TriggerEvidence grace) {
		return ((BenefitAmount.NeedsConfirmation) evaluate(service, performance, grace).standaloneAmount()).reason();
	}

	/** 세 분류의 서비스 ID를 모아, 규칙의 서비스 ID와 중복·누락 없이 같은지 확인한다. */
	private static void assertClassifiesExactlyOnce(CardServiceCandidates.Collected result, CardRuleVersion rule) {
		List<ServiceId> classified = new ArrayList<>();
		result.candidates().forEach(candidate -> classified.add(candidate.serviceId()));
		result.needingConfirmation().forEach(pending -> classified.add(pending.serviceId()));
		result.unsupported().forEach(service -> classified.add(service.serviceId()));
		assertThat(classified).doesNotHaveDuplicates()
			.containsExactlyInAnyOrderElementsOf(rule.services().stream().map(BenefitService::id).toList());
	}

	private static Map<ServiceId, Object> byService(CardServiceCandidates.Collected result) {
		Map<ServiceId, Object> classified = new HashMap<>();
		result.candidates().forEach(candidate -> classified.put(candidate.serviceId(), candidate));
		result.needingConfirmation().forEach(pending -> classified.put(pending.serviceId(), pending));
		result.unsupported().forEach(service -> classified.put(service.serviceId(), service));
		return classified;
	}

	@Nested
	class Candidates {

		@Test
		void keepsConfirmedCandidatesOfSeveralServicesAndConfirmedZero() {
			BenefitService base = rate("SYN-BASE", 100);
			BenefitService cinema = cinemaOnly("SYN-CINEMA");
			BenefitService fixed = fixed("SYN-FIXED", 500);
			RuleSelection.Selected selection = select(base, cinema, fixed);

			CardServiceCandidates.Collected result = collect(selection);

			assertThat(result.cardId()).isEqualTo(CARD);
			assertThat(result.rule()).isEqualTo(selection.rule().ref());
			assertThat(result.candidates()).containsExactly(
					new ServiceCandidate(ServiceId.of("SYN-BASE"), BenefitAmount.confirmed(100), Set.of()),
					new ServiceCandidate(ServiceId.of("SYN-CINEMA"), BenefitAmount.confirmed(0),
							Set.of(ApplicationStatus.NOT_APPLICABLE_CATEGORY)),
					new ServiceCandidate(ServiceId.of("SYN-FIXED"), BenefitAmount.confirmed(500), Set.of()));
			assertThat(result.needingConfirmation()).isEmpty();
			assertThat(result.unsupported()).isEmpty();
		}

		@Test
		void mixesCandidatesConfirmationsAndUnsupportedServicesOncePerService() {
			BenefitService base = rate("SYN-BASE", 100);
			// 정액 20,000원 > 결제 10,000원: 판정기가 확인 필요를 돌려준다.
			BenefitService tooLarge = fixed("SYN-FIXED", 20_000);
			BenefitService pendingRule = unresolvedCalculation("SYN-PENDING");
			BenefitService limited = limited("SYN-LIMITED");
			CardRuleVersion rule = rule(limitPeriodUnresolved(), List.of(base, tooLarge, pendingRule, limited));

			CardServiceCandidates.Collected result = collect(select(rule));

			assertThat(result.candidates()).extracting(ServiceCandidate::serviceId)
				.containsExactly(ServiceId.of("SYN-BASE"));
			assertThat(result.needingConfirmation()).containsExactly(
					new ServiceNeedingConfirmation(ServiceId.of("SYN-FIXED"), ConfirmationStage.CANDIDATE_EVALUATION,
							List.of(evaluationReason(tooLarge, 0, TriggerEvidence.CONFIRMED_ABSENT))),
					new ServiceNeedingConfirmation(ServiceId.of("SYN-PENDING"), ConfirmationStage.RULE_SELECTION,
							List.of(CALCULATION_REASON)));
			assertThat(result.unsupported()).containsExactly(new UnsupportedService(ServiceId.of("SYN-LIMITED"),
					Set.of(UnsupportedReason.HAS_LIMIT_BUCKETS), List.of(LIMIT_PERIOD_REASON)));
			assertClassifiesExactlyOnce(result, rule);
		}

		@Test
		void ruleWithoutServicesGivesEmptyClassification() {
			// VERIFIED 규칙은 서비스가 필요하므로 서비스 없는 선택 결과는 직접 만든다.
			CardRuleVersion empty = new CardRuleVersion(CARD, "SYN-EMPTY",
					ValidityPeriod.startingAt(LocalDate.of(2026, 1, 1)), VerificationStatus.CANDIDATE, false, List.of(),
					resolvedPolicy(), List.of(), List.of());

			CardServiceCandidates.Collected result = collect(
					new RuleSelection.Selected(empty, List.of(), Map.of(), List.of()));

			assertThat(result.rule()).isEqualTo(empty.ref());
			assertThat(result.candidates()).isEmpty();
			assertThat(result.needingConfirmation()).isEmpty();
			assertThat(result.unsupported()).isEmpty();
		}

		@Test
		void unresolvedConditionalTermsArePassedThroughWithoutChangingCandidates() {
			RulePolicy pending = new RulePolicy(RuleTerm.resolved(PerformanceWindow.PREVIOUS_CALENDAR_MONTH),
					RuleTerm.resolved(TransactionDateBasis.APPROVAL), RuleTerm.unresolved("synthetic: cancellation"),
					Set.of(), Set.of(), RuleTerm.unresolved("synthetic: benefited sales"),
					RuleTerm.resolved(LimitPeriod.CALENDAR_MONTH), RuleTerm.unresolved("synthetic: deduction order"),
					RuleTerm.resolved(Optional.empty()));
			BenefitService base = new BenefitService(ServiceId.of("SYN-BASE"), BenefitKind.BILLING_DISCOUNT,
					MerchantTarget.allMerchants(), Set.of(), Set.of(PaymentMethod.DIRECT), Won.ZERO, Optional.empty(),
					List.of(tier(0, BenefitCalculation.rate(100))), List.of(), false,
					RuleTerm.unresolved("synthetic: stacking"), RuleTerm.unresolved("synthetic: fractional won"));
			RuleSelection.Selected selection = select(rule(pending, List.of(base)));
			assertThat(selection.unresolvedConditionalTerms()).hasSize(5);

			CardServiceCandidates.Collected result = collect(selection);

			assertThat(result.candidates())
				.containsExactly(evaluate(base, 0, TriggerEvidence.CONFIRMED_ABSENT))
				.allSatisfy(candidate -> assertThat(candidate.standaloneAmount()).isEqualTo(BenefitAmount.confirmed(100)));
			assertThat(result.unresolvedConditionalTerms()).isEqualTo(selection.unresolvedConditionalTerms());
		}

	}

	@Nested
	class Unsupported {

		@Test
		void allServicesOutOfScopeAreUnsupportedWithoutCallingTheEvaluator() {
			BenefitService both = service("SYN-BOTH", MerchantTarget.allMerchants(),
					List.of(tier(0, BenefitCalculation.rate(100))), List.of(BUCKET), true);
			CardRuleVersion rule = rule(limitPeriodUnresolved(), List.of(limited("SYN-LIMITED"), selectable("SYN-SELECT"), both));

			CardServiceCandidates.Collected result = collect(select(rule));

			assertThat(result.candidates()).isEmpty();
			assertThat(result.needingConfirmation()).isEmpty();
			assertThat(result.unsupported()).containsExactly(
					new UnsupportedService(ServiceId.of("SYN-LIMITED"), Set.of(UnsupportedReason.HAS_LIMIT_BUCKETS),
							List.of(LIMIT_PERIOD_REASON)),
					new UnsupportedService(ServiceId.of("SYN-SELECT"), Set.of(UnsupportedReason.REQUIRES_SELECTION),
							List.of()),
					new UnsupportedService(ServiceId.of("SYN-BOTH"),
							Set.of(UnsupportedReason.HAS_LIMIT_BUCKETS, UnsupportedReason.REQUIRES_SELECTION),
							List.of(LIMIT_PERIOD_REASON)));
		}

		@Test
		void unsupportedWinsOverSelectionStageButKeepsItsReasons() {
			// 한도 기간 미확정: 규칙 선택이 한도 서비스를 확인 필요로 분리한다.
			CardRuleVersion rule = rule(
					policy(RuleTerm.unresolved(LIMIT_PERIOD_REASON), RuleTerm.resolved(Optional.empty())),
					List.of(limited("SYN-LIMITED"), rate("SYN-BASE", 100)));
			RuleSelection.Selected selection = select(rule);
			assertThat(selection.servicesNeedingConfirmation())
				.containsEntry(ServiceId.of("SYN-LIMITED"), List.of(LIMIT_PERIOD_REASON));

			CardServiceCandidates.Collected result = collect(selection);

			assertThat(result.unsupported()).containsExactly(new UnsupportedService(ServiceId.of("SYN-LIMITED"),
					Set.of(UnsupportedReason.HAS_LIMIT_BUCKETS), List.of(LIMIT_PERIOD_REASON)));
			assertThat(result.needingConfirmation()).isEmpty();
			assertClassifiesExactlyOnce(result, rule);
		}

		@Test
		void keepsValidSelectionReasonsVerbatimAndInOrder() {
			BenefitService base = rate("SYN-BASE", 100);
			CardRuleVersion rule = rule(limitPeriodUnresolved(),
					List.of(base, limited("SYN-LIMITED"), selectable("SYN-SELECT")));
			List<String> limitedReasons = List.of("synthetic: b", "synthetic: a", "synthetic: c");
			List<String> selectReasons = List.of("synthetic: only");

			CardServiceCandidates.Collected result = collect(new RuleSelection.Selected(rule, List.of(base),
					Map.of(ServiceId.of("SYN-LIMITED"), limitedReasons, ServiceId.of("SYN-SELECT"), selectReasons),
					List.of()));

			assertThat(result.unsupported()).containsExactly(
					new UnsupportedService(ServiceId.of("SYN-LIMITED"), Set.of(UnsupportedReason.HAS_LIMIT_BUCKETS),
							limitedReasons),
					new UnsupportedService(ServiceId.of("SYN-SELECT"), Set.of(UnsupportedReason.REQUIRES_SELECTION),
							selectReasons));
			assertClassifiesExactlyOnce(result, rule);
		}

		@Test
		void keepsPaddedSelectionReasonsWithoutTrimming() {
			BenefitService pendingRule = rate("SYN-PENDING", 100);
			CardRuleVersion rule = rule(limitPeriodUnresolved(), List.of(pendingRule, limited("SYN-LIMITED")));
			String padded = "  synthetic: padded  ";

			CardServiceCandidates.Collected result = collect(new RuleSelection.Selected(rule, List.of(),
					Map.of(ServiceId.of("SYN-PENDING"), List.of(padded), ServiceId.of("SYN-LIMITED"), List.of(padded)),
					List.of()));

			assertThat(result.unsupported()).extracting(UnsupportedService::selectionReasons)
				.containsExactly(List.of(padded));
			assertThat(result.needingConfirmation()).containsExactly(new ServiceNeedingConfirmation(
					ServiceId.of("SYN-PENDING"), ConfirmationStage.RULE_SELECTION, List.of(padded)));
		}

	}

	@Nested
	class Confirmation {

		@Test
		void separatesSelectionStageFromEvaluationStage() {
			BenefitService pendingRule = unresolvedCalculation("SYN-PENDING");
			BenefitService tooLarge = fixed("SYN-FIXED", 20_000);

			CardServiceCandidates.Collected result = collect(select(pendingRule, tooLarge));

			assertThat(result.candidates()).isEmpty();
			assertThat(result.needingConfirmation()).containsExactly(
					new ServiceNeedingConfirmation(ServiceId.of("SYN-PENDING"), ConfirmationStage.RULE_SELECTION,
							List.of(CALCULATION_REASON)),
					new ServiceNeedingConfirmation(ServiceId.of("SYN-FIXED"), ConfirmationStage.CANDIDATE_EVALUATION,
							List.of(evaluationReason(tooLarge, 0, TriggerEvidence.CONFIRMED_ABSENT))));
		}

		@Test
		void performanceNotProvidedKeepsUnsupportedAndSelectionStage() {
			assertMissingPerformance(NOT_PROVIDED, "prior performance is not provided and has not been derived");
		}

		@Test
		void performanceUnknownKeepsUnsupportedAndSelectionStage() {
			assertMissingPerformance(UNKNOWN, "prior performance is UNKNOWN");
		}

		private void assertMissingPerformance(CardMonthlyState state, String reason) {
			BenefitService base = rate("SYN-BASE", 100);
			BenefitService cinema = cinemaOnly("SYN-CINEMA");
			CardRuleVersion rule = rule(limitPeriodUnresolved(), List.of(base, cinema, unresolvedCalculation("SYN-PENDING"),
					limited("SYN-LIMITED"), selectable("SYN-SELECT")));

			CardServiceCandidates.Collected result = collect(select(rule), state);

			// 실적 조건이 없는 서비스나 업종 미충족 서비스도 실적 값을 만들어 넣지 않고 확인 필요로 둔다.
			assertThat(result.candidates()).isEmpty();
			assertThat(result.needingConfirmation()).containsExactly(
					new ServiceNeedingConfirmation(ServiceId.of("SYN-BASE"), ConfirmationStage.PRIOR_PERFORMANCE,
							List.of(reason)),
					new ServiceNeedingConfirmation(ServiceId.of("SYN-CINEMA"), ConfirmationStage.PRIOR_PERFORMANCE,
							List.of(reason)),
					new ServiceNeedingConfirmation(ServiceId.of("SYN-PENDING"), ConfirmationStage.RULE_SELECTION,
							List.of(CALCULATION_REASON)));
			assertThat(result.unsupported()).extracting(UnsupportedService::serviceId)
				.containsExactly(ServiceId.of("SYN-LIMITED"), ServiceId.of("SYN-SELECT"));
			assertClassifiesExactlyOnce(result, rule);
		}

	}

	@Nested
	class NewCardGraceEvidence {

		private static final NewCardGrace GRACE_FOR_OTHER = new NewCardGrace(GraceSpan.UNTIL_END_OF_NEXT_MONTH,
				Optional.empty(), Won.of(300_000), Set.of(ServiceId.of("SYN-OTHER")), GracePartialPerformance.DEEMED_TIER);

		private static final NewCardGrace GRACE_FOR_TIERED = new NewCardGrace(GraceSpan.UNTIL_END_OF_NEXT_MONTH,
				Optional.empty(), Won.of(300_000), Set.of(ServiceId.of("SYN-TIERED")), GracePartialPerformance.DEEMED_TIER);

		private CardServiceCandidates.Collected collectTiered(RuleTerm<Optional<NewCardGrace>> grace) {
			CardRuleVersion rule = rule(policy(RuleTerm.resolved(LimitPeriod.CALENDAR_MONTH), grace),
					List.of(twoTier("SYN-TIERED"), rate("SYN-OTHER", 100)));
			return collect(select(rule), performance(0));
		}

		private void assertConfirmedAbsent(CardServiceCandidates.Collected result) {
			assertThat(result.candidates())
				.contains(evaluate(twoTier("SYN-TIERED"), 0, TriggerEvidence.CONFIRMED_ABSENT));
			assertThat(result.candidates()).extracting(ServiceCandidate::serviceId).contains(ServiceId.of("SYN-TIERED"));
		}

		private void assertUndetermined(CardServiceCandidates.Collected result) {
			assertThat(result.needingConfirmation()).containsExactly(new ServiceNeedingConfirmation(
					ServiceId.of("SYN-TIERED"), ConfirmationStage.CANDIDATE_EVALUATION,
					List.of(evaluationReason(twoTier("SYN-TIERED"), 0, TriggerEvidence.UNDETERMINED))));
		}

		@Test
		void noGraceDefinitionIsConfirmedAbsent() {
			assertConfirmedAbsent(collectTiered(RuleTerm.resolved(Optional.empty())));
		}

		@Test
		void serviceOutsideConfirmedGraceIsConfirmedAbsent() {
			assertConfirmedAbsent(collectTiered(RuleTerm.resolved(Optional.of(GRACE_FOR_OTHER))));
		}

		@Test
		void serviceInsideConfirmedGraceWithUnknownRegistrationNeedsConfirmation() {
			CardRuleVersion rule = rule(
					policy(RuleTerm.resolved(LimitPeriod.CALENDAR_MONTH), RuleTerm.resolved(Optional.of(GRACE_FOR_TIERED))),
					List.of(twoTier("SYN-TIERED"), rate("SYN-OTHER", 100)));

			CardServiceCandidates.Collected result = (CardServiceCandidates.Collected) CardServiceCandidateCollector
				.collect(select(rule), ORDER, performance(0), StateValue.unknown(null, null));

			assertThat(result.needingConfirmation()).containsExactly(new ServiceNeedingConfirmation(
					ServiceId.of("SYN-TIERED"), ConfirmationStage.CANDIDATE_EVALUATION,
					List.of("service SYN-TIERED has new card grace but the registration date is UNKNOWN")));
			// 유예 대상이 아닌 서비스는 같은 규칙에서도 부재 확인으로 판정한다.
			assertThat(result.candidates())
				.containsExactly(evaluate(rate("SYN-OTHER", 100), 0, TriggerEvidence.CONFIRMED_ABSENT));
		}

		@Test
		void unresolvedGraceDefinitionIsUndetermined() {
			CardServiceCandidates.Collected result = collectTiered(RuleTerm.unresolved("synthetic: grace not confirmed"));

			assertUndetermined(result);
			assertThat(result.unresolvedConditionalTerms()).extracting(term -> term.name()).contains("newCardGrace");
		}

	}

	@Nested
	class UnavailableRule {

		@Test
		void noVerifiedRuleStaysUnavailable() {
			RuleSelection selection = new RuleCatalog(List.of()).select(CARD, DAY);

			assertThat(CardServiceCandidateCollector.collect(selection, ORDER, NOT_PROVIDED, PrimitiveTestData.REGISTERED)).isEqualTo(
					new CardServiceCandidates.Unavailable(CARD, ApplicationStatus.NO_VERIFIED_RULE,
							((RuleSelection.Unavailable) selection).reason()));
		}

		@Test
		void expiredRuleStaysUnavailable() {
			CardRuleVersion old = new CardRuleVersion(CARD, "SYN-2025",
					ValidityPeriod.between(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)),
					VerificationStatus.VERIFIED, false,
					List.of(OfficialSource.of("https://example.invalid/terms.pdf", "p.1")), resolvedPolicy(),
					List.of(rate("SYN-BASE", 100)), List.of());
			RuleSelection selection = new RuleCatalog(List.of(old)).select(CARD, DAY);

			CardServiceCandidates result = CardServiceCandidateCollector.collect(selection, ORDER, performance(0), PrimitiveTestData.REGISTERED);

			assertThat(result).isEqualTo(new CardServiceCandidates.Unavailable(CARD, ApplicationStatus.RULE_EXPIRED,
					((RuleSelection.Unavailable) selection).reason()));
		}

	}

	@Nested
	class InvalidInput {

		@Test
		void rejectsNull() {
			RuleSelection.Selected selection = select(rate("SYN-BASE", 100));

			assertThatThrownBy(() -> CardServiceCandidateCollector.collect(null, ORDER, performance(0), PrimitiveTestData.REGISTERED))
				.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> CardServiceCandidateCollector.collect(selection, null, performance(0), PrimitiveTestData.REGISTERED))
				.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> CardServiceCandidateCollector.collect(selection, ORDER, null, PrimitiveTestData.REGISTERED))
				.isInstanceOf(NullPointerException.class);
		}

		@Test
		void rejectsStateOfAnotherCard() {
			CardMonthlyState other = state(CardId.of("SYN-OTHER-CARD"), Optional.empty());

			assertThatThrownBy(() -> CardServiceCandidateCollector.collect(select(rate("SYN-BASE", 100)), ORDER, other, PrimitiveTestData.REGISTERED))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("does not match");
			RuleSelection unavailable = new RuleCatalog(List.of()).select(CARD, DAY);
			assertThatThrownBy(() -> CardServiceCandidateCollector.collect(unavailable, ORDER, other, PrimitiveTestData.REGISTERED))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("does not match");
		}

		@Test
		void rejectsNegativePerformanceOnEveryPath() {
			CardMonthlyState negative = performance(-1);

			assertThatThrownBy(() -> CardServiceCandidateCollector.collect(select(rate("SYN-BASE", 100)), ORDER, negative, PrimitiveTestData.REGISTERED))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("negative prior performance");
			// 판정기를 호출하지 않는 범위 밖·규칙 사용 불가 경로에서도 거부한다.
			assertThatThrownBy(() -> CardServiceCandidateCollector.collect(select(limited("SYN-LIMITED")), ORDER, negative, PrimitiveTestData.REGISTERED))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("negative prior performance");
			RuleSelection unavailable = new RuleCatalog(List.of()).select(CARD, DAY);
			assertThatThrownBy(() -> CardServiceCandidateCollector.collect(unavailable, ORDER, negative, PrimitiveTestData.REGISTERED))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("negative prior performance");
		}

		@Test
		void rejectsSelectionThatDoesNotSplitTheRuleServices() {
			BenefitService base = rate("SYN-BASE", 100);
			BenefitService other = rate("SYN-OTHER", 200);
			CardRuleVersion rule = rule(resolvedPolicy(), List.of(base, other));

			// 누락
			assertInconsistent(new RuleSelection.Selected(rule, List.of(base), Map.of(), List.of()));
			// 계산 가능과 확인 필요에 동시에 있음
			assertInconsistent(new RuleSelection.Selected(rule, List.of(base, other),
					Map.of(ServiceId.of("SYN-BASE"), List.of("synthetic")), List.of()));
			// 규칙에 없는 서비스
			assertInconsistent(new RuleSelection.Selected(rule, List.of(base, other, rate("SYN-EXTRA", 100)), Map.of(),
					List.of()));
			assertInconsistent(new RuleSelection.Selected(rule, List.of(base, other),
					Map.of(ServiceId.of("SYN-EXTRA"), List.of("synthetic")), List.of()));
			// 같은 ID지만 규칙과 다른 서비스
			assertInconsistent(new RuleSelection.Selected(rule, List.of(base, rate("SYN-OTHER", 300)), Map.of(),
					List.of()));
			// 같은 서비스를 두 번
			assertInconsistent(new RuleSelection.Selected(rule, List.of(base, base, other), Map.of(), List.of()));
			// 사유 없는 확인 필요(범위 밖 서비스여도 거부)
			assertInconsistent(new RuleSelection.Selected(rule, List.of(base),
					Map.of(ServiceId.of("SYN-OTHER"), List.of()), List.of()));
			CardRuleVersion withLimited = rule(resolvedPolicy(), List.of(base, limited("SYN-LIMITED")));
			assertInconsistent(new RuleSelection.Selected(withLimited, List.of(base),
					Map.of(ServiceId.of("SYN-LIMITED"), List.of()), List.of()));
		}

		@ParameterizedTest
		@ValueSource(strings = { "", " ", "\t", "\n" })
		void rejectsBlankSelectionReasonOfLimitedService(String blank) {
			assertBlankReasonRejected(limited("SYN-LIMITED"), List.of(blank));
		}

		@ParameterizedTest
		@ValueSource(strings = { "", " ", "\t", "\n" })
		void rejectsBlankSelectionReasonOfSelectableService(String blank) {
			assertBlankReasonRejected(selectable("SYN-SELECT"), List.of(blank));
		}

		@ParameterizedTest
		@ValueSource(strings = { "", " ", "\t", "\n" })
		void rejectsBlankSelectionReasonOfSupportedService(String blank) {
			assertBlankReasonRejected(rate("SYN-PENDING", 200), List.of(blank));
		}

		@Test
		void rejectsBlankReasonAmongValidReasons() {
			List<String> mixed = List.of("synthetic: a", " ", "synthetic: b");

			assertBlankReasonRejected(limited("SYN-LIMITED"), mixed);
			assertBlankReasonRejected(selectable("SYN-SELECT"), mixed);
			assertBlankReasonRejected(rate("SYN-PENDING", 200), mixed);
		}

		@Test
		void nullSelectionReasonStillThrowsNullPointerException() {
			// 선택 결과 생성은 사유 원소를 검사하지 않으므로 null 원소가 그대로 들어온다.
			List<String> withNull = Arrays.asList("synthetic: a", null);

			assertThatThrownBy(() -> collect(blockedSelection(limited("SYN-LIMITED"), withNull)))
				.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> collect(blockedSelection(rate("SYN-PENDING", 200), withNull)))
				.isInstanceOf(NullPointerException.class);
		}

		private RuleSelection.Selected blockedSelection(BenefitService blocked, List<String> reasons) {
			BenefitService base = rate("SYN-BASE", 100);
			CardRuleVersion rule = rule(resolvedPolicy(), List.of(base, blocked));
			Map<ServiceId, List<String>> reasonsByService = new HashMap<>();
			reasonsByService.put(blocked.id(), reasons);
			return new RuleSelection.Selected(rule, List.of(base), reasonsByService, List.of());
		}

		private void assertBlankReasonRejected(BenefitService blocked, List<String> reasons) {
			RuleSelection.Selected selection = blockedSelection(blocked, reasons);

			assertThatThrownBy(() -> CardServiceCandidateCollector.collect(selection, ORDER, performance(0), PrimitiveTestData.REGISTERED))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("blank confirmation reason");
		}

		private void assertInconsistent(RuleSelection.Selected selection) {
			assertThatThrownBy(() -> CardServiceCandidateCollector.collect(selection, ORDER, performance(0), PrimitiveTestData.REGISTERED))
				.isInstanceOf(IllegalArgumentException.class);
		}

	}

	@Nested
	class Stability {

		@Test
		void reversingServiceOrderKeepsTheMeaningOfEachService() {
			List<BenefitService> services = List.of(rate("SYN-BASE", 100), cinemaOnly("SYN-CINEMA"),
					fixed("SYN-FIXED", 20_000), unresolvedCalculation("SYN-PENDING"), limited("SYN-LIMITED"),
					selectable("SYN-SELECT"));
			List<BenefitService> reversed = new ArrayList<>(services);
			Collections.reverse(reversed);

			CardServiceCandidates.Collected forward = collect(select(rule(resolvedPolicy(), services)));
			CardServiceCandidates.Collected backward = collect(select(rule(resolvedPolicy(), reversed)));

			assertThat(byService(backward)).isEqualTo(byService(forward)).hasSize(services.size());
		}

		@Test
		void returnedCollectionsCannotBeChanged() {
			CardRuleVersion rule = rule(limitPeriodUnresolved(),
					List.of(rate("SYN-BASE", 100), unresolvedCalculation("SYN-PENDING"), limited("SYN-LIMITED")));
			CardServiceCandidates.Collected result = collect(select(rule));

			assertThatThrownBy(() -> result.candidates().clear()).isInstanceOf(UnsupportedOperationException.class);
			assertThatThrownBy(() -> result.needingConfirmation().clear())
				.isInstanceOf(UnsupportedOperationException.class);
			assertThatThrownBy(() -> result.unsupported().clear()).isInstanceOf(UnsupportedOperationException.class);
			assertThatThrownBy(() -> result.unresolvedConditionalTerms().clear())
				.isInstanceOf(UnsupportedOperationException.class);
			assertThatThrownBy(() -> result.needingConfirmation().get(0).reasons().clear())
				.isInstanceOf(UnsupportedOperationException.class);
			assertThatThrownBy(() -> result.unsupported().get(0).reasons().clear())
				.isInstanceOf(UnsupportedOperationException.class);
		}

		@Test
		void laterChangesToSelectionReasonsDoNotLeakIntoTheResult() {
			BenefitService base = rate("SYN-BASE", 100);
			BenefitService limitedService = limited("SYN-LIMITED");
			CardRuleVersion rule = rule(limitPeriodUnresolved(), List.of(base, limitedService));
			List<String> reasons = new ArrayList<>(List.of("synthetic: mutable"));
			Map<ServiceId, List<String>> blocked = new HashMap<>();
			blocked.put(ServiceId.of("SYN-LIMITED"), reasons);
			RuleSelection.Selected selection = new RuleSelection.Selected(rule, List.of(base), blocked, List.of());

			CardServiceCandidates.Collected result = collect(selection);
			reasons.add("synthetic: added later");

			assertThat(result.unsupported().get(0).selectionReasons()).containsExactly("synthetic: mutable");
			assertClassifiesExactlyOnce(result, rule);
		}

	}

}
