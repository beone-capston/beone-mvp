package com.beone.api.benefit.rule;

import java.util.Objects;

import com.beone.api.benefit.money.Won;

/**
 * 전월 실적이 {@code minimumPerformance} 이상일 때 적용하는 월 한도액.
 */
public record LimitTier(Won minimumPerformance, Won monthlyCap) {

	public LimitTier {
		Objects.requireNonNull(minimumPerformance, "minimumPerformance");
		Objects.requireNonNull(monthlyCap, "monthlyCap");
		if (minimumPerformance.isNegative()) {
			throw new IllegalArgumentException("minimum performance must not be negative");
		}
		if (!monthlyCap.isPositive()) {
			throw new IllegalArgumentException("monthly cap must be positive: " + monthlyCap.value());
		}
	}

}
