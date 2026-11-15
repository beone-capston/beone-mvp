package com.beone.api.benefit.primitive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.rule.BenefitCalculation;

/**
 * 합성 금액으로 산식만 확인한다. 실제 카드 약관의 계산 결과를 주장하지 않는다.
 */
class BenefitArithmeticTest {

	@Test
	void rateWithoutFraction() {
		// 20,000 × 5,000bp / 10,000 = 10,000원
		UnroundedWon result = BenefitArithmetic.rate(BenefitCalculation.rate(5_000), Won.of(20_000));

		assertThat(result).isEqualTo(UnroundedWon.exact(Won.of(10_000)));
		assertThat(result.hasFractionalWon()).isFalse();
	}

	@Test
	void rateKeepsFractionInsteadOfRounding() {
		// 1,234 × 100bp / 10,000 = 12.34원 → 정수 12원, 원 미만 3,400/10,000
		UnroundedWon result = BenefitArithmetic.rate(BenefitCalculation.rate(100), Won.of(1_234));

		assertThat(result).isEqualTo(new UnroundedWon(Won.of(12), 3_400));
		assertThat(result.hasFractionalWon()).isTrue();
	}

	@Test
	void smallestRateOnSmallestAmountIsOnlyFraction() {
		// 1 × 1bp / 10,000 = 0.0001원
		assertThat(BenefitArithmetic.rate(BenefitCalculation.rate(1), Won.of(1)))
			.isEqualTo(new UnroundedWon(Won.ZERO, 1));
	}

	@Test
	void zeroTargetGivesZero() {
		assertThat(BenefitArithmetic.rate(BenefitCalculation.rate(5_000), Won.ZERO))
			.isEqualTo(UnroundedWon.exact(Won.ZERO));
	}

	@Test
	void fullRateOnLargestAmountDoesNotOverflow() {
		// 100%면 대상 금액 그대로
		assertThat(BenefitArithmetic.rate(BenefitCalculation.rate(10_000), Won.of(Long.MAX_VALUE)))
			.isEqualTo(UnroundedWon.exact(Won.of(Long.MAX_VALUE)));
	}

	@Test
	void largeAmountKeepsExactFraction() {
		// Long 최댓값 9,223,372,036,854,775,807 × 1bp / 10,000
		// = 922,337,203,685,477 + 5,807/10,000
		assertThat(BenefitArithmetic.rate(BenefitCalculation.rate(1), Won.of(Long.MAX_VALUE)))
			.isEqualTo(new UnroundedWon(Won.of(922_337_203_685_477L), 5_807));
		// 9,223,372,036,854,775,807 × 3,333bp / 10,000
		// 몫 922,337,203,685,477 × 3,333 = 3,074,149,899,883,694,841
		// 나머지 5,807 × 3,333 = 19,354,731 → 1,935원 + 4,731/10,000
		assertThat(BenefitArithmetic.rate(BenefitCalculation.rate(3_333), Won.of(Long.MAX_VALUE)))
			.isEqualTo(new UnroundedWon(Won.of(3_074_149_899_883_696_776L), 4_731));
	}

	@Test
	void fixedAmountIgnoresTargetSize() {
		// D-10 형태의 합성 정액: 50,000원이든 80,000원이든 2,500원
		BenefitCalculation fixed = BenefitCalculation.fixed(2_500);

		assertThat(BenefitArithmetic.apply(fixed, Won.of(50_000))).isEqualTo(UnroundedWon.exact(Won.of(2_500)));
		assertThat(BenefitArithmetic.apply(fixed, Won.of(80_000))).isEqualTo(UnroundedWon.exact(Won.of(2_500)));
	}

	@Test
	void fixedAmountIsNotClampedToTarget() {
		// 정액을 결제 금액으로 줄일지는 정해지지 않았으므로 1,000원 대상에도 2,500원 그대로 둔다.
		assertThat(BenefitArithmetic.apply(BenefitCalculation.fixed(2_500), Won.of(1_000)))
			.isEqualTo(UnroundedWon.exact(Won.of(2_500)));
	}

	@Test
	void applyDispatchesRate() {
		assertThat(BenefitArithmetic.apply(BenefitCalculation.rate(100), Won.of(1_234)))
			.isEqualTo(new UnroundedWon(Won.of(12), 3_400));
	}

	@Test
	void capLimitsOnlyAmountAboveMaximum() {
		Optional<Won> cap = Optional.of(Won.of(20_000));

		assertThat(BenefitArithmetic.capTargetAmount(Won.of(30_000), cap)).isEqualTo(Won.of(20_000));
		assertThat(BenefitArithmetic.capTargetAmount(Won.of(20_000), cap)).isEqualTo(Won.of(20_000));
		assertThat(BenefitArithmetic.capTargetAmount(Won.of(19_999), cap)).isEqualTo(Won.of(19_999));
	}

	@Test
	void capMustBePositive() {
		// 0원·음수 상한을 그대로 쓰면 대상 금액이 0원이나 음수가 된다.
		assertThatThrownBy(() -> BenefitArithmetic.capTargetAmount(Won.of(100), Optional.of(Won.of(-1))))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> BenefitArithmetic.capTargetAmount(Won.of(100), Optional.of(Won.ZERO)))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(BenefitArithmetic.capTargetAmount(Won.of(100), Optional.of(Won.of(1)))).isEqualTo(Won.of(1));
	}

	@Test
	void noCapMeansWholeAmount() {
		assertThat(BenefitArithmetic.capTargetAmount(Won.of(Long.MAX_VALUE), Optional.empty()))
			.isEqualTo(Won.of(Long.MAX_VALUE));
	}

	@Test
	void cappedRateCombination() {
		// 상한 2만원, 35%: 30,000 → 20,000 × 3,500bp / 10,000 = 7,000원
		Won target = BenefitArithmetic.capTargetAmount(Won.of(30_000), Optional.of(Won.of(20_000)));

		assertThat(BenefitArithmetic.apply(BenefitCalculation.rate(3_500), target))
			.isEqualTo(UnroundedWon.exact(Won.of(7_000)));
	}

	@Test
	void sameInputGivesSameResult() {
		BenefitCalculation rate = BenefitCalculation.rate(100);

		assertThat(BenefitArithmetic.apply(rate, Won.of(1_234))).isEqualTo(BenefitArithmetic.apply(rate, Won.of(1_234)));
	}

	@Test
	void rejectsNegativeOrMissingInput() {
		assertThatThrownBy(() -> BenefitArithmetic.rate(BenefitCalculation.rate(100), Won.of(-1)))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> BenefitArithmetic.apply(BenefitCalculation.fixed(2_500), Won.of(-1)))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> BenefitArithmetic.capTargetAmount(Won.of(-1), Optional.empty()))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> BenefitArithmetic.apply(null, Won.ZERO)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> BenefitArithmetic.rate(BenefitCalculation.rate(100), null))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> BenefitArithmetic.capTargetAmount(Won.ZERO, null))
			.isInstanceOf(NullPointerException.class);
	}

}
