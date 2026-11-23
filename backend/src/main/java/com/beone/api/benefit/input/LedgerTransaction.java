package com.beone.api.benefit.input;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.Optional;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.card.MerchantClass;
import com.beone.api.benefit.card.PaymentMethod;
import com.beone.api.benefit.card.TransactionNature;
import com.beone.api.benefit.money.Won;

/**
 * 원장 거래 한 건(PRD FR-02). 출처가 주지 않은 값은 비워 두고 기본값으로 채우지 않는다.
 *
 * @param cardId 카드 상품 식별자
 * @param date 승인일. 취소는 취소 접수일
 * @param time 거래 시각. 출처가 주지 않으면 빈 값
 * @param amount 거래 금액
 * @param type 승인·취소 구분
 * @param nature 거래 성격. 출처가 주지 않으면 빈 값
 * @param merchantClass 업종 분류. 출처가 주지 않으면 빈 값
 * @param paymentMethod 결제 방식. 출처가 주지 않으면 빈 값
 * @param originalDate 취소된 원거래의 날짜. 취소에만 쓴다
 */
public record LedgerTransaction(CardId cardId, LocalDate date, Optional<LocalTime> time, Won amount,
		LedgerEntryType type, Optional<TransactionNature> nature, Optional<MerchantClass> merchantClass,
		Optional<PaymentMethod> paymentMethod, Optional<LocalDate> originalDate) {

	public LedgerTransaction {
		Objects.requireNonNull(cardId, "cardId");
		Objects.requireNonNull(date, "date");
		Objects.requireNonNull(time, "time");
		Objects.requireNonNull(amount, "amount");
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(nature, "nature");
		Objects.requireNonNull(merchantClass, "merchantClass");
		Objects.requireNonNull(paymentMethod, "paymentMethod");
		Objects.requireNonNull(originalDate, "originalDate");
		switch (type) {
			case APPROVAL -> {
				if (!amount.isPositive()) {
					throw new IllegalArgumentException("approval amount must be positive: " + amount.value());
				}
				if (originalDate.isPresent()) {
					throw new IllegalArgumentException("an approval has no original date");
				}
			}
			case CANCELLATION -> {
				if (!amount.isNegative()) {
					throw new IllegalArgumentException("cancellation amount must be negative: " + amount.value());
				}
				if (originalDate.isPresent() && originalDate.get().isAfter(date)) {
					throw new IllegalArgumentException("cancellation is received before its original transaction");
				}
			}
		}
	}

}
