package com.beone.api.benefit.rule;

import java.util.Objects;

import com.beone.api.benefit.money.Won;

/**
 * 혜택 구간의 계산 방식. 베이시스 포인트 요율 또는 정액.
 */
public sealed interface BenefitCalculation {

	/** 100%를 베이시스 포인트로 표현한 값. */
	int FULL_RATE_BASIS_POINTS = 10_000;

	static Rate rate(int basisPoints) {
		return new Rate(basisPoints);
	}

	static FixedAmount fixed(long won) {
		return new FixedAmount(Won.of(won));
	}

	/**
	 * 베이시스 포인트 요율(예: 1% = 100, 50% = 5,000). 부동소수점을 쓰지 않도록 정수로 둔다.
	 */
	record Rate(int basisPoints) implements BenefitCalculation {

		public Rate {
			if (basisPoints <= 0 || basisPoints > FULL_RATE_BASIS_POINTS) {
				throw new IllegalArgumentException("rate must be within 1..10000 basis points: " + basisPoints);
			}
		}

	}

	/** 대상 거래 한 건당 정액(예: 2,500원, D-10). */
	record FixedAmount(Won amount) implements BenefitCalculation {

		public FixedAmount {
			Objects.requireNonNull(amount, "amount");
			if (!amount.isPositive()) {
				throw new IllegalArgumentException("fixed benefit must be positive: " + amount.value());
			}
		}

	}

}
