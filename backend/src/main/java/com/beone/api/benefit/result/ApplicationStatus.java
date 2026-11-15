package com.beone.api.benefit.result;

import com.beone.api.benefit.money.BenefitAmount;

/**
 * 카드 또는 서비스의 혜택 적용 여부와 사유.
 * 평가 fixture 코드와 같고, {@code NO_VERIFIED_RULE}은 사례집, {@code NEEDS_CONFIRMATION}은 D-05의 {@code 확인 필요}에 근거한다.
 */
public enum ApplicationStatus {

	/** 0원보다 큰 혜택이 적용됨. */
	APPLIED(AmountShape.POSITIVE),

	/** 0원보다 큰 혜택이 적용됐지만 잔여 한도 때문에 산식 금액보다 줄어듦. */
	APPLIED_CAP_REMAINING(AmountShape.POSITIVE),

	/** 해당 한도를 이미 모두 사용함. */
	LIMIT_EXHAUSTED(AmountShape.ZERO),

	/** 약관상 제외 거래. */
	NOT_APPLICABLE_EXCLUDED(AmountShape.ZERO),

	/** 대상 업종이 아님. */
	NOT_APPLICABLE_CATEGORY(AmountShape.ZERO),

	/** 전월 실적이 기준에 못 미침. */
	NOT_APPLICABLE_PERFORMANCE(AmountShape.ZERO),

	/** 건당 최소 금액에 못 미침. */
	NOT_APPLICABLE_MIN_AMOUNT(AmountShape.ZERO),

	/** 결제 방식이 대상이 아님. */
	NOT_APPLICABLE_PAYMENT_METHOD(AmountShape.ZERO),

	/** 택1 선택형 서비스인데 다른 서비스를 선택했거나 선택 없음({@code NONE})이 입력됨(D-18 K1). */
	NOT_APPLICABLE_SELECTION(AmountShape.ZERO),

	/** 판정일에 유효한 규칙 버전이 없음. */
	RULE_EXPIRED(AmountShape.UNCONFIRMED),

	/** 판정일에 유효한 버전 중 {@code VERIFIED}가 없거나 카드에 규칙이 없음. */
	NO_VERIFIED_RULE(AmountShape.UNCONFIRMED),

	/** 필수 상태나 규칙 조건이 미확정임(D-05). */
	NEEDS_CONFIRMATION(AmountShape.UNCONFIRMED);

	private final AmountShape amountShape;

	ApplicationStatus(AmountShape amountShape) {
		this.amountShape = amountShape;
	}

	/**
	 * 상태와 금액이 맞는지 확인한다.
	 * 적용 상태는 0원보다 큰 확정 금액, 미적용 상태는 확정 0원, 규칙·상태 공백은 미확정 금액이어야 한다.
	 */
	public boolean isConsistentWith(BenefitAmount amount) {
		return switch (amountShape) {
			case POSITIVE -> amount.isConfirmed() && amount.confirmedWon().isPositive();
			case ZERO -> amount.isConfirmed() && amount.confirmedWon().isZero();
			case UNCONFIRMED -> !amount.isConfirmed();
		};
	}

	public boolean isApplied() {
		return amountShape == AmountShape.POSITIVE;
	}

	private enum AmountShape {

		POSITIVE, ZERO, UNCONFIRMED

	}

}
