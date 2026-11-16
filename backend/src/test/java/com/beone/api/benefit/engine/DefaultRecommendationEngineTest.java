package com.beone.api.benefit.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.rule.RuleCatalog;
import com.beone.api.benefit.rule.RuleSelection;
import com.beone.api.benefit.rule.loading.RuleCatalogLoader;

/**
 * 실제 규칙 JSON으로 입구 {@link RecommendationEngine}을 실행해 D-22 연결을 손계산 값과 비교한다.
 *
 * <p>
 * 날짜·금액은 합성 값이다. fixture에 기대값이 없는 남은 한도·차감·선별·조건부·대표 카드 비교를 여기서 고정한다.
 */
class DefaultRecommendationEngineTest {

	private static final LocalDate DATE = LocalDate.of(2026, 10, 15);

	private static final LocalDate REGISTERED = LocalDate.of(2026, 1, 1);

	private static final OffsetDateTime UPDATED_AT = OffsetDateTime.parse("2026-10-15T09:00:00+09:00");

	private static final PolicyEvidenceInput NO_POLICY_EVENTS = new PolicyEvidenceInput("CONFIRMED_ABSENT",
			"CONFIRMED_ABSENT");

	private static RuleCatalog catalog;

	private static RecommendationEngine engine;

	@BeforeAll
	static void load() {
		catalog = RuleCatalogLoader.load();
		engine = new DefaultRecommendationEngine(catalog);
	}

	@Nested
	class RemainingLimit {

		@Test
		void c10GreatContributionLeavesZeroInItsBucket() {
			// 산식 20,000×50%=10,000, GREAT 한도 10,000 − 사용 9,000 = 남은 1,000 → 기여 1,000, 주문 뒤 0원
			EngineResult result = recommend(order("STARBUCKS_STORE", 20_000, "DIRECT"),
					card("09174", state(DATE, 300_000L, with09174Usage("GREAT", 9_000L))));

			ComparedCard card = onlyCompared(result);
			assertThat(card.applicationStatus()).isEqualTo("APPLIED_CAP_REMAINING");
			assertThat(card.currentOrderBenefit()).isEqualTo(BenefitAmountOutput.confirmed(1_000));
			ServiceResult great = service(card, "GREAT");
			assertThat(great.candidateWon()).isEqualTo(10_000L);
			assertThat(great.limits()).containsExactly(new ServiceLimitLine("GREAT", null, 10_000L, 9_000L, "SYNTHETIC", 0L));
			// 적용되지 않은 버킷도 현재 남은 한도를 보여 준다.
			assertThat(service(card, "CHECK").applicationStatus()).isEqualTo("NOT_APPLICABLE_PAYMENT_METHOD");
			assertThat(service(card, "CHECK").limits())
				.containsExactly(new ServiceLimitLine("CHECK", null, 5_000L, 0L, "SYNTHETIC", 5_000L));
			assertThat(result.usageEffects()).containsExactly(new BucketUsageEffect("uc-09174", "GREAT", 1_000L));
		}

		@Test
		void m04CheckChainIsReducedByItsOwnContribution() {
			// GREAT 1,000(남은 한도) + CHECK 20,000×10%=2,000. 각 기여액은 자기 체인에서만 뺀다.
			EngineResult result = recommend(order("STARBUCKS_STORE", 20_000, "ELIGIBLE_SIMPLE_PAY"),
					card("09174", state(DATE, 300_000L, with09174Usage("GREAT", 9_000L))));

			ComparedCard card = onlyCompared(result);
			assertThat(card.currentOrderBenefit()).isEqualTo(BenefitAmountOutput.confirmed(3_000));
			assertThat(service(card, "GREAT").limits())
				.containsExactly(new ServiceLimitLine("GREAT", null, 10_000L, 9_000L, "SYNTHETIC", 0L));
			assertThat(service(card, "CHECK").applicationStatus()).isEqualTo("APPLIED");
			assertThat(service(card, "CHECK").limits())
				.containsExactly(new ServiceLimitLine("CHECK", null, 5_000L, 0L, "SYNTHETIC", 3_000L));
			assertThat(result.usageEffects()).containsExactly(new BucketUsageEffect("uc-09174", "GREAT", 1_000L),
					new BucketUsageEffect("uc-09174", "CHECK", 2_000L));
		}

		@Test
		void unknownUsageMakesTheChainAndDeductionUnknown() {
			// GREAT 사용액을 넣지 않으면 0원으로 보지 않고 확인 필요다. 차감액도 null로 넘긴다.
			Map<String, Long> usages = new LinkedHashMap<>(allBuckets("09174", 0L));
			usages.remove("GREAT");
			EngineResult result = recommend(order("STARBUCKS_STORE", 20_000, "DIRECT"),
					card("09174", state(DATE, 300_000L, usages)));

			assertThat(result.outcome()).isEqualTo("NOT_DETERMINED");
			ExcludedCard card = result.excludedCards().get(0);
			assertThat(card.reason()).isEqualTo("NEEDS_CONFIRMATION");
			assertThat(card.applicationStatus()).isEqualTo("NEEDS_CONFIRMATION");
			assertThat(card.appliedRule()).isNotNull();
			assertThat(card.pendingReasons()).extracting(PendingReasonOutput::kind).containsExactly("NEEDS_CONFIRMATION");
			assertThat(result.usageEffects()).containsExactly(new BucketUsageEffect("uc-09174", "GREAT", null));
		}

	}

	@Nested
	class PeriodAndState {

		@Test
		void priorPerformanceOutsideThePreviousMonthNeedsConfirmation() {
			MonthlyStateInput state = new MonthlyStateInput(StateInput.manual(300_000L, DATE.withDayOfMonth(1),
					YearMonth.from(DATE).atEndOfMonth(), UPDATED_AT), usages(DATE, allBuckets("09174", 0L)),
					StateInput.synthetic(0L, DATE.withDayOfMonth(1), YearMonth.from(DATE).atEndOfMonth()), "NONE",
					List.of(noSelection()));

			EngineResult result = recommend(order("STARBUCKS_STORE", 20_000, "DIRECT"), card("09174", state));

			assertThat(result.excludedCards()).extracting(ExcludedCard::reason).containsExactly("NEEDS_CONFIRMATION");
		}

		@Test
		void usageOutsideTheOrderMonthNeedsConfirmation() {
			YearMonth previous = YearMonth.from(DATE).minusMonths(1);
			List<BucketUsageInput> usages = new ArrayList<>(usages(DATE, allBuckets("09174", 0L)));
			usages.replaceAll(usage -> usage.limitBucketId().equals("GREAT")
					? new BucketUsageInput("GREAT", StateInput.synthetic(0L, previous.atDay(1), previous.atEndOfMonth()))
					: usage);
			MonthlyStateInput state = new MonthlyStateInput(prior(DATE, 300_000L), usages, other(DATE), "NONE",
					List.of(noSelection()));

			EngineResult result = recommend(order("STARBUCKS_STORE", 20_000, "DIRECT"), card("09174", state));

			assertThat(result.excludedCards()).extracting(ExcludedCard::reason).containsExactly("NEEDS_CONFIRMATION");
			assertThat(result.usageEffects()).containsExactly(new BucketUsageEffect("uc-09174", "GREAT", null));
		}

		@Test
		void negativePriorPerformanceNeedsConfirmationInsteadOfFailing() {
			EngineResult result = recommend(order("STARBUCKS_STORE", 20_000, "DIRECT"),
					card("09174", state(DATE, -1_000L, allBuckets("09174", 0L))));

			assertThat(result.excludedCards()).extracting(ExcludedCard::reason).containsExactly("NEEDS_CONFIRMATION");
		}

		@Test
		void unusableValueNeedsConfirmation() {
			YearMonth previous = YearMonth.from(DATE).minusMonths(1);
			StateInput<Long> conflicted = new StateInput<>(300_000L, "MANUAL", previous.atDay(1), previous.atEndOfMonth(),
					UPDATED_AT, false, "SOURCE_CONFLICT");
			MonthlyStateInput state = new MonthlyStateInput(conflicted, usages(DATE, allBuckets("09174", 0L)), other(DATE),
					"NONE", List.of(noSelection()));

			EngineResult result = recommend(order("STARBUCKS_STORE", 20_000, "DIRECT"), card("09174", state));

			assertThat(result.excludedCards()).extracting(ExcludedCard::reason).containsExactly("NEEDS_CONFIRMATION");
		}

		@Test
		void undeterminedPolicyEvidenceKeepsPerformanceDependentServicesPending() {
			// 09174는 취소 귀속월이 미확정이다. 근거가 없으면 실적에 의존하는 서비스를 확정하지 않는다.
			HeldCardInput card = new HeldCardInput("uc-09174", null, "09174", null, false, "SYNTHETIC", registered(),
					state(DATE, 300_000L, allBuckets("09174", 0L)), PolicyEvidenceInput.undetermined());

			EngineResult result = recommend(order("STARBUCKS_STORE", 20_000, "DIRECT"), card);

			ExcludedCard excluded = result.excludedCards().get(0);
			assertThat(excluded.reason()).isEqualTo("NEEDS_CONFIRMATION");
			assertThat(excluded.pendingReasons()).extracting(PendingReasonOutput::kind).contains("PERFORMANCE_POLICY");
		}

	}

	@Nested
	class Screening {

		@Test
		void userExclusionAloneIsNotConditional() {
			HeldCardInput excluded = new HeldCardInput("uc-09174", null, "09174", null, true, "SYNTHETIC", registered(),
					state(DATE, 300_000L, allBuckets("09174", 0L)), NO_POLICY_EVENTS);

			EngineResult result = recommend(order("ORDINARY_DOMESTIC", 100_000, "DIRECT"), base09271(), excluded);

			assertThat(result.conditional()).isFalse();
			assertThat(result.excludedCards()).singleElement().satisfies(card -> {
				assertThat(card.reason()).isEqualTo("USER_EXCLUDED");
				assertThat(card.applicationStatus()).isNull();
			});
			assertThat(result.noteCodes()).doesNotContain(DefaultRecommendationEngine.NOTE_CONDITIONAL);
		}

		@Test
		void otherExclusionsMakeTheRecommendationConditional() {
			HeldCardInput candidate = new HeldCardInput("uc-c", "CANDIDATE", null, "09174", false, "MANUAL", registered(),
					state(DATE, null, Map.of()), NO_POLICY_EVENTS);
			HeldCardInput notSupported = new HeldCardInput("uc-n", "NOT_SUPPORTED", null, null, false, "MANUAL",
					registered(), state(DATE, null, Map.of()), NO_POLICY_EVENTS);

			EngineResult result = recommend(order("ORDINARY_DOMESTIC", 100_000, "DIRECT"), base09271(), candidate,
					notSupported);

			assertThat(result.conditional()).isTrue();
			assertThat(result.excludedCards()).extracting(ExcludedCard::reason)
				.containsExactly("MAPPING_UNCONFIRMED", "NO_SUPPORTED_RULE");
			assertThat(result.excludedCards().get(0).candidateCardProductId()).isEqualTo("09174");
			assertThat(result.noteCodes()).contains(DefaultRecommendationEngine.NOTE_CONDITIONAL);
		}

		@Test
		void noComparedCardIsNotDetermined() {
			EngineResult result = engine.recommend(new EngineRequest(DATE, order("ORDINARY_DOMESTIC", 100_000, "DIRECT"),
					"AUTO_RECOMMEND", null, List.of()));

			assertThat(result.outcome()).isEqualTo("NOT_DETERMINED");
			assertThat(result.conditional()).isFalse();
			assertThat(result.selected()).isNull();
			assertThat(result.noteCodes()).containsExactly(DefaultRecommendationEngine.NOTE_PLANNED_SPENDING_EXCLUDED,
					DefaultRecommendationEngine.NOTE_NO_COMPARABLE_CARD);
		}

		@Test
		void singleComparedCardHasNoBasisOrGap() {
			EngineResult result = recommend(order("ORDINARY_DOMESTIC", 100_000, "DIRECT"), base09271());

			assertThat(result.selected().decidedBy()).isNull();
			assertThat(result.selected().gapToRunnerUpWon()).isNull();
			assertThat(result.noteCodes()).contains(DefaultRecommendationEngine.NOTE_SINGLE_COMPARED_CARD);
		}

		@Test
		void ruleUnavailableCardIsExcludedWithItsStatus() {
			// 09174 규칙은 2026-07-14부터 유효하다.
			LocalDate before = LocalDate.of(2026, 7, 1);
			EngineResult result = engine.recommend(new EngineRequest(before, order(before, "ORDINARY_DOMESTIC", 100_000,
					"DIRECT"), "AUTO_RECOMMEND", null, List.of(card("09174", state(before, 300_000L, Map.of())))));

			assertThat(result.excludedCards()).singleElement().satisfies(card -> {
				assertThat(card.reason()).isEqualTo("RULE_UNAVAILABLE");
				assertThat(card.cardProductId()).isEqualTo("09174");
				assertThat(card.appliedRule()).isNull();
			});
			assertThat(result.usageEffects()).isEmpty();
		}

	}

	@Nested
	class Selection {

		private static final LocalDate TIE_DATE = LocalDate.of(2026, 10, 1);

		@Test
		void equalBenefitsFallBackToCardIdAscending() {
			EngineResult result = engine.recommend(tieRequest("AUTO_RECOMMEND", null));

			SelectedCard selected = result.selected();
			assertThat(selected.cardProductId()).isEqualTo("01914");
			assertThat(selected.decidedBy()).isEqualTo("CARD_ID_ASC");
			assertThat(selected.arbitraryTieBreak()).isTrue();
			assertThat(selected.tiedUserCardIds()).containsExactly("uc-01914", "uc-09271");
			assertThat(selected.gapToRunnerUpWon()).isZero();
		}

		@Test
		void representativeCardBreaksTheTieBeforeCardId() {
			EngineResult result = engine.recommend(tieRequest("AUTO_RECOMMEND", "uc-09271"));

			assertThat(result.selected().cardProductId()).isEqualTo("09271");
			assertThat(result.selected().decidedBy()).isEqualTo("REPRESENTATIVE_CARD");
			assertThat(result.fixedComparison()).isNull();
		}

		@Test
		void largerMonthlyTotalWinsWithItsGap() {
			// 09174 GREAT 10,000, 09271 BASE 20,000×1%=200
			EngineResult result = recommend(order("STARBUCKS_STORE", 20_000, "DIRECT"), great09174(), base09271());

			assertThat(result.selected().cardProductId()).isEqualTo("09174");
			assertThat(result.selected().decidedBy()).isEqualTo("MONTHLY_TOTAL");
			assertThat(result.selected().gapToRunnerUpWon()).isEqualTo(9_800L);
		}

		@Test
		void fixedRepresentativeKeepsTheOptimalCardSelected() {
			EngineResult result = engine.recommend(new EngineRequest(DATE, order("STARBUCKS_STORE", 20_000, "DIRECT"),
					"FIXED_REPRESENTATIVE", "uc-09271", List.of(great09174(), base09271())));

			assertThat(result.selected().cardProductId()).isEqualTo("09174");
			assertThat(result.fixedComparison()).isEqualTo(new FixedComparison("uc-09271", "uc-09174", 9_800L));
		}

		@Test
		void unconfirmedRepresentativeHasNoDifference() {
			Map<String, Long> usages = new LinkedHashMap<>(allBuckets("09174", 0L));
			usages.remove("GREAT");
			HeldCardInput pending = card("09174", state(DATE, 300_000L, usages));

			EngineResult result = engine.recommend(new EngineRequest(DATE, order("STARBUCKS_STORE", 20_000, "DIRECT"),
					"FIXED_REPRESENTATIVE", "uc-09174", List.of(pending, base09271())));

			assertThat(result.selected().cardProductId()).isEqualTo("09271");
			assertThat(result.conditional()).isTrue();
			assertThat(result.fixedComparison()).isEqualTo(new FixedComparison("uc-09174", "uc-09271", null));
		}

		@Test
		void ruleUnavailableRepresentativeHasNoDifference() {
			LocalDate before = LocalDate.of(2026, 7, 1);
			HeldCardInput unavailable = card("09174", state(before, 300_000L, Map.of()));
			HeldCardInput base = card("09271", state(before, 0L, Map.of()));

			EngineResult result = engine.recommend(new EngineRequest(before,
					order(before, "ORDINARY_DOMESTIC", 100_000, "DIRECT"), "FIXED_REPRESENTATIVE", "uc-09174",
					List.of(unavailable, base)));

			assertThat(result.excludedCards()).extracting(ExcludedCard::reason).containsExactly("RULE_UNAVAILABLE");
			assertThat(result.fixedComparison()).isEqualTo(new FixedComparison("uc-09174", "uc-09271", null));
		}

		private EngineRequest tieRequest(String mode, String representative) {
			// 01914 CU 1,000(실적 300,000)과 09271 BASE 100,000×1%=1,000이 같다(C19와 같은 값).
			HeldCardInput first = card("01914", state(TIE_DATE, 300_000L, allBuckets("01914", 0L)));
			HeldCardInput second = card("09271", state(TIE_DATE, 0L, Map.of()));
			return new EngineRequest(TIE_DATE, order(TIE_DATE, "CU_STORE", 100_000, "DIRECT"), mode, representative,
					List.of(second, first));
		}

	}

	@Nested
	class ZeroContribution {

		@Test
		void truncatedZeroServiceIsNoContribution() {
			// 99×1%=0.99 → 원 미만 절사(D-03)로 0원. 미충족 사유가 없으므로 NO_CONTRIBUTION이다.
			EngineResult result = recommend(order("ORDINARY_DOMESTIC", 99, "DIRECT"), base09271());

			ComparedCard card = onlyCompared(result);
			ServiceResult base = service(card, "BASE");
			assertThat(base.applicationStatus()).isEqualTo("NO_CONTRIBUTION");
			assertThat(base.fractionalWonTruncated()).isTrue();
			assertThat(card.currentOrderBenefit()).isEqualTo(BenefitAmountOutput.confirmed(0));
			assertThat(card.noBenefitReason()).isEqualTo("CONFIRMED_NOT_MET");
			assertThat(card.policyNotes()).containsExactly(CardEvaluator.FRACTIONAL_WON_NOTE);
			// D-22 카드 상태 4의 ②가 ③보다 앞서므로, 대상 업종이 아닌 특정 업종 서비스의 사유가 카드 상태가 된다.
			assertThat(card.applicationStatus()).isEqualTo("NOT_APPLICABLE_CATEGORY");
		}

	}

	private static EngineResult recommend(OrderInput order, HeldCardInput... cards) {
		return engine.recommend(new EngineRequest(order.date(), order, "AUTO_RECOMMEND", null, List.of(cards)));
	}

	private static HeldCardInput great09174() {
		return card("09174", state(DATE, 300_000L, allBuckets("09174", 0L)));
	}

	private static HeldCardInput base09271() {
		return card("09271", state(DATE, 0L, Map.of()));
	}

	private static OrderInput order(String merchantClass, long amount, String paymentMethod) {
		return order(DATE, merchantClass, amount, paymentMethod);
	}

	private static OrderInput order(LocalDate date, String merchantClass, long amount, String paymentMethod) {
		return new OrderInput("ord-1", null, amount, date, null, merchantClass, paymentMethod);
	}

	private static HeldCardInput card(String product, MonthlyStateInput state) {
		return new HeldCardInput("uc-" + product, null, product, null, false, "SYNTHETIC", registered(), state,
				NO_POLICY_EVENTS);
	}

	private static StateInput<LocalDate> registered() {
		return StateInput.synthetic(REGISTERED, REGISTERED, REGISTERED);
	}

	/** 전월 실적과 버킷 사용액을 받아 월간 상태를 만든다. 실적이 null이면 입력하지 않은 것이다. */
	private static MonthlyStateInput state(LocalDate orderDate, Long prior, Map<String, Long> used) {
		StateInput<Long> performance = prior == null ? StateInput.notEntered() : prior(orderDate, prior);
		return new MonthlyStateInput(performance, usages(orderDate, used), other(orderDate), "NONE", List.of(noSelection()));
	}

	private static StateInput<Long> prior(LocalDate orderDate, long won) {
		YearMonth previous = YearMonth.from(orderDate).minusMonths(1);
		return StateInput.synthetic(won, previous.atDay(1), previous.atEndOfMonth());
	}

	private static StateInput<Long> other(LocalDate orderDate) {
		YearMonth month = YearMonth.from(orderDate);
		return StateInput.synthetic(0L, month.atDay(1), month.atEndOfMonth());
	}

	private static List<BucketUsageInput> usages(LocalDate orderDate, Map<String, Long> used) {
		YearMonth month = YearMonth.from(orderDate);
		return used.entrySet()
			.stream()
			.map(entry -> new BucketUsageInput(entry.getKey(),
					StateInput.synthetic(entry.getValue(), month.atDay(1), month.atEndOfMonth())))
			.toList();
	}

	/** 규칙의 모든 버킷을 같은 사용액으로 채운다. 주어진 값은 그 버킷만 바꾼다. */
	private static Map<String, Long> allBuckets(String product, long won) {
		Map<String, Long> used = new LinkedHashMap<>();
		RuleSelection.Selected selected = (RuleSelection.Selected) catalog.select(CardId.of(product), DATE);
		selected.rule().limitBuckets().forEach(bucket -> used.put(bucket.id().value(), won));
		return used;
	}

	/** 09174의 모든 버킷을 0원으로 채우고 한 버킷만 바꾼다. */
	private static Map<String, Long> with09174Usage(String bucket, long won) {
		Map<String, Long> used = allBuckets("09174", 0L);
		used.put(bucket, won);
		return used;
	}

	private static ServiceSelectionInput noSelection() {
		return new ServiceSelectionInput(null, REGISTERED, "SYNTHETIC", null, true, null);
	}

	private static ComparedCard onlyCompared(EngineResult result) {
		assertThat(result.comparedCards()).hasSize(1);
		return result.comparedCards().get(0);
	}

	private static ServiceResult service(ComparedCard card, String serviceId) {
		return card.services()
			.stream()
			.filter(service -> service.serviceId().equals(serviceId))
			.findFirst()
			.orElseThrow(() -> new AssertionError("no service " + serviceId));
	}

}
