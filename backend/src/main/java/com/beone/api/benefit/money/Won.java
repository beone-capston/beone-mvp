package com.beone.api.benefit.money;

/**
 * 원 단위 정수 금액. 취소를 위해 음수도 허용하며, 부호 제한은 각 모델이 검증한다.
 * 금액에 {@code float}·{@code double}을 쓰지 않는다.
 */
public record Won(long value) implements Comparable<Won> {

	public static final Won ZERO = new Won(0);

	public static Won of(long value) {
		return new Won(value);
	}

	public boolean isPositive() {
		return value > 0;
	}

	public boolean isNegative() {
		return value < 0;
	}

	public boolean isZero() {
		return value == 0;
	}

	@Override
	public int compareTo(Won other) {
		return Long.compare(value, other.value);
	}

}
