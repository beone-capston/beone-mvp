package com.beone.api.benefit.primitive;

import java.util.Objects;

import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.rule.FractionalWonPolicy;

/**
 * 원 미만 처리 전의 정확한 혜택 금액. {@code wholeWon + tenThousandths / 10,000}원을 뜻한다.
 *
 * <p>
 * 원 미만 처리 방식은 카드별로 확인되지 않았으므로(D-03) 이 타입은 금액을 임의로 절사·반올림하지 않는다.
 * 확정된 정책이 있을 때만 {@link #round(FractionalWonPolicy)}로 원 단위 금액을 만든다.
 *
 * @param wholeWon 원 단위 정수 부분. 음수일 수 없다
 * @param tenThousandths 원 미만 부분을 1/10,000원 단위로 나타낸 값. 0~9,999
 */
public record UnroundedWon(Won wholeWon, int tenThousandths) {

	static final int UNITS_PER_WON = 10_000;

	private static final int HALF_WON = UNITS_PER_WON / 2;

	public UnroundedWon {
		Objects.requireNonNull(wholeWon, "wholeWon");
		if (wholeWon.isNegative()) {
			throw new IllegalArgumentException("unrounded benefit must not be negative: " + wholeWon.value());
		}
		if (tenThousandths < 0 || tenThousandths >= UNITS_PER_WON) {
			throw new IllegalArgumentException("fraction must be within 0..9999 ten-thousandths: " + tenThousandths);
		}
	}

	public static UnroundedWon exact(Won won) {
		return new UnroundedWon(won, 0);
	}

	/**
	 * 원 미만 금액이 생겼으면 참. 참이면 카드의 원 미만 처리 조건(D-03)이 결과에 영향을 준다.
	 */
	public boolean hasFractionalWon() {
		return tenThousandths != 0;
	}

	/**
	 * 확정된 원 미만 처리 방식으로 원 단위 금액을 만든다. 미확정 정책을 기본값으로 대신하지 않도록 정책을 반드시 받는다.
	 */
	public Won round(FractionalWonPolicy policy) {
		Objects.requireNonNull(policy, "policy");
		return switch (policy) {
			case TRUNCATE -> wholeWon;
			// 음수가 없으므로 0.5원 이상이면 1원을 올린다. 정수 부분이 Long 최댓값이면 올림 결과를 표현할 수 없다.
			case ROUND_HALF_UP -> tenThousandths >= HALF_WON ? Won.of(Math.addExact(wholeWon.value(), 1)) : wholeWon;
		};
	}

}
