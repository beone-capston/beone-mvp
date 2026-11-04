package com.beone.api.benefit.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.card.LimitBucketId;
import com.beone.api.benefit.card.ServiceId;
import com.beone.api.benefit.input.CardMonthlyState;
import com.beone.api.benefit.input.HeldCard;
import com.beone.api.benefit.input.LedgerTransaction;
import com.beone.api.benefit.input.PolicyTermEvidence;
import com.beone.api.benefit.input.RecommendationInput;
import com.beone.api.benefit.input.ServiceSelection;
import com.beone.api.benefit.card.TransactionNature;
import com.beone.api.benefit.money.BenefitAmount;
import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.primitive.OrderBenefitResolver;
import com.beone.api.benefit.result.ApplicationStatus;
import com.beone.api.benefit.result.OrderBenefit;
import com.beone.api.benefit.result.OrderServiceBenefit;
import com.beone.api.benefit.rule.CardRuleVersion;
import com.beone.api.benefit.rule.RuleCatalog;
import com.beone.api.benefit.rule.RuleSelection;
import com.beone.api.benefit.rule.TriggerEvidence;
import com.beone.api.benefit.rule.loading.RuleCatalogLoader;
import com.beone.api.benefit.state.BasisPeriod;
import com.beone.api.benefit.state.StateValue;

/**
 * 평가 사례를 규칙 JSON과 판정 코드로 실행해 카드 금액과 서비스 상태를 비교한다. 기대값은 fixture 그대로이며 여기서 고치지 않는다.
 *
 * <p>
 * fixture를 바꾸지 않고 실행기 안에서만 하는 입력 해석(2026-11-04 사용자 승인, 네 번째는 fixture 기본값에 따른 해석).
 * <ul>
 * <li>{@code selection}이 없으면 선택형 서비스가 있는 카드에 {@code NONE}, 적용일은 fixture 기본 등록일.</li>
 * <li>사용액은 규칙의 모든 버킷을 fixture 기본값으로 채우고, 키 {@code Great}·{@code CU_AREA}는 버킷 {@code GREAT}·{@code CU}로 대응.</li>
 * <li>전월 실적은 주문월 전월의 {@code ORDINARY} 합계. 실적 제외를 계산하지 않는다.</li>
 * <li>취소 귀속·할인 매출 근거는 {@code CONFIRMED_ABSENT}. fixture 기본값이 "unless the case says so" 취소·경쟁 혜택 없음이고, 대상 사례의
 * 전월 거래가 모두 {@code ORDINARY}이기 때문이다.</li>
 * </ul>
 * 카드 상태({@code applicationStatusByCard})는 비교하지 않는다. 추천 연결 작업 전까지 검증되지 않는다.
 */
class EvaluationCaseRunnerTest {

	/** 대상 밖 사례와 이유. 건너뛰기 표시 대신 목록으로 남기고, 전체 사례와 빠짐없이 나뉘는지 확인한다. */
	private static final Map<String, String> OUT_OF_SCOPE = Map.of("C07", "중복 합산 (가) 필요", "M04", "중복 합산 (가) 필요", "C09",
			"원장의 세금 실적 제외 필요", "C14", "원장의 취소 귀속 필요", "C20", "예정 소비 비교 필요(D-18 K4, MVP 범위 밖)");

	private static final Map<String, String> USED_KEY_TO_BUCKET = Map.of("Great", "GREAT", "CU_AREA", "CU");

	private static RuleCatalog catalog;

	private static Map<String, EvaluationScenario> scenarios;

	@BeforeAll
	static void load() throws IOException {
		catalog = RuleCatalogLoader.load();
		EvaluationFixture fixture = EvaluationFixtureReader.read();
		scenarios = Stream.concat(fixture.cases().stream(), fixture.mutations().stream())
			.collect(Collectors.toMap(EvaluationScenario::id, Function.identity()));
	}

	static Stream<String> targetIds() throws IOException {
		EvaluationFixture fixture = EvaluationFixtureReader.read();
		return Stream.concat(fixture.cases().stream(), fixture.mutations().stream())
			.map(EvaluationScenario::id)
			.filter(id -> !OUT_OF_SCOPE.containsKey(id));
	}

	@Test
	void targetAndOutOfScopeCasesCoverTheFixture() throws IOException {
		assertThat(targetIds().toList()).hasSize(25);
		assertThat(scenarios).hasSize(25 + OUT_OF_SCOPE.size());
		assertThat(scenarios.keySet()).containsAll(OUT_OF_SCOPE.keySet());
	}

	@ParameterizedTest
	@MethodSource("targetIds")
	void matchesExpectedAmountsAndServiceStatuses(String id) {
		EvaluationScenario scenario = scenarios.get(id);
		Map<CardId, OrderBenefit> results = run(scenario);

		ExpectedOutcome expected = scenario.expected();
		assertThat(results.keySet()).as(id).containsExactlyInAnyOrderElementsOf(expected.currentOrderBenefits().keySet());
		expected.currentOrderBenefits().forEach((card, amount) -> assertSameAmount(id + " " + card, results.get(card).orderBenefit(), amount));
		expected.serviceBenefits().forEach((ref, amount) -> assertSameAmount(id + " " + ref,
				service(results.get(ref.cardId()), ref.serviceId()).contribution(), amount));
		expected.serviceStatus().forEach((ref, status) -> assertThat(statuses(service(results.get(ref.cardId()), ref.serviceId())))
			.as(id + " " + ref)
			.containsExactly(status));
	}

	@Test
	void c02IsUnavailableBecauseItsRuleExpired() {
		OrderBenefit result = run(scenarios.get("C02")).get(CardId.of("09271"));

		assertThat(result).isInstanceOfSatisfying(OrderBenefit.Unavailable.class,
				unavailable -> assertThat(unavailable.status()).isEqualTo(ApplicationStatus.RULE_EXPIRED));
	}

	@Test
	void c05GreatIsLimitExhausted() {
		OrderBenefit result = run(scenarios.get("C05")).get(CardId.of("09174"));

		assertThat(statuses(service(result, ServiceId.of("GREAT")))).containsExactly(ApplicationStatus.LIMIT_EXHAUSTED);
	}

	@Test
	void c10GreatIsCutByTheRemainingLimit() {
		OrderServiceBenefit great = service(run(scenarios.get("C10")).get(CardId.of("09174")), ServiceId.of("GREAT"));

		assertThat(great.candidate().standaloneAmount()).isEqualTo(BenefitAmount.confirmed(10_000));
		assertThat(great.contribution()).isEqualTo(BenefitAmount.confirmed(1_000));
		assertThat(great.appliedStatus()).contains(ApplicationStatus.APPLIED_CAP_REMAINING);
	}

	@Test
	void m08CuIsLimitExhaustedWithTheChainStillKnown() {
		OrderBenefit result = run(scenarios.get("M08")).get(CardId.of("01914"));

		assertThat(result.orderBenefit()).isEqualTo(BenefitAmount.confirmed(0));
		assertThat(statuses(service(result, ServiceId.of("CU")))).containsExactly(ApplicationStatus.LIMIT_EXHAUSTED);
	}

	private static Map<CardId, OrderBenefit> run(EvaluationScenario scenario) {
		RecommendationInput input = scenario.input();
		RuleCatalog rules = scenario.ruleSnapshots().isEmpty() ? catalog : new RuleCatalog(scenario.ruleSnapshots());
		Map<CardId, OrderBenefit> results = new HashMap<>();
		for (HeldCard held : input.cards()) {
			CardId card = held.cardId();
			CardMonthlyState state = interpret(scenario, held, rules);
			PolicyTermEvidence evidence = new PolicyTermEvidence(card, TriggerEvidence.CONFIRMED_ABSENT,
					TriggerEvidence.CONFIRMED_ABSENT);
			results.put(card, OrderBenefitResolver.resolve(rules, card, input.order().date(), input.order(), state,
					held.registeredAt(), evidence));
		}
		return results;
	}

	private static CardMonthlyState interpret(EvaluationScenario scenario, HeldCard held, RuleCatalog rules) {
		RecommendationInput input = scenario.input();
		CardId card = held.cardId();
		CardMonthlyState mapped = input.states().stream().filter(state -> state.cardId().equals(card)).findFirst().orElseThrow();
		YearMonth orderMonth = YearMonth.from(input.order().date());
		BasisPeriod month = BasisPeriod.month(orderMonth);

		Map<LimitBucketId, StateValue<Won>> used = new HashMap<>();
		List<StateValue<ServiceSelection>> selections = mapped.serviceSelections();
		if (rules.select(card, input.order().date()) instanceof RuleSelection.Selected selected) {
			CardRuleVersion rule = selected.rule();
			rule.limitBuckets().forEach(bucket -> used.put(bucket.id(), mapped.otherBucketsUsed()));
			boolean selectable = rule.services().stream().anyMatch(service -> service.requiresSelection());
			if (selections.isEmpty() && selectable) {
				var registered = held.registeredAt().knownValue();
				selections = List.of(StateValue.synthetic(ServiceSelection.none(registered), BasisPeriod.day(registered)));
			}
		}
		mapped.usedBenefits().forEach((bucket, value) -> used.put(
				LimitBucketId.of(USED_KEY_TO_BUCKET.getOrDefault(bucket.toString(), bucket.toString())), value));

		BasisPeriod priorMonth = BasisPeriod.month(orderMonth.minusMonths(1));
		long prior = input.ledger()
			.transactions()
			.stream()
			.filter(transaction -> transaction.cardId().equals(card))
			.filter(transaction -> !transaction.date().isBefore(priorMonth.from()) && !transaction.date().isAfter(priorMonth.to()))
			.filter(EvaluationCaseRunnerTest::isOrdinary)
			.mapToLong(transaction -> transaction.amount().value())
			.sum();
		return new CardMonthlyState(card, Optional.of(StateValue.synthetic(Won.of(prior), priorMonth)), used,
				StateValue.synthetic(mapped.otherBucketsUsed().knownValue(), month), selections);
	}

	private static boolean isOrdinary(LedgerTransaction transaction) {
		return transaction.nature().filter(nature -> nature == TransactionNature.ORDINARY).isPresent();
	}

	private static OrderServiceBenefit service(OrderBenefit result, ServiceId id) {
		assertThat(result).isInstanceOf(OrderBenefit.Decided.class);
		return ((OrderBenefit.Decided) result).services()
			.stream()
			.filter(service -> service.candidate().serviceId().equals(id))
			.findFirst()
			.orElseThrow(() -> new AssertionError("no decided service " + id + " in " + result));
	}

	private static Set<ApplicationStatus> statuses(OrderServiceBenefit service) {
		return service.appliedStatus().map(Set::of).orElse(service.candidate().unmetConditions());
	}

	// 확인 필요의 문구는 비교하지 않는다. 기대값의 확인 필요는 fixture가 금액을 null로 적은 것이다.
	private static void assertSameAmount(String description, BenefitAmount actual, BenefitAmount expected) {
		assertThat(actual.isConfirmed()).as(description + " confirmed: " + actual).isEqualTo(expected.isConfirmed());
		if (expected.isConfirmed()) {
			assertThat(actual.confirmedWon()).as(description).isEqualTo(expected.confirmedWon());
		}
	}

}
