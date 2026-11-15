package com.beone.api.benefit.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.card.TransactionNature;
import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.rule.TriggerEvidence;
import com.beone.api.benefit.state.BasisPeriod;
import org.junit.jupiter.api.Test;

class LedgerTest {

	private static final CardId CARD = CardId.of("01664");

	private static final LocalDate SEP_25 = LocalDate.of(2026, 9, 25);

	private static final LocalDate OCT_15 = LocalDate.of(2026, 10, 15);

	private static final BasisPeriod SEPTEMBER = BasisPeriod.month(YearMonth.of(2026, 9));

	@Test
	void cancellationIsNegativeAndKeepsItsOriginalDate() {
		LedgerTransaction cancellation = transaction(-100_000, LedgerEntryType.CANCELLATION,
				Optional.of(LocalDate.of(2026, 8, 20)));

		assertThat(cancellation.amount().isNegative()).isTrue();
		assertThat(cancellation.originalDate()).contains(LocalDate.of(2026, 8, 20));
		assertThat(cancellation.paymentMethod()).isEmpty();
	}

	@Test
	void approvalMustBePositiveAndCancellationNegative() {
		assertThatThrownBy(() -> transaction(0, LedgerEntryType.APPROVAL, Optional.empty())).hasMessageContaining("positive");
		assertThatThrownBy(() -> transaction(-1, LedgerEntryType.APPROVAL, Optional.empty()))
			.hasMessageContaining("positive");
		assertThatThrownBy(() -> transaction(100_000, LedgerEntryType.CANCELLATION, Optional.empty()))
			.hasMessageContaining("negative");
		assertThatThrownBy(() -> transaction(0, LedgerEntryType.CANCELLATION, Optional.empty()))
			.hasMessageContaining("negative");
	}

	@Test
	void originalDateBelongsOnlyToAnEarlierCancelledTransaction() {
		assertThatThrownBy(() -> transaction(100_000, LedgerEntryType.APPROVAL, Optional.of(LocalDate.of(2026, 8, 20))))
			.hasMessageContaining("no original date");
		assertThatThrownBy(() -> transaction(-100_000, LedgerEntryType.CANCELLATION,
				Optional.of(LocalDate.of(2026, 9, 26))))
			.hasMessageContaining("before its original");
	}

	@Test
	void completenessIsKnownOnlyForDeclaredPeriods() {
		BasisPeriod september = BasisPeriod.month(YearMonth.of(2026, 9));
		Ledger ledger = new Ledger(LedgerSource.SIMULATION,
				List.of(transaction(300_000, LedgerEntryType.APPROVAL, Optional.empty())),
				List.of(new LedgerCoverage(CARD, september)));

		assertThat(ledger.isComplete(CARD, september)).isTrue();
		assertThat(ledger.isComplete(CARD, BasisPeriod.month(YearMonth.of(2026, 8)))).isFalse();
		assertThat(ledger.isComplete(CardId.of("01914"), september)).isFalse();
	}

	@Test
	void adjacentDeclaredPeriodsCombineForCompleteness() {
		Ledger ledger = new Ledger(LedgerSource.SIMULATION, List.of(), List.of(
				new LedgerCoverage(CARD, BasisPeriod.month(YearMonth.of(2026, 9))),
				new LedgerCoverage(CARD, BasisPeriod.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15)))));

		assertThat(ledger.isComplete(CARD, BasisPeriod.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 15)))).isTrue();
		assertThat(ledger.isComplete(CARD, BasisPeriod.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 16)))).isFalse();
	}

	@Test
	void cancellationInOrForTheWindowIsPresentEvenWithIncompleteLedger() {
		Ledger receivedInWindow = ledger(List.of(cancellation(SEP_25, Optional.of(LocalDate.of(2026, 8, 20)))), List.of());
		Ledger cancelsWindowSale = ledger(
				List.of(cancellation(LocalDate.of(2026, 10, 3), Optional.of(LocalDate.of(2026, 9, 10)))), List.of());

		assertThat(receivedInWindow.cancellationEvidence(CARD, SEPTEMBER, OCT_15)).isEqualTo(TriggerEvidence.PRESENT);
		assertThat(cancelsWindowSale.cancellationEvidence(CARD, SEPTEMBER, OCT_15)).isEqualTo(TriggerEvidence.PRESENT);
	}

	@Test
	void absenceIsConfirmedOnlyWhenLedgerIsCompleteThroughAsOf() {
		LedgerCoverage september = new LedgerCoverage(CARD, SEPTEMBER);
		LedgerCoverage october = new LedgerCoverage(CARD, BasisPeriod.of(LocalDate.of(2026, 10, 1), OCT_15));

		assertThat(ledger(List.of(), List.of()).cancellationEvidence(CARD, SEPTEMBER, OCT_15))
			.isEqualTo(TriggerEvidence.UNDETERMINED);
		assertThat(ledger(List.of(), List.of(september)).cancellationEvidence(CARD, SEPTEMBER, OCT_15))
			.as("a cancellation received in October could still belong to September")
			.isEqualTo(TriggerEvidence.UNDETERMINED);
		assertThat(ledger(List.of(), List.of(september, october)).cancellationEvidence(CARD, SEPTEMBER, OCT_15))
			.isEqualTo(TriggerEvidence.CONFIRMED_ABSENT);
	}

	@Test
	void laterCancellationWithUnknownOriginalIsUndetermined() {
		Ledger ledger = ledger(List.of(cancellation(LocalDate.of(2026, 10, 3), Optional.empty())),
				List.of(new LedgerCoverage(CARD, BasisPeriod.of(LocalDate.of(2026, 9, 1), OCT_15))));

		assertThat(ledger.cancellationEvidence(CARD, SEPTEMBER, OCT_15)).isEqualTo(TriggerEvidence.UNDETERMINED);
	}

	@Test
	void cancellationOfAnotherCardOrBeforeTheWindowDoesNotCount() {
		LedgerTransaction otherCard = new LedgerTransaction(CardId.of("01914"), SEP_25, Optional.empty(),
				Won.of(-10_000), LedgerEntryType.CANCELLATION, Optional.empty(), Optional.empty(), Optional.empty(),
				Optional.empty());
		Ledger ledger = ledger(List.of(otherCard, cancellation(LocalDate.of(2026, 8, 25), Optional.empty())),
				List.of(new LedgerCoverage(CARD, BasisPeriod.of(LocalDate.of(2026, 9, 1), OCT_15))));

		assertThat(ledger.cancellationEvidence(CARD, SEPTEMBER, OCT_15)).isEqualTo(TriggerEvidence.CONFIRMED_ABSENT);
		assertThatThrownBy(() -> ledger.cancellationEvidence(CARD, SEPTEMBER, LocalDate.of(2026, 9, 29)))
			.hasMessageContaining("before the window end");
	}

	private static Ledger ledger(List<LedgerTransaction> transactions, List<LedgerCoverage> coverage) {
		return new Ledger(LedgerSource.SIMULATION, transactions, coverage);
	}

	private static LedgerTransaction cancellation(LocalDate received, Optional<LocalDate> originalDate) {
		return new LedgerTransaction(CARD, received, Optional.empty(), Won.of(-100_000), LedgerEntryType.CANCELLATION,
				Optional.empty(), Optional.empty(), Optional.empty(), originalDate);
	}

	private static LedgerTransaction transaction(long amount, LedgerEntryType type, Optional<LocalDate> originalDate) {
		return new LedgerTransaction(CARD, SEP_25, Optional.empty(), Won.of(amount), type,
				type == LedgerEntryType.APPROVAL ? Optional.of(TransactionNature.ORDINARY) : Optional.empty(),
				Optional.empty(), Optional.empty(), originalDate);
	}

}
