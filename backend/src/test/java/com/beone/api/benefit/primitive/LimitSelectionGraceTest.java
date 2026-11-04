package com.beone.api.benefit.primitive;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.card.LimitBucketId;
import com.beone.api.benefit.card.MerchantClass;
import com.beone.api.benefit.card.PaymentMethod;
import com.beone.api.benefit.card.ServiceId;
import com.beone.api.benefit.input.CardMonthlyState;
import com.beone.api.benefit.input.Order;
import com.beone.api.benefit.input.PolicyTermEvidence;
import com.beone.api.benefit.input.ServiceSelection;
import com.beone.api.benefit.money.BenefitAmount;
import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.result.ApplicationStatus;
import com.beone.api.benefit.result.LimitReason;
import com.beone.api.benefit.result.OrderBenefit;
import com.beone.api.benefit.result.OrderServiceBenefit;
import com.beone.api.benefit.result.PendingReason;
import com.beone.api.benefit.result.ServiceResultNotes;
import com.beone.api.benefit.result.UsageBasis;
import com.beone.api.benefit.rule.RuleCatalog;
import com.beone.api.benefit.rule.TriggerEvidence;
import com.beone.api.benefit.rule.loading.RuleCatalogLoader;
import com.beone.api.benefit.state.BasisPeriod;
import com.beone.api.benefit.state.StateValue;

/**
 * 규칙 JSON 4종으로 한도 체인, 택1 선택, 신규 유예를 확인한다(D-18 K1, D-20). 기대값은 결정 기록과 규칙 근거 표에서 손으로 계산했다.
 */
class LimitSelectionGraceTest {

	private static final RuleCatalog CATALOG = RuleCatalogLoader.load();

	private static final CardId TALKTALK = CardId.of("09174");

	private static final CardId NORI = CardId.of("01664");

	private static final CardId CHECKCHECK = CardId.of("01914");

	private static final CardId TITANIUM = CardId.of("09271");

	private static final LocalDate DAY = LocalDate.of(2026, 10, 15);

	private static final BasisPeriod OCTOBER = BasisPeriod.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

	private static final BasisPeriod SEPTEMBER = BasisPeriod.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

	private static final LocalDate OLD = LocalDate.of(2026, 1, 1);

	private static Order order(String merchantClass, long won, PaymentMethod method) {
		return order(merchantClass, won, method, DAY);
	}

	private static Order order(String merchantClass, long won, PaymentMethod method, LocalDate date) {
		return new Order(Optional.empty(), Optional.empty(), Won.of(won), date, Optional.empty(),
				MerchantClass.of(merchantClass), method);
	}

	private static StateValue<Won> known(long won) {
		return StateValue.synthetic(Won.of(won), OCTOBER);
	}

	private static StateValue<LocalDate> registered(LocalDate date) {
		return StateValue.synthetic(date, BasisPeriod.day(date));
	}

	private static StateValue<ServiceSelection> selection(ServiceSelection value) {
		return StateValue.synthetic(value, BasisPeriod.day(value.effectiveFrom()));
	}

	/** 체인 버킷 사용액을 이름으로 지정한다. 값이 null이면 UNKNOWN이고, 지정하지 않은 버킷은 입력되지 않은 것이다. */
	private static Map<LimitBucketId, StateValue<Won>> used(Object... bucketThenWon) {
		Map<LimitBucketId, StateValue<Won>> used = new HashMap<>();
		for (int i = 0; i < bucketThenWon.length; i += 2) {
			Object value = bucketThenWon[i + 1];
			used.put(LimitBucketId.of((String) bucketThenWon[i]),
					value == null ? StateValue.unknown(OCTOBER, null) : known(((Number) value).longValue()));
		}
		return used;
	}

	private static CardMonthlyState state(CardId card, long performance, Map<LimitBucketId, StateValue<Won>> used,
			List<StateValue<ServiceSelection>> selections) {
		// otherBucketsUsed는 0원이어도 체인 버킷 입력을 대신하지 않는다.
		return new CardMonthlyState(card, Optional.of(StateValue.synthetic(Won.of(performance), SEPTEMBER)), used, known(0),
				selections);
	}

	private static OrderBenefit.Decided decide(CardId card, Order order, CardMonthlyState state, StateValue<LocalDate> registeredAt) {
		return decide(card, order, state, registeredAt, TriggerEvidence.CONFIRMED_ABSENT);
	}

	private static OrderBenefit.Decided decide(CardId card, Order order, CardMonthlyState state,
			StateValue<LocalDate> registeredAt, TriggerEvidence policy) {
		OrderBenefit result = OrderBenefitResolver.resolve(CATALOG, card, order.date(), order, state, registeredAt,
				new PolicyTermEvidence(card, policy, policy));
		return (OrderBenefit.Decided) result;
	}

	private static OrderServiceBenefit service(OrderBenefit.Decided result, String id) {
		return result.services()
			.stream()
			.filter(service -> service.candidate().serviceId().equals(ServiceId.of(id)))
			.findFirst()
			.orElseThrow(() -> new AssertionError("no decided service " + id + " in " + result));
	}

	private static List<String> pendingServices(OrderBenefit.Decided result) {
		return result.source().needingConfirmation().stream().map(pending -> pending.serviceId().toString()).toList();
	}

	private static List<String> unsupportedServices(OrderBenefit.Decided result) {
		return result.source().unsupported().stream().map(unsupported -> unsupported.serviceId().toString()).toList();
	}

	@Nested
	class LimitChain {

		private static final Order CU = order("CU_STORE", 10_000, PaymentMethod.DIRECT);

		private OrderBenefit.Decided cu(Map<LimitBucketId, StateValue<Won>> used) {
			return decide(CHECKCHECK, CU, state(CHECKCHECK, 300_000, used, List.of()), registered(OLD));
		}

		// 01914 1구간: CU 한도 2,000원, 통합 COMMON 한도 10,000원, CU 정액 1,000원
		private Map<LimitBucketId, StateValue<Won>> allZero() {
			Map<LimitBucketId, StateValue<Won>> used = new HashMap<>();
			for (String id : List.of("COMMON", "CU", "STARBUCKS", "CGV", "TENBYTEN", "SIMPLE_PAY", "BEAUTY", "BOOKSTORE",
					"CULTURE", "TRANSIT")) {
				used.put(LimitBucketId.of(id), known(0));
			}
			return used;
		}

		@Test
		void parentBucketCanBeTheSmallestRemainingLimit() {
			OrderBenefit.Decided result = cu(used("CU", 0, "COMMON", 10_000));

			assertThat(service(result, "CU").candidate().unmetConditions()).containsExactly(ApplicationStatus.LIMIT_EXHAUSTED);
			assertThat(result.orderBenefit()).isEqualTo(BenefitAmount.confirmed(0));
		}

		@Test
		void usageEqualToLimitIsExhaustedNotPending() {
			OrderBenefit.Decided result = cu(used("CU", 2_000, "COMMON", 0));

			assertThat(service(result, "CU").candidate().unmetConditions()).containsExactly(ApplicationStatus.LIMIT_EXHAUSTED);
		}

		@Test
		void fixedBenefitAboveThePositiveRemainingLimitNeedsConfirmation() {
			OrderBenefit.Decided result = cu(used("CU", 1_500, "COMMON", 0));

			assertThat(pendingServices(result)).containsExactly("CU");
			assertThat(result.orderBenefit().isConfirmed()).isFalse();
		}

		@Test
		void unknownChainBucketNeedsConfirmation() {
			OrderBenefit.Decided result = cu(used("CU", 0, "COMMON", null));

			assertThat(pendingServices(result)).containsExactly("CU");
		}

		@Test
		void usageOfAnotherMonthNeedsConfirmationOnEveryChainBucket() {
			// D-16: 전월 기준 사용액은 이번 달 남은 한도를 줄이지 않고 계산에 쓰지 않는다.
			StateValue<Won> september = StateValue.synthetic(Won.of(2_000), SEPTEMBER);
			Map<LimitBucketId, StateValue<Won>> childOld = used("COMMON", 0);
			childOld.put(LimitBucketId.of("CU"), september);
			Map<LimitBucketId, StateValue<Won>> parentOld = used("CU", 0);
			parentOld.put(LimitBucketId.of("COMMON"), StateValue.synthetic(Won.of(10_000), SEPTEMBER));

			for (Map<LimitBucketId, StateValue<Won>> used : List.of(childOld, parentOld)) {
				OrderBenefit.Decided result = cu(used);

				assertThat(pendingServices(result)).containsExactly("CU");
				assertThat(result.orderBenefit()).isInstanceOf(BenefitAmount.NeedsConfirmation.class);
			}
		}

		@Test
		void confirmedUnmetWinsOverUsageOfAnotherMonth() {
			Map<LimitBucketId, StateValue<Won>> old = used("COMMON", 0);
			old.put(LimitBucketId.of("CU"), StateValue.synthetic(Won.of(2_000), SEPTEMBER));
			Order small = order("CU_STORE", 9_999, PaymentMethod.DIRECT);

			OrderBenefit.Decided result = decide(CHECKCHECK, small, state(CHECKCHECK, 300_000, old, List.of()), registered(OLD));

			assertThat(service(result, "CU").candidate().unmetConditions()).containsExactly(ApplicationStatus.NOT_APPLICABLE_MIN_AMOUNT);
		}

		@Test
		void missingChainBucketIsNotReplacedByOtherBucketsUsed() {
			OrderBenefit.Decided result = cu(used("CU", 0));

			assertThat(pendingServices(result)).containsExactly("CU");
		}

		@Test
		void exhaustedBucketDoesNotConfirmZeroWhileAnotherChainBucketIsUnknown() {
			OrderBenefit.Decided result = cu(used("CU", 2_000, "COMMON", null));

			assertThat(pendingServices(result)).containsExactly("CU");
		}

		@Test
		void usageAboveLimitNeedsConfirmationWithItsReason() {
			OrderBenefit.Decided result = cu(used("CU", 2_001, "COMMON", null));

			assertThat(pendingServices(result)).containsExactly("CU");
			assertThat(result.source().needingConfirmation().get(0).limitReason()).contains(LimitReason.USAGE_EXCEEDS_LIMIT);
			ServiceResultNotes notes = notes(result, "CU");
			assertThat(notes.limitReason()).contains(LimitReason.USAGE_EXCEEDS_LIMIT);
		}

		@Test
		void unrelatedServicesDoNotNeedTheirBuckets() {
			// D-18 K1: 업종이 맞지 않는 서비스는 사용액 입력과 무관하게 확정 0원이다.
			OrderBenefit.Decided result = cu(used("CU", 0, "COMMON", 0));

			assertThat(result.orderBenefit()).isEqualTo(BenefitAmount.confirmed(1_000));
			assertThat(service(result, "TENBYTEN").candidate().unmetConditions())
				.containsExactly(ApplicationStatus.NOT_APPLICABLE_CATEGORY);
		}

		@Test
		void confirmedUnmetWinsOverUnknownUsage() {
			Order small = order("CU_STORE", 9_999, PaymentMethod.DIRECT);
			OrderBenefit.Decided minimum = decide(CHECKCHECK, small, state(CHECKCHECK, 300_000, used(), List.of()), registered(OLD));
			assertThat(service(minimum, "CU").candidate().unmetConditions()).containsExactly(ApplicationStatus.NOT_APPLICABLE_MIN_AMOUNT);

			OrderBenefit.Decided performance = decide(CHECKCHECK, CU, state(CHECKCHECK, 299_999, used(), List.of()), registered(OLD));
			assertThat(service(performance, "CU").candidate().unmetConditions()).containsExactly(ApplicationStatus.NOT_APPLICABLE_PERFORMANCE);
			assertThat(performance.orderBenefit()).isEqualTo(BenefitAmount.confirmed(0));
		}

		@Test
		void giftCardRegressionStaysExcludedOnCu() {
			Order gift = order("GIFT_CARD_PREPAID", 10_000, PaymentMethod.DIRECT);

			OrderBenefit.Decided result = decide(CHECKCHECK, gift, state(CHECKCHECK, 300_000, allZero(), List.of()), registered(OLD));

			assertThat(service(result, "CU").candidate().unmetConditions()).contains(ApplicationStatus.NOT_APPLICABLE_EXCLUDED);
			assertThat(service(result, "CU").contribution()).isEqualTo(BenefitAmount.confirmed(0));
			assertThat(result.orderBenefit()).isEqualTo(BenefitAmount.confirmed(0));
		}

		@Test
		void rateIsCutByTheRemainingLimitAfterTruncation() {
			// 09174 GREAT 50%: 3,333원 → 1,666.5원 → 절사 1,666원. 남은 한도 500원으로 줄인다.
			Order starbucks = order("STARBUCKS_STORE", 3_333, PaymentMethod.DIRECT);

			OrderBenefit.Decided result = decide(TALKTALK, starbucks,
					state(TALKTALK, 300_000, used("GREAT", 9_500), List.of()), registered(OLD));

			OrderServiceBenefit great = service(result, "GREAT");
			assertThat(great.candidate().standaloneAmount()).isEqualTo(BenefitAmount.confirmed(1_666));
			assertThat(great.contribution()).isEqualTo(BenefitAmount.confirmed(500));
			assertThat(great.appliedStatus()).contains(ApplicationStatus.APPLIED_CAP_REMAINING);
			assertThat(result.orderBenefit()).isEqualTo(BenefitAmount.confirmed(500));
			assertThat(notes(result, "GREAT").fractionalWonTruncated()).isTrue();
			assertThat(notes(result, "GREAT").fractionalWonBasis()).isPresent();
		}

		@Test
		void exhaustedZeroIsRecheckedWhenPerformancePolicyMayChangeTheTier() {
			// 09174는 취소 귀속이 미확정이고 GREAT 한도가 실적 조건을 갖는다(U4).
			Order starbucks = order("STARBUCKS_STORE", 20_000, PaymentMethod.DIRECT);

			OrderBenefit.Decided result = decide(TALKTALK, starbucks,
					state(TALKTALK, 300_000, used("GREAT", 10_000), List.of()), registered(OLD), TriggerEvidence.UNDETERMINED);

			assertThat(result.rechecks()).extracting(recheck -> recheck.candidate().serviceId())
				.contains(ServiceId.of("GREAT"));
			assertThat(result.orderBenefit().isConfirmed()).isFalse();
			assertThat(result.reasons()).extracting(PendingReason::kind).contains(PendingReason.Kind.PERFORMANCE_POLICY);
		}

	}

	@Nested
	class Selection {

		private static final Order GS25 = order("GS25_STORE", 50_000, PaymentMethod.DIRECT);

		private OrderBenefit.Decided titanium(Order order, List<StateValue<ServiceSelection>> selections) {
			return decide(TITANIUM, order, state(TITANIUM, 400_000, used("FUEL", 0, "STORE", 0), selections), registered(OLD));
		}

		@Test
		void noneMakesTheOtherSelectionServicesNotApplicable() {
			OrderBenefit.Decided result = titanium(GS25, List.of(selection(ServiceSelection.none(OLD))));

			assertThat(service(result, "STORE").candidate().unmetConditions()).containsExactly(ApplicationStatus.NOT_APPLICABLE_SELECTION);
			assertThat(service(result, "BASE").contribution()).isEqualTo(BenefitAmount.confirmed(500));
			assertThat(result.orderBenefit()).isEqualTo(BenefitAmount.confirmed(500));
		}

		@Test
		void anotherSelectedServiceMakesThisOneNotApplicable() {
			OrderBenefit.Decided result = titanium(GS25, List.of(selection(new ServiceSelection(ServiceId.of("FUEL"), OLD))));

			assertThat(service(result, "STORE").candidate().unmetConditions()).containsExactly(ApplicationStatus.NOT_APPLICABLE_SELECTION);
			assertThat(service(result, "FUEL").candidate().unmetConditions()).containsExactly(ApplicationStatus.NOT_APPLICABLE_CATEGORY);
		}

		@Test
		void missingSelectionKeepsTheTargetServiceUnsupported() {
			OrderBenefit.Decided result = titanium(GS25, List.of());

			assertThat(unsupportedServices(result)).containsExactly("STORE");
			assertThat(service(result, "FUEL").candidate().unmetConditions()).containsExactly(ApplicationStatus.NOT_APPLICABLE_CATEGORY);
			assertThat(result.orderBenefit().isConfirmed()).isFalse();
		}

		@Test
		void unknownSelectionKeepsTheTargetServiceUnsupported() {
			OrderBenefit.Decided result = titanium(GS25, List.of(StateValue.unknown(null, null)));

			assertThat(unsupportedServices(result)).containsExactly("STORE");
		}

		@Test
		void selectedServiceThatAppliesStaysUnsupported() {
			OrderBenefit.Decided result = titanium(GS25, List.of(selection(new ServiceSelection(ServiceId.of("STORE"), OLD))));

			assertThat(unsupportedServices(result)).containsExactly("STORE");
			assertThat(result.orderBenefit().isConfirmed()).isFalse();
		}

		@Test
		void selectionEffectiveAfterTheOrderDoesNotExcludeAnything() {
			OrderBenefit.Decided result = titanium(GS25, List.of(selection(ServiceSelection.none(DAY.plusDays(1)))));

			assertThat(unsupportedServices(result)).containsExactly("STORE");
		}

		@Test
		void selectionEffectiveOnTheOrderDateApplies() {
			OrderBenefit.Decided result = titanium(GS25, List.of(selection(ServiceSelection.none(DAY))));

			assertThat(service(result, "STORE").candidate().unmetConditions()).containsExactly(ApplicationStatus.NOT_APPLICABLE_SELECTION);
		}

		@Test
		void latestEffectiveSelectionWins() {
			OrderBenefit.Decided result = titanium(GS25, List.of(selection(new ServiceSelection(ServiceId.of("STORE"), OLD)),
					selection(new ServiceSelection(ServiceId.of("FUEL"), OLD.plusDays(1)))));

			assertThat(service(result, "STORE").candidate().unmetConditions()).containsExactly(ApplicationStatus.NOT_APPLICABLE_SELECTION);
		}

		@Test
		void selectionOutsideTheRuleIsNotTreatedAsAnotherChoice() {
			OrderBenefit.Decided result = titanium(GS25, List.of(selection(new ServiceSelection(ServiceId.of("BASE"), OLD))));

			assertThat(unsupportedServices(result)).containsExactly("STORE");
		}

		@Test
		void unconfirmedInclusionBlocksWithoutASelectionAndClearsWithNone() {
			// 09271 STORE: STARBUCKS_STORE가 COFFEE_BEVERAGE에 드는지 확인되지 않았다.
			Order starbucks = order("STARBUCKS_STORE", 10_000, PaymentMethod.DIRECT);

			assertThat(unsupportedServices(titanium(starbucks, List.of()))).containsExactly("STORE");
			assertThat(service(titanium(starbucks, List.of(selection(ServiceSelection.none(OLD)))), "STORE").candidate()
				.unmetConditions()).containsExactly(ApplicationStatus.NOT_APPLICABLE_SELECTION);
		}

		@Test
		void unconfirmedInclusionOfANonSelectionServiceNeedsConfirmation() {
			// 09174 GREAT: COFFEE_BEVERAGE가 STARBUCKS_STORE를 포함하는지 확인되지 않았다.
			Order coffee = order("COFFEE_BEVERAGE", 10_000, PaymentMethod.DIRECT);

			OrderBenefit.Decided result = decide(TALKTALK, coffee, state(TALKTALK, 300_000, used("GREAT", 0), List.of()),
					registered(OLD));

			assertThat(pendingServices(result)).contains("GREAT");
			assertThat(result.orderBenefit().isConfirmed()).isFalse();
		}

	}

	@Nested
	class Grace {

		private static final Order STARBUCKS_TALK = order("STARBUCKS_STORE", 20_000, PaymentMethod.DIRECT);

		private OrderBenefit.Decided talktalk(long performance, StateValue<LocalDate> registeredAt, Order order) {
			return decide(TALKTALK, order, state(TALKTALK, performance, used("GREAT", 0), List.of()), registeredAt);
		}

		private OrderBenefit.Decided nori(long performance, LocalDate registeredAt) {
			Order starbucks = order("STARBUCKS_STORE", 10_000, PaymentMethod.DIRECT);
			return decide(NORI, starbucks, state(NORI, performance, used("COMMON", 0), List.of()), registered(registeredAt));
		}

		private OrderBenefit.Decided checkcheck(long performance, LocalDate registeredAt) {
			Order cu = order("CU_STORE", 10_000, PaymentMethod.DIRECT);
			return decide(CHECKCHECK, cu, state(CHECKCHECK, performance, used("CU", 0, "COMMON", 0), List.of()),
					registered(registeredAt));
		}

		@Test
		void untilEndOfNextMonthBoundary() {
			// 9월 등록: 10월 말까지 유예. 11월 1일은 일반 판정이다.
			LocalDate september = LocalDate.of(2026, 9, 30);
			assertThat(talktalk(0, registered(september), order("STARBUCKS_STORE", 20_000, PaymentMethod.DIRECT, LocalDate.of(2026, 10, 31))).orderBenefit())
				.isEqualTo(BenefitAmount.confirmed(10_000));
			assertThat(service(talktalk(0, registered(september), order("STARBUCKS_STORE", 20_000, PaymentMethod.DIRECT, LocalDate.of(2026, 11, 1))), "GREAT")
				.candidate().unmetConditions()).containsExactly(ApplicationStatus.NOT_APPLICABLE_PERFORMANCE);
		}

		@Test
		void sixtyDaysCountTheRegistrationDay() {
			// 등록일 포함 60일: 8월 17일 등록이면 10월 15일이 60일째, 8월 16일 등록이면 61일째다.
			assertThat(nori(0, LocalDate.of(2026, 8, 17)).orderBenefit()).isEqualTo(BenefitAmount.confirmed(2_000));
			assertThat(service(nori(0, LocalDate.of(2026, 8, 16)), "STARBUCKS").candidate().unmetConditions())
				.containsExactly(ApplicationStatus.NOT_APPLICABLE_PERFORMANCE);
		}

		@Test
		void unknownRegistrationNeedsConfirmationButKeepsConfirmedUnmet() {
			OrderBenefit.Decided result = talktalk(300_000, StateValue.unknown(null, null), STARBUCKS_TALK);

			assertThat(pendingServices(result)).contains("GREAT");
			assertThat(service(result, "ENJOY").candidate().unmetConditions()).contains(ApplicationStatus.NOT_APPLICABLE_CATEGORY);
			assertThat(result.orderBenefit().isConfirmed()).isFalse();
		}

		@Test
		void orderBeforeRegistrationNeedsConfirmation() {
			OrderBenefit.Decided result = talktalk(300_000, registered(DAY.plusDays(1)), STARBUCKS_TALK);

			assertThat(pendingServices(result)).contains("GREAT");
		}

		@ParameterizedTest(name = "09174 P={0}")
		@CsvSource({ "0,10000", "1,", "299999,", "300000,10000", "900000,10000" })
		void talktalkHasOneTier(long performance, Long expected) {
			assertAmount(talktalk(performance, registered(DAY), STARBUCKS_TALK), expected);
		}

		@ParameterizedTest(name = "01664 P={0}")
		@CsvSource({ "0,2000", "1,", "199999,", "200000,2000", "299999,2000", "300000,", "1000000," })
		void noriSeparatesTheGraceLimitFromTheNextTier(long performance, Long expected) {
			assertAmount(nori(performance, DAY), expected);
		}

		@ParameterizedTest(name = "01914 P={0}")
		@CsvSource({ "0,1000", "1,1000", "299999,1000", "300000,1000", "599999,1000", "600000," })
		void checkcheckGivesTheFirstTierToPartialPerformance(long performance, Long expected) {
			assertAmount(checkcheck(performance, DAY), expected);
		}

		@Test
		void servicesOutsideTheGraceListStayOnNormalJudgement() {
			// 01664 TELECOM은 유예 대상이 아니다. 유예 기간이어도 실적 0원이면 실적 미충족이다.
			Order telecom = order("TELECOM_SKT", 50_000, PaymentMethod.AUTOPAY);

			OrderBenefit.Decided result = decide(NORI, telecom, state(NORI, 0, used("TELECOM", 0, "COMMON", 0), List.of()),
					registered(DAY));

			assertThat(service(result, "TELECOM").candidate().unmetConditions()).containsExactly(ApplicationStatus.NOT_APPLICABLE_PERFORMANCE);
		}

		private void assertAmount(OrderBenefit.Decided result, Long expected) {
			if (expected == null) {
				assertThat(result.orderBenefit().isConfirmed()).isFalse();
			}
			else {
				assertThat(result.orderBenefit()).isEqualTo(BenefitAmount.confirmed(expected));
			}
		}

	}

	@Nested
	class Notes {

		@Test
		void limitServicesShowTheUsageBasisEvenWithZero() {
			Order cu = order("CU_STORE", 10_000, PaymentMethod.DIRECT);

			OrderBenefit.Decided result = decide(CHECKCHECK, cu, state(CHECKCHECK, 300_000, used("CU", 0, "COMMON", 0), List.of()),
					registered(OLD));

			ServiceResultNotes zero = notes(result, "TENBYTEN");
			assertThat(zero.usageBasis()).contains(UsageBasis.USER_ENTERED);
			assertThat(zero.mayDifferFromActual()).isTrue();
			assertThat(zero.usageNote()).contains("입력한 사용액 기준, 미매입 전표에 따라 달라질 수 있음");
			assertThat(zero.unevaluatedExclusions()).isNotEmpty();
		}

		@Test
		void serviceWithoutLimitHasNoUsageBasisAndShowsTruncation() {
			// 09271 BASE 1%: 33,333원 → 333.33원 → 절사 333원. 한도 없음.
			Order order = order("ORDINARY_DOMESTIC", 33_333, PaymentMethod.DIRECT);

			OrderBenefit.Decided result = decide(TITANIUM, order,
					state(TITANIUM, 0, used(), List.of(selection(ServiceSelection.none(OLD)))), registered(OLD));

			ServiceResultNotes base = notes(result, "BASE");
			assertThat(result.orderBenefit()).isEqualTo(BenefitAmount.confirmed(333));
			assertThat(base.usageBasis()).isEmpty();
			assertThat(base.mayDifferFromActual()).isFalse();
			assertThat(base.fractionalWonTruncated()).isTrue();
			assertThat(base.fractionalWonBasis()).isPresent();
			assertThat(base.unevaluatedExclusions()).contains("무이자할부");
		}

		@Test
		void wholeWonResultShowsNoTruncation() {
			Order order = order("ORDINARY_DOMESTIC", 100_000, PaymentMethod.DIRECT);

			OrderBenefit.Decided result = decide(TITANIUM, order,
					state(TITANIUM, 0, used(), List.of(selection(ServiceSelection.none(OLD)))), registered(OLD));

			assertThat(notes(result, "BASE").fractionalWonTruncated()).isFalse();
			assertThat(notes(result, "BASE").fractionalWonBasis()).isEmpty();
		}

	}

	private static ServiceResultNotes notes(OrderBenefit.Decided result, String id) {
		return result.serviceNotes()
			.stream()
			.filter(note -> note.serviceId().equals(ServiceId.of(id)))
			.findFirst()
			.orElseThrow();
	}

}
