package com.beone.api.benefit.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.card.LimitBucketId;
import com.beone.api.benefit.card.MerchantClass;
import com.beone.api.benefit.card.PaymentMethod;
import com.beone.api.benefit.input.CardMonthlyState;
import com.beone.api.benefit.input.Ledger;
import com.beone.api.benefit.input.LedgerCoverage;
import com.beone.api.benefit.input.LedgerTransaction;
import com.beone.api.benefit.input.Order;
import com.beone.api.benefit.input.PlannedSpendings;
import com.beone.api.benefit.input.RecommendationInput;
import com.beone.api.benefit.input.SelectionPreference;
import com.beone.api.benefit.money.BenefitAmount;
import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.result.ApplicationStatus;
import com.beone.api.benefit.result.SelectionBasis;
import com.beone.api.benefit.rule.TriggerEvidence;
import com.beone.api.benefit.state.BasisPeriod;
import com.beone.api.benefit.state.StateValue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 입력 변경 시험 M01~M10을 하나씩 확인한다.
 * 기준 사례 모델에서 패치한 필드(와 fixture 기본값이 그 필드로부터 정하는 값)만 바꾼 기대 입력을 만들어 입력 전체를 비교하고, 기대 결과도 확인한다.
 */
class EvaluationMutationMappingTest {

	private static final CardId TITANIUM = CardId.of("09271");

	private static final CardId TALKTALK = CardId.of("09174");

	private static final CardId NORI = CardId.of("01664");

	private static final CardId CHECKCHECK = CardId.of("01914");

	private static Map<String, EvaluationScenario> byId;

	@BeforeAll
	static void readFixture() throws IOException {
		EvaluationFixture fixture = EvaluationFixtureReader.read();
		byId = Stream.concat(fixture.cases().stream(), fixture.mutations().stream())
			.collect(Collectors.toMap(EvaluationScenario::id, Function.identity()));
	}

	@Test
	void m01AddsRepresentativeCard() {
		RecommendationInput base = base("M01", "C19");

		assertThat(input("M01")).isEqualTo(withPreference(base,
				new SelectionPreference(base.preference().mode(), Optional.of(TITANIUM))));
		ExpectedOutcome expected = expected("M01");
		assertThat(expected.tie()).containsExactly(CHECKCHECK, TITANIUM);
		assertThat(expected.tieBreak()).contains(SelectionBasis.REPRESENTATIVE_CARD);
		assertThat(expected.selected()).contains(TITANIUM);
	}

	@Test
	void m02MovesOrderDateToNovember() {
		RecommendationInput base = base("M02", "C06");
		RecommendationInput mutated = input("M02");
		LocalDate november = LocalDate.of(2026, 11, 1);

		// fixture 기본값은 사용액 기준월과 완전 전월 기간을 주문일에서 정하므로 둘 다 함께 바뀐다.
		BasisPeriod novemberMonth = BasisPeriod.month(YearMonth.of(2026, 11));
		CardMonthlyState baseState = base.states().get(0);
		CardMonthlyState expectedState = new CardMonthlyState(baseState.cardId(), baseState.priorPerformance(),
				baseState.usedBenefits(), StateValue.synthetic(baseState.otherBucketsUsed().knownValue(), novemberMonth),
				baseState.serviceSelections());
		Ledger expectedLedger = new Ledger(base.ledger().source(), base.ledger().transactions(),
				List.of(new LedgerCoverage(TALKTALK, BasisPeriod.month(YearMonth.of(2026, 10)))));
		assertThat(mutated).isEqualTo(new RecommendationInput(base.referenceDate(),
				withOrderDate(base.order(), november), base.cards(), expectedLedger, List.of(expectedState),
				base.plannedSpendings(), base.preference()));
		assertThat(mutated.referenceDate()).as("the scenario clock does not move").isEqualTo(LocalDate.of(2026, 10, 15));
		assertExpected("M02", TALKTALK, 0, ApplicationStatus.NOT_APPLICABLE_PERFORMANCE);
	}

	@Test
	void m03ChangesMerchantClass() {
		RecommendationInput base = base("M03", "C06");
		Order order = base.order();

		assertThat(input("M03")).isEqualTo(withOrder(base, new Order(order.orderId(), order.merchantName(),
				order.amount(), order.date(), order.time(), MerchantClass.of("ORDINARY_DOMESTIC"), order.paymentMethod())));
		assertExpected("M03", TALKTALK, 0, ApplicationStatus.NOT_APPLICABLE_CATEGORY);
	}

	@Test
	void m04ChangesPaymentMethod() {
		RecommendationInput base = base("M04", "C10");
		Order order = base.order();

		assertThat(input("M04")).isEqualTo(withOrder(base, new Order(order.orderId(), order.merchantName(),
				order.amount(), order.date(), order.time(), order.merchantClass(), PaymentMethod.ELIGIBLE_SIMPLE_PAY)));
		assertExpected("M04", TALKTALK, 3_000, ApplicationStatus.APPLIED_CAP_REMAINING);
		assertThat(expected("M04").serviceBenefits()).containsOnly(
				Map.entry(ServiceRef.parse("09174:Great"), BenefitAmount.confirmed(1_000)),
				Map.entry(ServiceRef.parse("09174:Check"), BenefitAmount.confirmed(2_000)));
		assertThat(expected("M04").serviceStatus()).containsOnly(
				Map.entry(ServiceRef.parse("09174:Great"), ApplicationStatus.APPLIED_CAP_REMAINING),
				Map.entry(ServiceRef.parse("09174:Check"), ApplicationStatus.APPLIED));
	}

	@Test
	void m05ChangesFirstLedgerAmount() {
		RecommendationInput base = base("M05", "C11");
		LedgerTransaction first = base.ledger().transactions().get(0);
		LedgerTransaction changed = new LedgerTransaction(first.cardId(), first.date(), first.time(), Won.of(199_999),
				first.type(), first.nature(), first.merchantClass(), first.paymentMethod(), first.originalDate());

		assertThat(input("M05")).isEqualTo(withLedger(base, new Ledger(base.ledger().source(), List.of(changed),
				base.ledger().completePeriods())));
		assertExpected("M05", NORI, 0, ApplicationStatus.NOT_APPLICABLE_PERFORMANCE);
	}

	@Test
	void m06LowersOrderAmountBelowTelecomMinimum() {
		RecommendationInput base = base("M06", "C13");

		assertThat(input("M06")).isEqualTo(withOrder(base, withAmount(base.order(), 49_999)));
		assertExpected("M06", NORI, 0, ApplicationStatus.NOT_APPLICABLE_MIN_AMOUNT);
	}

	@Test
	void m07RaisesOrderAmountAndKeepsFixedTelecomDiscount() {
		RecommendationInput base = base("M07", "C13");

		assertThat(input("M07")).isEqualTo(withOrder(base, withAmount(base.order(), 80_000)));
		assertExpected("M07", NORI, 2_500, ApplicationStatus.APPLIED);
	}

	@Test
	void m08AddsCuAreaUsage() {
		RecommendationInput base = base("M08", "C18");
		CardMonthlyState baseState = base.states().get(0);
		Map<LimitBucketId, StateValue<Won>> used = new HashMap<>(baseState.usedBenefits());
		used.put(LimitBucketId.of("CU_AREA"),
				StateValue.synthetic(Won.of(4_000), BasisPeriod.month(YearMonth.of(2026, 10))));
		CardMonthlyState expectedState = new CardMonthlyState(baseState.cardId(), baseState.priorPerformance(), used,
				baseState.otherBucketsUsed(), baseState.serviceSelections());

		assertThat(input("M08")).isEqualTo(new RecommendationInput(base.referenceDate(), base.order(), base.cards(),
				base.ledger(), List.of(expectedState), base.plannedSpendings(), base.preference()));
		assertExpected("M08", CHECKCHECK, 0, ApplicationStatus.LIMIT_EXHAUSTED);
	}

	@Test
	void m09RemovesPlannedSpending() {
		RecommendationInput base = base("M09", "C20");

		assertThat(base.plannedSpendings().items()).hasSize(1);
		assertThat(input("M09")).isEqualTo(new RecommendationInput(base.referenceDate(), base.order(), base.cards(),
				base.ledger(), base.states(), PlannedSpendings.none(), base.preference()));
		ExpectedOutcome expected = expected("M09");
		assertThat(expected.selected()).contains(TALKTALK);
		assertThat(expected.monthlyTotal()).contains(Won.of(5_000));
		assertThat(expected.currentOrderBenefits()).containsOnly(Map.entry(TALKTALK, BenefitAmount.confirmed(5_000)),
				Map.entry(TITANIUM, BenefitAmount.confirmed(100)));
	}

	@Test
	void m10RemovesTheCheckCardButKeepsItsLedger() {
		RecommendationInput base = base("M10", "C19");
		RecommendationInput mutated = input("M10");

		// 보유 카드, 월간 상태, 완전 기간 선언은 보유 카드를 따라 바뀐다.
		// 제거된 카드의 과거 거래는 fixture에 남아 있다(사례집: 비교에서 제외).
		assertThat(mutated).isEqualTo(new RecommendationInput(base.referenceDate(), base.order(),
				base.cards().stream().filter(card -> card.cardId().equals(TITANIUM)).toList(),
				new Ledger(base.ledger().source(), base.ledger().transactions(),
						base.ledger().completePeriods().stream().filter(c -> c.cardId().equals(TITANIUM)).toList()),
				base.states().stream().filter(state -> state.cardId().equals(TITANIUM)).toList(),
				base.plannedSpendings(), base.preference()));
		assertThat(mutated.ledger().transactions()).extracting(LedgerTransaction::cardId).containsExactly(CHECKCHECK);
		assertExpected("M10", TITANIUM, 1_000, ApplicationStatus.APPLIED);
		assertThat(expected("M10").tie()).isEmpty();
	}

	@Test
	void fixtureLedgerCannotConfirmAbsenceOfCancellationsAfterThePriorMonth() {
		// fixture는 전월만 완전하다고 선언한다. 실적 기간 종료일부터 시나리오 기준일 사이에 접수된 취소를 배제할 수 없으므로,
		// 미확정 취소 귀속월 조건은 여전히 필요한 것으로 봐야 한다.
		RecommendationInput c06 = byId.get("C06").input();
		RecommendationInput c14 = byId.get("C14").input();
		BasisPeriod september = BasisPeriod.month(YearMonth.of(2026, 9));

		assertThat(c06.ledger().cancellationEvidence(TALKTALK, september, c06.referenceDate()))
			.isEqualTo(TriggerEvidence.UNDETERMINED);
		assertThat(c14.ledger().cancellationEvidence(NORI, september, c14.referenceDate()))
			.isEqualTo(TriggerEvidence.PRESENT);
	}

	private static RecommendationInput base(String mutationId, String baseId) {
		assertThat(byId.get(mutationId).baseCaseId()).contains(baseId);
		return byId.get(baseId).input();
	}

	private static RecommendationInput input(String id) {
		return byId.get(id).input();
	}

	private static ExpectedOutcome expected(String id) {
		return byId.get(id).expected();
	}

	private static void assertExpected(String id, CardId card, long won, ApplicationStatus status) {
		ExpectedOutcome expected = expected(id);
		assertThat(expected.currentOrderBenefits()).containsOnly(Map.entry(card, BenefitAmount.confirmed(won)));
		assertThat(expected.statusByCard()).containsOnly(Map.entry(card, status));
		assertThat(expected.selected()).contains(card);
	}

	private static RecommendationInput withOrder(RecommendationInput base, Order order) {
		return new RecommendationInput(base.referenceDate(), order, base.cards(), base.ledger(), base.states(),
				base.plannedSpendings(), base.preference());
	}

	private static RecommendationInput withLedger(RecommendationInput base, Ledger ledger) {
		return new RecommendationInput(base.referenceDate(), base.order(), base.cards(), ledger, base.states(),
				base.plannedSpendings(), base.preference());
	}

	private static RecommendationInput withPreference(RecommendationInput base, SelectionPreference preference) {
		return new RecommendationInput(base.referenceDate(), base.order(), base.cards(), base.ledger(), base.states(),
				base.plannedSpendings(), preference);
	}

	private static Order withAmount(Order order, long won) {
		return new Order(order.orderId(), order.merchantName(), Won.of(won), order.date(), order.time(),
				order.merchantClass(), order.paymentMethod());
	}

	private static Order withOrderDate(Order order, LocalDate date) {
		return new Order(order.orderId(), order.merchantName(), order.amount(), date, order.time(),
				order.merchantClass(), order.paymentMethod());
	}

}
