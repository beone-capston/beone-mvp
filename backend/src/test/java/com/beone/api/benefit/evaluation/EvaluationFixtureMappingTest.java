package com.beone.api.benefit.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.card.LimitBucketId;
import com.beone.api.benefit.card.ServiceId;
import com.beone.api.benefit.card.TransactionNature;
import com.beone.api.benefit.input.LedgerEntryType;
import com.beone.api.benefit.input.LedgerTransaction;
import com.beone.api.benefit.money.BenefitAmount;
import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.result.ApplicationStatus;
import com.beone.api.benefit.result.SelectionBasis;
import com.beone.api.benefit.rule.CardRuleVersion;
import com.beone.api.benefit.rule.RuleCatalog;
import com.beone.api.benefit.rule.RuleSelection;
import com.beone.api.benefit.rule.VerificationStatus;
import com.beone.api.benefit.state.StateSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 평가 fixture의 모든 입력과 기대 결과를 모델로 표현할 수 있는지 확인한다(이슈 #15).
 * 혜택은 계산하지 않는다.
 */
class EvaluationFixtureMappingTest {

	private static String rawFixture;

	private static EvaluationFixture fixture;

	private static Map<String, EvaluationScenario> byId;

	@BeforeAll
	static void readFixture() throws IOException {
		rawFixture = Files.readString(EvaluationFixtureReader.FIXTURE);
		fixture = EvaluationFixtureReader.read(rawFixture);
		byId = Stream.concat(fixture.cases().stream(), fixture.mutations().stream())
			.collect(Collectors.toMap(EvaluationScenario::id, Function.identity()));
	}

	@Test
	void mapsAllTwentyCasesAndTenMutations() {
		assertThat(fixture.cases()).extracting(EvaluationScenario::id)
			.containsExactlyElementsOf(ids("C", 20));
		assertThat(fixture.mutations()).extracting(EvaluationScenario::id)
			.containsExactlyElementsOf(ids("M", 10));
		assertThat(fixture.status()).isEqualTo("DRAFT_CONDITIONAL_NOT_VERIFIED");
	}

	@Test
	void everyStatusCodeAndTieBreakInTheFixtureMapsToTheModel() {
		Set<ApplicationStatus> used = EnumSet.noneOf(ApplicationStatus.class);
		for (EvaluationScenario scenario : byId.values()) {
			used.addAll(scenario.expected().statusByCard().values());
			used.addAll(scenario.expected().serviceStatus().values());
		}
		assertThat(used).containsExactlyInAnyOrder(ApplicationStatus.APPLIED, ApplicationStatus.APPLIED_CAP_REMAINING,
				ApplicationStatus.LIMIT_EXHAUSTED, ApplicationStatus.NOT_APPLICABLE_EXCLUDED,
				ApplicationStatus.NOT_APPLICABLE_CATEGORY, ApplicationStatus.NOT_APPLICABLE_PERFORMANCE,
				ApplicationStatus.NOT_APPLICABLE_MIN_AMOUNT, ApplicationStatus.NOT_APPLICABLE_PAYMENT_METHOD,
				ApplicationStatus.RULE_EXPIRED);
		assertThat(byId.values()).flatExtracting(s -> s.expected().tieBreak().stream().toList())
			.containsExactlyInAnyOrder(SelectionBasis.CARD_ID_ASC, SelectionBasis.REPRESENTATIVE_CARD);
	}

	@Test
	void expectedCardsMatchHeldCards() {
		for (EvaluationScenario scenario : byId.values()) {
			Set<CardId> held = scenario.input()
				.cards()
				.stream()
				.map(card -> card.cardId())
				.collect(Collectors.toSet());
			assertThat(scenario.expected().currentOrderBenefits().keySet()).as(scenario.id()).isEqualTo(held);
		}
	}

	@Test
	void unconfirmedExpectedAmountIsNotZeroWon() {
		EvaluationScenario c02 = byId.get("C02");
		BenefitAmount amount = c02.expected().currentOrderBenefits().get(CardId.of("09271"));

		assertThat(amount.isConfirmed()).isFalse();
		assertThat(amount).isNotEqualTo(BenefitAmount.confirmed(0));
		assertThat(c02.expected().selected()).isEmpty();
		assertThat(byId.get("C03").expected().currentOrderBenefits().get(CardId.of("09271")))
			.isEqualTo(BenefitAmount.confirmed(0));
	}

	@Test
	void c02SnapshotIsRepresentedButNeverSelectedAndIsExpired() {
		EvaluationScenario c02 = byId.get("C02");
		CardRuleVersion snapshot = c02.ruleSnapshots().get(0);

		assertThat(snapshot.status()).isEqualTo(VerificationStatus.SYNTHETIC_TEST_SNAPSHOT);
		assertThat(snapshot.evaluationOnly()).isTrue();
		assertThat(snapshot.isVerified()).isFalse();
		RuleSelection selection = new RuleCatalog(c02.ruleSnapshots()).select(CardId.of("09271"),
				c02.input().order().date());
		assertThat(selection).isInstanceOfSatisfying(RuleSelection.Unavailable.class, unavailable -> {
			assertThat(unavailable.status()).isEqualTo(c02.expected().statusByCard().get(CardId.of("09271")));
			assertThat(unavailable.amount().isConfirmed()).isFalse();
		});
	}

	@Test
	void c02SnapshotIsNotSelectedEvenOnAValidDate() {
		CardRuleVersion snapshot = byId.get("C02").ruleSnapshots().get(0);

		assertThat(new RuleCatalog(List.of(snapshot)).select(CardId.of("09271"), LocalDate.of(2026, 10, 14)))
			.isInstanceOfSatisfying(RuleSelection.Unavailable.class,
					unavailable -> assertThat(unavailable.status()).isEqualTo(ApplicationStatus.NO_VERIFIED_RULE));
	}

	@Test
	void onlyC02StatesARuleSnapshotAndTheOthersRelyOnTheStatedDefault() {
		assertThat(byId.values()).filteredOn(s -> !s.ruleSnapshots().isEmpty())
			.extracting(EvaluationScenario::id)
			.containsExactly("C02");
		// ruleSnapshot이 없는 사례는 주문일에 유효한 후보 규칙을 전제한다.
		// fixture에 그 규칙이 정의돼 있지 않으므로 평가 하네스가 따로 제공해야 하며, 이 매핑은 만들지 않는다.
		assertThat(fixture.defaultNotes().get("ruleApplicability"))
			.contains("Cases without ruleSnapshot assume a rule snapshot that is valid on the order date");
	}

	@Test
	void cancellationKeepsNegativeAmountAndOriginalDateWithoutInventedFields() {
		LedgerTransaction cancellation = byId.get("C14")
			.input()
			.ledger()
			.transactions()
			.stream()
			.filter(tx -> tx.type() == LedgerEntryType.CANCELLATION)
			.findFirst()
			.orElseThrow();

		assertThat(cancellation.amount()).isEqualTo(Won.of(-100_000));
		assertThat(cancellation.date()).isEqualTo(LocalDate.of(2026, 9, 25));
		assertThat(cancellation.originalDate()).contains(LocalDate.of(2026, 8, 20));
		assertThat(cancellation.nature()).isEmpty();
		assertThat(cancellation.merchantClass()).isEmpty();
		assertThat(cancellation.paymentMethod()).isEmpty();
		assertThat(cancellation.time()).isEmpty();
	}

	@Test
	void taxLedgerEntryIsAnApprovalWithTaxNature() {
		List<LedgerTransaction> prior = byId.get("C09").input().ledger().transactions();

		assertThat(prior).extracting(LedgerTransaction::type).containsOnly(LedgerEntryType.APPROVAL);
		assertThat(prior).extracting(tx -> tx.nature().orElseThrow())
			.containsExactly(TransactionNature.ORDINARY, TransactionNature.TAX);
	}

	@Test
	void datesAreKeptWithoutInventedTimes() {
		for (EvaluationScenario scenario : byId.values()) {
			assertThat(scenario.input().order().time()).isEmpty();
			assertThat(scenario.input().plannedSpendings().items()).allSatisfy(item -> assertThat(item.time()).isEmpty());
		}
	}

	@Test
	void unlistedUsageIsTheFixtureDefaultAsSyntheticKnownZero() {
		var state = byId.get("C06").input().states().get(0);

		assertThat(state.usedBenefits()).isEmpty();
		assertThat(state.otherBucketsUsed().source()).isEqualTo(StateSource.SYNTHETIC);
		assertThat(state.otherBucketsUsed().knownValue()).isEqualTo(Won.ZERO);
		assertThat(state.priorPerformance()).as("derived from the ledger later").isEmpty();
	}

	@Test
	void listedUsageIsKeptPerLimitBucket() {
		var c10 = byId.get("C10").input().states().get(0);
		var m08 = byId.get("M08").input().states().get(0);

		assertThat(c10.usedBenefits().get(LimitBucketId.of("Great")).knownValue()).isEqualTo(Won.of(9_000));
		assertThat(m08.usedBenefits().get(LimitBucketId.of("CU_AREA")).knownValue()).isEqualTo(Won.of(4_000));
	}

	@Test
	void optInSelectionIsMappedWithItsEffectiveDate() {
		var selections = byId.get("C04").input().states().get(0).serviceSelections();

		assertThat(selections).singleElement().satisfies(selection -> {
			assertThat(selection.source()).isEqualTo(StateSource.SYNTHETIC);
			assertThat(selection.knownValue().service()).contains(ServiceId.of("FUEL"));
			assertThat(selection.knownValue().effectiveFrom()).isEqualTo(LocalDate.of(2026, 9, 1));
		});
		var c04 = byId.get("C04").expected();
		assertThat(c04.serviceBenefits()).containsEntry(new ServiceRef(CardId.of("09271"), ServiceId.of("FUEL")),
				BenefitAmount.confirmed(0));
		assertThat(c04.serviceStatus()).containsEntry(new ServiceRef(CardId.of("09271"), ServiceId.of("FUEL")),
				ApplicationStatus.NOT_APPLICABLE_CATEGORY);
	}

	@Test
	void tieAndRepresentativeCardAreMapped() {
		var c19 = byId.get("C19");
		var m01 = byId.get("M01");

		assertThat(c19.expected().tie()).containsExactly(CardId.of("01914"), CardId.of("09271"));
		assertThat(c19.expected().tieBreak()).contains(SelectionBasis.CARD_ID_ASC);
		assertThat(c19.input().preference().representativeCard()).isEmpty();
		assertThat(m01.input().preference().representativeCard()).contains(CardId.of("09271"));
		assertThat(m01.expected().tieBreak()).contains(SelectionBasis.REPRESENTATIVE_CARD);
		assertThat(m01.expected().selected()).contains(CardId.of("09271"));
	}

	@Test
	void plannedSpendingAndMonthlyTotalAreMapped() {
		var c20 = byId.get("C20");
		var m09 = byId.get("M09");

		assertThat(c20.input().plannedSpendings().items()).hasSize(1);
		assertThat(c20.expected().monthlyTotal()).contains(Won.of(10_100));
		assertThat(m09.input().plannedSpendings().items()).isEmpty();
		assertThat(m09.expected().monthlyTotal()).contains(Won.of(5_000));
	}

	@Test
	void ledgerOfACardNoLongerHeldIsKept() {
		var m10 = byId.get("M10").input();

		assertThat(m10.cards()).extracting(card -> card.cardId()).containsExactly(CardId.of("09271"));
		assertThat(m10.ledger().transactions()).extracting(LedgerTransaction::cardId)
			.containsExactly(CardId.of("01914"));
	}

	@Test
	void eachMutationChangesItsBaseInput() {
		for (EvaluationScenario mutation : fixture.mutations()) {
			EvaluationScenario base = byId.get(mutation.baseCaseId().orElseThrow());
			assertThat(mutation.input()).as(mutation.id()).isNotEqualTo(base.input());
			assertThat(mutation.sourceKeys()).as(mutation.id()).isEqualTo(base.sourceKeys());
		}
	}

	@Test
	void readingDoesNotChangeTheFixtureFile() throws IOException {
		EvaluationFixtureReader.read();

		assertThat(Files.readString(EvaluationFixtureReader.FIXTURE)).isEqualTo(rawFixture);
	}

	@Test
	void rejectsAnUnmappedFixtureKey() {
		String withExtraKey = rawFixture.replaceFirst("\"focus\": \"기본 적립\",",
				"\"focus\": \"기본 적립\", \"unexpected\": 1,");

		assertThat(withExtraKey).isNotEqualTo(rawFixture);
		assertThatThrownBy(() -> EvaluationFixtureReader.read(withExtraKey))
			.hasMessageContaining("unmapped key 'unexpected'");
	}

	@Test
	void rejectsAVerifiedStatusOnAFixtureSnapshot() {
		String promoted = rawFixture.replace("\"SYNTHETIC_TEST_SNAPSHOT\"", "\"VERIFIED\"");

		assertThat(promoted).isNotEqualTo(rawFixture);
		assertThatThrownBy(() -> EvaluationFixtureReader.read(promoted))
			.hasMessageContaining("VERIFIED rule cannot be evaluation-only");
	}

	@Test
	void rejectsAFractionalAmount() {
		String fractional = rawFixture.replaceFirst("\"amountWon\": 100000", "\"amountWon\": 100000.5");

		assertThat(fractional).isNotEqualTo(rawFixture);
		assertThatThrownBy(() -> EvaluationFixtureReader.read(fractional)).hasMessageContaining("integer won amount");
	}

	private static List<String> ids(String prefix, int count) {
		return IntStream.rangeClosed(1, count).mapToObj(i -> "%s%02d".formatted(prefix, i)).toList();
	}

}
