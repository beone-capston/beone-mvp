package com.beone.api.ledger.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Objects;

/**
 * 원장에 남길 모의 승인·취소 행 하나(docs/api/ledger.md 항목 형식). 출처는 항상 {@link LedgerSource#SIMULATION}이다.
 *
 * <p>거래 성격({@code transactionNature})은 1차 모의 거래에서 받지 않으므로 두지 않는다(저장 값 {@code null}).
 *
 * @param userId 사용자
 * @param userCardId 보유 카드 DB 번호
 * @param entryType 승인·취소
 * @param amountWon 승인은 양수, 취소는 음수
 * @param transactionDate 거래일
 * @param transactionTime 거래 시각. 없으면 {@code null}(00:00을 만들지 않음)
 * @param merchantClass 가맹점·업종 분류 코드
 * @param paymentMethod 결제 방식 이름
 * @param merchantName 가맹점 이름. 없으면 {@code null}
 * @param originalLedgerId 취소가 가리키는 승인 행 DB 번호. 승인이면 {@code null}
 * @param simulatedPaymentId 모의 결제 DB 번호
 * @param attributionMonth 귀속월
 * @param attributionStatus 귀속월 확정 여부
 * @param recordedAt 기록 시각(서울)
 */
public record NewLedgerEntry(long userId, long userCardId, LedgerEntryType entryType, long amountWon,
		LocalDate transactionDate, LocalTime transactionTime, String merchantClass, String paymentMethod,
		String merchantName, Long originalLedgerId, long simulatedPaymentId, YearMonth attributionMonth,
		AttributionStatus attributionStatus, LocalDateTime recordedAt) {

	public NewLedgerEntry {
		Objects.requireNonNull(entryType, "entryType");
		Objects.requireNonNull(transactionDate, "transactionDate");
		Objects.requireNonNull(merchantClass, "merchantClass");
		Objects.requireNonNull(paymentMethod, "paymentMethod");
		Objects.requireNonNull(attributionMonth, "attributionMonth");
		Objects.requireNonNull(attributionStatus, "attributionStatus");
		Objects.requireNonNull(recordedAt, "recordedAt");
		if (entryType == LedgerEntryType.APPROVAL && (amountWon < 1 || originalLedgerId != null)) {
			throw new IllegalArgumentException("an approval has a positive amount and no original row");
		}
		if (entryType == LedgerEntryType.CANCELLATION && (amountWon > -1 || originalLedgerId == null)) {
			throw new IllegalArgumentException("a cancellation has a negative amount and an original row");
		}
	}

}
