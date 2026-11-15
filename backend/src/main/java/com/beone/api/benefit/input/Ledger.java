package com.beone.api.benefit.input;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.rule.TriggerEvidence;
import com.beone.api.benefit.state.BasisPeriod;

/**
 * 한 출처의 원장 거래와 완전하다고 선언된 기간.
 * 보유하지 않은 카드의 거래도 받아 두며, 비교할 때는 제외한다(사례집 M10).
 */
public record Ledger(LedgerSource source, List<LedgerTransaction> transactions, List<LedgerCoverage> completePeriods) {

	public Ledger {
		Objects.requireNonNull(source, "source");
		transactions = List.copyOf(transactions);
		completePeriods = List.copyOf(completePeriods);
	}

	/**
	 * 기간의 모든 날짜가 그 카드의 완전 기간 선언 안에 있으면 참.
	 * 인접한 선언은 이어서 보고, 선언되지 않은 날이 하나라도 있으면 불완전하다.
	 */
	public boolean isComplete(CardId cardId, BasisPeriod period) {
		for (LocalDate day = period.from(); !day.isAfter(period.to()); day = day.plusDays(1)) {
			LocalDate current = day;
			boolean covered = completePeriods.stream()
				.anyMatch(coverage -> coverage.cardId().equals(cardId) && coverage.period().contains(current));
			if (!covered) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 취소 실적 귀속월이 미확정일 때 쓰는 {@link com.beone.api.benefit.rule.TermTrigger#CANCELLATION_AFFECTING_PERFORMANCE_WINDOW} 근거.
	 *
	 * <ol>
	 * <li>실적 기간에 접수됐거나 실적 기간의 거래를 취소한 건이 있으면 {@code PRESENT}</li>
	 * <li>실적 기간 시작일부터 {@code asOf}까지 원장이 완전하지 않으면 {@code UNDETERMINED}. 나중에 접수된 취소가 실적 기간 거래의 취소일 수 있다.</li>
	 * <li>실적 기간 뒤에 접수된 취소 중 원거래일을 모르는 건이 있으면 {@code UNDETERMINED}</li>
	 * <li>그 밖에는 {@code CONFIRMED_ABSENT}</li>
	 * </ol>
	 *
	 * @param asOf 결과에 영향을 줄 수 있는 마지막 날짜. 실적 기간 종료일보다 앞설 수 없다
	 */
	public TriggerEvidence cancellationEvidence(CardId cardId, BasisPeriod window, LocalDate asOf) {
		Objects.requireNonNull(cardId, "cardId");
		Objects.requireNonNull(window, "window");
		Objects.requireNonNull(asOf, "asOf");
		if (asOf.isBefore(window.to())) {
			throw new IllegalArgumentException("asOf " + asOf + " is before the window end " + window.to());
		}
		List<LedgerTransaction> cancellations = transactions.stream()
			.filter(tx -> tx.cardId().equals(cardId) && tx.type() == LedgerEntryType.CANCELLATION)
			.toList();
		boolean touchesWindow = cancellations.stream()
			.anyMatch(tx -> window.contains(tx.date()) || tx.originalDate().map(window::contains).orElse(false));
		if (touchesWindow) {
			return TriggerEvidence.PRESENT;
		}
		if (!isComplete(cardId, BasisPeriod.of(window.from(), asOf))) {
			return TriggerEvidence.UNDETERMINED;
		}
		boolean unknownOriginAfterWindow = cancellations.stream()
			.anyMatch(tx -> tx.date().isAfter(window.to()) && !tx.date().isAfter(asOf) && tx.originalDate().isEmpty());
		return unknownOriginAfterWindow ? TriggerEvidence.UNDETERMINED : TriggerEvidence.CONFIRMED_ABSENT;
	}

}
