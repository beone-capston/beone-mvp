package com.beone.api.benefit.primitive;

import java.util.Objects;
import java.util.Optional;

import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.rule.BenefitCalculation;

/**
 * 서비스 한 건의 혜택 산식. 조건 판정·한도·중복 적용·원 미만 처리는 하지 않는다.
 *
 * <p>
 * 입력 금액은 이미 확인된 값이어야 한다. 모르는 금액을 0원으로 바꿔 넣지 않는다(D-05).
 * 취소(음수 금액)의 혜택 처리는 정해지지 않았으므로 음수 금액은 거부한다.
 */
public final class BenefitArithmetic {

	private BenefitArithmetic() {
	}

	/**
	 * 건당 할인 대상 금액 상한을 적용한다. 상한을 넘는 부분은 할인 대상이 아니다({@code BenefitService.maximumTargetAmount}).
	 * 상한이 없으면 거래 금액 전체가 대상이다. 상한은 규칙 모델과 같이 0원보다 커야 한다.
	 */
	public static Won capTargetAmount(Won transactionAmount, Optional<Won> maximumTargetAmount) {
		requireNotNegative(transactionAmount, "transaction amount");
		Objects.requireNonNull(maximumTargetAmount, "maximumTargetAmount");
		maximumTargetAmount.ifPresent(cap -> {
			if (!cap.isPositive()) {
				throw new IllegalArgumentException("maximum target amount must be positive: " + cap.value());
			}
		});
		return maximumTargetAmount.filter(cap -> cap.compareTo(transactionAmount) < 0).orElse(transactionAmount);
	}

	/**
	 * 계산 방식에 따라 대상 금액의 혜택을 원 미만까지 정확히 계산한다.
	 */
	public static UnroundedWon apply(BenefitCalculation calculation, Won targetAmount) {
		Objects.requireNonNull(calculation, "calculation");
		return switch (calculation) {
			case BenefitCalculation.Rate rate -> rate(rate, targetAmount);
			case BenefitCalculation.FixedAmount fixed -> fixed(fixed, targetAmount);
		};
	}

	/**
	 * 정률 혜택: {@code targetAmount × basisPoints / 10,000}.
	 *
	 * <p>
	 * 곱셈 넘침을 피하려고 대상 금액을 10,000 단위 몫과 나머지로 나눠 계산한다. 요율이 100% 이하이므로 결과는 대상 금액을 넘지 않는다.
	 */
	public static UnroundedWon rate(BenefitCalculation.Rate rate, Won targetAmount) {
		Objects.requireNonNull(rate, "rate");
		requireNotNegative(targetAmount, "target amount");
		long units = UnroundedWon.UNITS_PER_WON;
		long quotient = targetAmount.value() / units;
		long remainder = targetAmount.value() % units;
		long remainderProduct = remainder * rate.basisPoints();
		long whole = quotient * rate.basisPoints() + remainderProduct / units;
		return new UnroundedWon(Won.of(whole), (int) (remainderProduct % units));
	}

	/**
	 * 정액 혜택: 대상 거래 한 건당 정해진 금액({@code BenefitCalculation.FixedAmount}). 대상 금액으로 금액이 바뀌지 않는다(D-10).
	 * 최소 금액 등 적용 조건은 {@link ServiceConditions}에서 따로 판정한다.
	 *
	 * <p>
	 * {@code targetAmount}는 음수인지 확인하는 데만 쓴다. 결과가 대상 금액보다 클 수 있으며, 정액을 결제 금액으로 줄일지는
	 * 정해지지 않았으므로 여기서 줄이지 않는다.
	 */
	public static UnroundedWon fixed(BenefitCalculation.FixedAmount fixed, Won targetAmount) {
		Objects.requireNonNull(fixed, "fixed");
		requireNotNegative(targetAmount, "target amount");
		return UnroundedWon.exact(fixed.amount());
	}

	private static void requireNotNegative(Won amount, String name) {
		Objects.requireNonNull(amount, name);
		if (amount.isNegative()) {
			throw new IllegalArgumentException(name + " must not be negative: " + amount.value());
		}
	}

}
