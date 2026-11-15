package com.beone.api.benefit.rule;

import java.util.Objects;

import com.beone.api.benefit.money.Won;

/**
 * 전월 실적이 {@code minimumPerformance} 이상일 때 적용하는 계산 방식.
 * 실적 조건이 없는 서비스는 0원부터 시작하는 구간 하나를 둔다.
 */
public record ServiceTier(Won minimumPerformance, RuleTerm<BenefitCalculation> calculation) {

	public ServiceTier {
		Objects.requireNonNull(minimumPerformance, "minimumPerformance");
		Objects.requireNonNull(calculation, "calculation");
		if (minimumPerformance.isNegative()) {
			throw new IllegalArgumentException("minimum performance must not be negative");
		}
	}

}
