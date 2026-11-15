package com.beone.api.benefit.input;

import static com.beone.api.benefit.input.InputTestData.emptyLedger;
import static com.beone.api.benefit.input.InputTestData.held;
import static com.beone.api.benefit.input.InputTestData.input;
import static com.beone.api.benefit.input.InputTestData.state;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.card.LimitBucketId;
import com.beone.api.benefit.card.TransactionNature;
import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.state.BasisPeriod;
import com.beone.api.benefit.state.StateSource;
import com.beone.api.benefit.state.StateValue;
import org.junit.jupiter.api.Test;

class RecommendationInputTest {

	@Test
	void acceptsLedgerOfACardThatIsNotHeld() {
		Ledger ledger = new Ledger(LedgerSource.SIMULATION,
				List.of(new LedgerTransaction(CardId.of("01914"), LocalDate.of(2026, 9, 30), Optional.empty(),
						Won.of(300_000), LedgerEntryType.APPROVAL, Optional.of(TransactionNature.ORDINARY),
						Optional.empty(), Optional.empty(), Optional.empty())),
				List.of());

		RecommendationInput input = input(List.of(held("09271")), List.of(state("09271")), ledger,
				SelectionPreference.autoRecommend());

		assertThat(input.ledger().transactions()).hasSize(1);
	}

	@Test
	void rejectsEmptyOrDuplicateCards() {
		assertThatThrownBy(() -> input(List.of(), List.of(), emptyLedger(), SelectionPreference.autoRecommend()))
			.hasMessageContaining("at least one");
		assertThatThrownBy(() -> input(List.of(held("09271"), held("09271")), List.of(), emptyLedger(),
				SelectionPreference.autoRecommend()))
			.hasMessageContaining("held twice");
	}

	@Test
	void rejectsStateForCardNotHeldOrGivenTwice() {
		assertThatThrownBy(() -> input(List.of(held("09271")), List.of(state("01914")), emptyLedger(),
				SelectionPreference.autoRecommend()))
			.hasMessageContaining("not held");
		assertThatThrownBy(() -> input(List.of(held("09271")), List.of(state("09271"), state("09271")), emptyLedger(),
				SelectionPreference.autoRecommend()))
			.hasMessageContaining("twice");
	}

	@Test
	void representativeCardMustBeHeld() {
		SelectionPreference preference = new SelectionPreference(SelectionMode.AUTO_RECOMMEND,
				Optional.of(CardId.of("01914")));

		assertThatThrownBy(() -> input(List.of(held("09271")), List.of(), emptyLedger(), preference))
			.hasMessageContaining("representative card");
	}

	@Test
	void fixedModeNeedsARepresentativeCard() {
		assertThatThrownBy(() -> new SelectionPreference(SelectionMode.FIXED_REPRESENTATIVE, Optional.empty()))
			.hasMessageContaining("representative card");
	}

	@Test
	void cardHoldingCannotBeInferredOrUnknown() {
		var registered = StateValue.synthetic(LocalDate.of(2026, 1, 1), BasisPeriod.day(InputTestData.ORDER_DATE));

		assertThatThrownBy(() -> new HeldCard(CardId.of("09271"), StateSource.UNKNOWN, registered))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new HeldCard(CardId.of("09271"), StateSource.INFERRED, registered))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void monthlyStateKeepsUnknownUsageApartFromZeroAndRejectsNegativeUsage() {
		BasisPeriod october = BasisPeriod.month(YearMonth.of(2026, 10));
		CardMonthlyState unknownOthers = new CardMonthlyState(CardId.of("09174"), Optional.empty(),
				Map.of(LimitBucketId.of("Great"), StateValue.unknown(october, null)), StateValue.unknown(october, null),
				List.of());

		assertThat(unknownOthers.otherBucketsUsed().isKnown()).isFalse();
		assertThat(unknownOthers.usedBenefits().get(LimitBucketId.of("Great")).isKnown()).isFalse();
		assertThatThrownBy(() -> new CardMonthlyState(CardId.of("09174"), Optional.empty(),
				Map.of(LimitBucketId.of("Great"), StateValue.synthetic(Won.of(-1), october)),
				StateValue.unknown(october, null), List.of()))
			.hasMessageContaining("negative");
	}

	@Test
	void cardIdKeepsLeadingZerosAndOrdersAsString() {
		assertThat(CardId.of("01914").value()).isEqualTo("01914");
		assertThat(CardId.of("01914")).isLessThan(CardId.of("09271"));
		assertThat(CardId.of("10")).isLessThan(CardId.of("9"));
		assertThatThrownBy(() -> CardId.of(" 09271")).isInstanceOf(IllegalArgumentException.class);
	}

}
