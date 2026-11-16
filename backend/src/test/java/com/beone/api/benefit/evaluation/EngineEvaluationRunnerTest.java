package com.beone.api.benefit.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.card.TransactionNature;
import com.beone.api.benefit.engine.BenefitAmountOutput;
import com.beone.api.benefit.engine.BucketUsageInput;
import com.beone.api.benefit.engine.ComparedCard;
import com.beone.api.benefit.engine.DefaultRecommendationEngine;
import com.beone.api.benefit.engine.EngineRequest;
import com.beone.api.benefit.engine.EngineResult;
import com.beone.api.benefit.engine.ExcludedCard;
import com.beone.api.benefit.engine.HeldCardInput;
import com.beone.api.benefit.engine.MonthlyStateInput;
import com.beone.api.benefit.engine.OrderInput;
import com.beone.api.benefit.engine.PolicyEvidenceInput;
import com.beone.api.benefit.engine.RecommendationEngine;
import com.beone.api.benefit.engine.ServiceResult;
import com.beone.api.benefit.engine.ServiceSelectionInput;
import com.beone.api.benefit.engine.StateInput;
import com.beone.api.benefit.input.CardMonthlyState;
import com.beone.api.benefit.input.HeldCard;
import com.beone.api.benefit.input.LedgerTransaction;
import com.beone.api.benefit.input.Order;
import com.beone.api.benefit.input.RecommendationInput;
import com.beone.api.benefit.input.ServiceSelection;
import com.beone.api.benefit.money.BenefitAmount;
import com.beone.api.benefit.rule.RuleCatalog;
import com.beone.api.benefit.rule.RuleSelection;
import com.beone.api.benefit.rule.loading.RuleCatalogLoader;
import com.beone.api.benefit.state.StateValue;

/**
 * 평가 사례를 실제 입구 {@link RecommendationEngine}으로 실행해 카드 금액·카드 상태·서비스 결과·선택 카드를 비교한다(D-22).
 * 기대값은 fixture 그대로이며 여기서 고치지 않는다.
 *
 * <p>
 * fixture를 입구 입력으로 옮길 때의 해석은 {@link EvaluationCaseRunnerTest}와 같다(2026-11-04 사용자 승인).
 * <ul>
 * <li>카드는 가상 포트폴리오({@code SYNTHETIC}) 보유 카드이고 보유 카드 ID는 {@code uc-}와 규칙 카드 ID를 이은 값이다.</li>
 * <li>{@code selection}이 없으면 선택형 서비스가 있는 카드에 {@code NONE}, 적용일은 fixture 기본 등록일.</li>
 * <li>사용액은 규칙의 모든 버킷을 fixture 기본값으로 채우고, 키 {@code Great}·{@code CU_AREA}는 버킷 {@code GREAT}·{@code CU}로 대응.</li>
 * <li>전월 실적은 주문월 전월의 {@code ORDINARY} 합계. 실적 제외를 계산하지 않는다.</li>
 * <li>실적 정책 근거는 취소·할인 매출 모두 {@code CONFIRMED_ABSENT}. fixture 기본값이 "unless the case says so" 취소·경쟁 혜택 없음이고,
 * 대상 사례의 전월 거래가 모두 {@code ORDINARY}이기 때문이다.</li>
 * </ul>
 * 범위 밖 C09·C14·C20은 기대값과 비교하지 않고 실제 출력만 표준 출력으로 남긴다.
 */
class EngineEvaluationRunnerTest {

	private static final Map<String, String> OUT_OF_SCOPE = Map.of("C09", "원장의 세금 실적 제외 필요", "C14", "원장의 취소 귀속 필요", "C20",
			"예정 소비 비교 필요(D-18 K4, MVP 범위 밖)");

	private static final Map<String, String> USED_KEY_TO_BUCKET = Map.of("Great", "GREAT", "CU_AREA", "CU");

	private static final Map<String, String> SERVICE_KEY_TO_ID = Map.of("Great", "GREAT", "Check", "CHECK");

	private static final PolicyEvidenceInput NO_POLICY_EVENTS = new PolicyEvidenceInput("CONFIRMED_ABSENT",
			"CONFIRMED_ABSENT");

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
		assertThat(targetIds().toList()).hasSize(27);
		assertThat(scenarios).hasSize(27 + OUT_OF_SCOPE.size());
	}

	@ParameterizedTest
	@MethodSource("targetIds")
	void matchesExpectedThroughTheEntry(String id) {
		EvaluationScenario scenario = scenarios.get(id);
		EngineResult result = run(scenario);
		ExpectedOutcome expected = scenario.expected();

		List<String> resultCards = new ArrayList<>();
		result.comparedCards().forEach(card -> resultCards.add(card.cardProductId()));
		result.excludedCards().forEach(card -> resultCards.add(card.cardProductId()));
		assertThat(resultCards).as(id)
			.containsExactlyInAnyOrderElementsOf(expected.currentOrderBenefits().keySet().stream().map(CardId::value).toList());

		expected.currentOrderBenefits().forEach((card, amount) -> {
			assertSameAmount(id + " " + card, cardAmount(result, card), amount);
			assertThat(cardStatus(result, card)).as(id + " " + card + " status").isEqualTo(expected.statusByCard().get(card).name());
		});
		expected.serviceBenefits().forEach((ref, amount) -> {
			ServiceResult service = service(result, ref);
			assertSameAmount(id + " " + ref, service.contribution(), amount);
			assertThat(service.applicationStatus()).as(id + " " + ref + " status")
				.isEqualTo(expected.serviceStatus().get(ref).name());
		});

		Optional<String> selected = Optional.ofNullable(result.selected()).map(card -> card.cardProductId());
		assertThat(selected).as(id + " selected").isEqualTo(expected.selected().map(CardId::value));
		assertThat(result.outcome()).as(id + " outcome").isEqualTo(expected.selected().isPresent() ? "SELECTED" : "NOT_DETERMINED");
		expected.tieBreak().ifPresent(basis -> {
			assertThat(result.selected().decidedBy()).as(id + " tie break").isEqualTo(basis.name());
			assertThat(result.selected().tiedUserCardIds()).as(id + " tie")
				.containsExactlyInAnyOrderElementsOf(expected.tie().stream().map(EngineEvaluationRunnerTest::userCardId).toList());
		});
		expected.monthlyTotal().ifPresent(total -> assertThat(compared(result, expected.selected().orElseThrow())
			.monthlyTotalBenefit()
			.amountWon()).as(id + " monthly total").isEqualTo(total.value()));
	}

	@Test
	void recordsOutOfScopeOutputsWithoutComparing() {
		for (String id : List.of("C09", "C14", "C20")) {
			EngineResult result = run(scenarios.get(id));
			StringBuilder line = new StringBuilder("[out-of-scope] ").append(id)
				.append(" (")
				.append(OUT_OF_SCOPE.get(id))
				.append(") outcome=")
				.append(result.outcome())
				.append(" selected=")
				.append(result.selected() == null ? null : result.selected().cardProductId());
			for (ComparedCard card : result.comparedCards()) {
				line.append(" | ").append(card.cardProductId()).append(' ').append(card.applicationStatus()).append(' ')
					.append(card.currentOrderBenefit().amountWon());
			}
			for (ExcludedCard card : result.excludedCards()) {
				line.append(" | ").append(card.cardProductId()).append(' ').append(card.reason()).append(' ')
					.append(card.applicationStatus());
			}
			System.out.println(line);
			assertThat(result.decisionDate()).isEqualTo(scenarios.get(id).input().order().date());
		}
	}

	private static EngineResult run(EvaluationScenario scenario) {
		RuleCatalog rules = scenario.ruleSnapshots().isEmpty() ? catalog : new RuleCatalog(scenario.ruleSnapshots());
		RecommendationEngine engine = new DefaultRecommendationEngine(rules);
		return engine.recommend(request(scenario, rules));
	}

	private static EngineRequest request(EvaluationScenario scenario, RuleCatalog rules) {
		RecommendationInput input = scenario.input();
		Order order = input.order();
		OrderInput orderInput = new OrderInput(order.orderId().orElse(null), order.merchantName().orElse(null),
				order.amount().value(), order.date(), order.time().orElse(null), order.merchantClass().code(),
				order.paymentMethod().name());
		List<HeldCardInput> cards = new ArrayList<>();
		for (HeldCard held : input.cards()) {
			LocalDate registered = held.registeredAt().knownValue();
			cards.add(new HeldCardInput(userCardId(held.cardId()), null, held.cardId().value(), null, false, "SYNTHETIC",
					StateInput.synthetic(registered, registered, registered), monthlyState(scenario, held, rules),
					NO_POLICY_EVENTS));
		}
		String representative = input.preference().representativeCard().map(EngineEvaluationRunnerTest::userCardId).orElse(null);
		return new EngineRequest(order.date(), orderInput, input.preference().mode().name(), representative, cards);
	}

	private static MonthlyStateInput monthlyState(EvaluationScenario scenario, HeldCard held, RuleCatalog rules) {
		RecommendationInput input = scenario.input();
		CardId card = held.cardId();
		CardMonthlyState mapped = input.states().stream().filter(state -> state.cardId().equals(card)).findFirst().orElseThrow();
		YearMonth orderMonth = YearMonth.from(input.order().date());
		LocalDate monthFrom = orderMonth.atDay(1);
		LocalDate monthTo = orderMonth.atEndOfMonth();
		long defaultUsed = mapped.otherBucketsUsed().knownValue().value();

		Map<String, Long> used = new LinkedHashMap<>();
		List<StateValue<ServiceSelection>> selections = mapped.serviceSelections();
		boolean selectable = false;
		if (rules.select(card, input.order().date()) instanceof RuleSelection.Selected selected) {
			selected.rule().limitBuckets().forEach(bucket -> used.put(bucket.id().value(), defaultUsed));
			selectable = selected.rule().services().stream().anyMatch(service -> service.requiresSelection());
		}
		mapped.usedBenefits().forEach((bucket, value) -> used
			.put(USED_KEY_TO_BUCKET.getOrDefault(bucket.toString(), bucket.toString()), value.knownValue().value()));
		List<BucketUsageInput> usages = used.entrySet()
			.stream()
			.map(entry -> new BucketUsageInput(entry.getKey(), StateInput.synthetic(entry.getValue(), monthFrom, monthTo)))
			.toList();

		YearMonth priorMonth = orderMonth.minusMonths(1);
		long prior = input.ledger()
			.transactions()
			.stream()
			.filter(transaction -> transaction.cardId().equals(card))
			.filter(transaction -> YearMonth.from(transaction.date()).equals(priorMonth))
			.filter(EngineEvaluationRunnerTest::isOrdinary)
			.mapToLong(transaction -> transaction.amount().value())
			.sum();

		String selectionStatus;
		List<ServiceSelectionInput> selectionInputs;
		if (!selections.isEmpty()) {
			selectionStatus = "SELECTED";
			selectionInputs = selections.stream()
				.map(value -> new ServiceSelectionInput(value.knownValue().service().map(Object::toString).orElseThrow(),
						value.knownValue().effectiveFrom(), "SYNTHETIC", null, true, null))
				.toList();
		}
		else if (selectable) {
			selectionStatus = "NONE";
			selectionInputs = List.of(new ServiceSelectionInput(null, held.registeredAt().knownValue(), "SYNTHETIC", null,
					true, null));
		}
		else {
			selectionStatus = "UNKNOWN";
			selectionInputs = List.of();
		}
		return new MonthlyStateInput(
				StateInput.synthetic(prior, priorMonth.atDay(1), priorMonth.atEndOfMonth()), usages,
				StateInput.synthetic(defaultUsed, monthFrom, monthTo), selectionStatus, selectionInputs);
	}

	private static boolean isOrdinary(LedgerTransaction transaction) {
		return transaction.nature().filter(nature -> nature == TransactionNature.ORDINARY).isPresent();
	}

	private static String userCardId(CardId card) {
		return "uc-" + card.value();
	}

	private static ComparedCard compared(EngineResult result, CardId card) {
		return result.comparedCards()
			.stream()
			.filter(compared -> compared.cardProductId().equals(card.value()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("card " + card + " is not compared"));
	}

	private static BenefitAmountOutput cardAmount(EngineResult result, CardId card) {
		return result.comparedCards()
			.stream()
			.filter(compared -> compared.cardProductId().equals(card.value()))
			.map(ComparedCard::currentOrderBenefit)
			.findFirst()
			.orElse(BenefitAmountOutput.needsConfirmation());
	}

	private static String cardStatus(EngineResult result, CardId card) {
		Optional<String> compared = result.comparedCards()
			.stream()
			.filter(candidate -> candidate.cardProductId().equals(card.value()))
			.map(ComparedCard::applicationStatus)
			.findFirst();
		return compared.orElseGet(() -> result.excludedCards()
			.stream()
			.filter(candidate -> card.value().equals(candidate.cardProductId()))
			.map(ExcludedCard::applicationStatus)
			.findFirst()
			.orElseThrow());
	}

	private static ServiceResult service(EngineResult result, ServiceRef ref) {
		String serviceId = SERVICE_KEY_TO_ID.getOrDefault(ref.serviceId().toString(), ref.serviceId().toString());
		return compared(result, ref.cardId()).services()
			.stream()
			.filter(service -> service.serviceId().equals(serviceId))
			.findFirst()
			.orElseThrow(() -> new AssertionError("no service " + ref));
	}

	// 확인 필요의 문구는 비교하지 않는다. 기대값의 확인 필요는 fixture가 금액을 null로 적은 것이다.
	private static void assertSameAmount(String description, BenefitAmountOutput actual, BenefitAmount expected) {
		assertThat(actual.amountWon() != null).as(description + " confirmed: " + actual).isEqualTo(expected.isConfirmed());
		if (expected.isConfirmed()) {
			assertThat(actual.amountWon()).as(description).isEqualTo(expected.confirmedWon().value());
		}
	}

}
