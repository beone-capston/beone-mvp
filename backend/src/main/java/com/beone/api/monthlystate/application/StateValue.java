package com.beone.api.monthlystate.application;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 출처·기준 기간·입력 시각을 붙인 상태값 하나(D-04, D-14 원칙 4).
 *
 * <p>미입력은 {@link #unknown()}이며 {@code value}가 {@code null}이다. {@code null}과 사용자가 입력한 0은 다르다. 1차 수동 입력
 * 값은 음수 전월 실적까지 모두 계산에 쓸 수 있는 값으로 둔다(직접 입력은 D-17의 근거 확정 값, 결정 27 A3-8).
 *
 * @param <T> 값의 타입(원 단위 금액 {@link Long} 또는 날짜)
 * @param value 값. 미입력이면 {@code null}
 * @param source 출처
 * @param period 기준 기간. 미입력이면 {@code null}
 * @param updatedAt 입력 시각(서울). 미입력이면 {@code null}
 * @param blockedReason 값은 있지만 계산에 쓰지 않는 이유. 지금은 출처 충돌({@link StateReason#SOURCE_CONFLICT}, D-16 사용자 결정
 * 2026-11-27 Q2)만 있다. 없으면 {@code null}
 */
public record StateValue<T>(T value, StateSource source, DatePeriod period, LocalDateTime updatedAt,
		StateReason blockedReason) {

	public StateValue {
		Objects.requireNonNull(source, "source");
		if ((source == StateSource.UNKNOWN) != (value == null)) {
			throw new IllegalArgumentException("value must be null exactly when source is UNKNOWN");
		}
		if (blockedReason != null && (source == StateSource.UNKNOWN || blockedReason != StateReason.SOURCE_CONFLICT)) {
			throw new IllegalArgumentException("only a known value can be blocked, and only by a source conflict");
		}
	}

	/**
	 * 계산을 막는 이유가 없는 값.
	 * @param value 값
	 * @param source 출처
	 * @param period 기준 기간
	 * @param updatedAt 입력 시각
	 */
	public StateValue(T value, StateSource source, DatePeriod period, LocalDateTime updatedAt) {
		this(value, source, period, updatedAt, null);
	}

	/**
	 * 같은 값을 출처 충돌로 계산에서 뺀다. 값·출처·기간·시각은 그대로 보여 준다.
	 * @return {@code usable=false}, 이유 {@code SOURCE_CONFLICT}인 값
	 */
	public StateValue<T> conflicted() {
		return new StateValue<>(this.value, this.source, this.period, this.updatedAt, StateReason.SOURCE_CONFLICT);
	}

	/**
	 * 입력하지 않은 값.
	 * @param <T> 값 형식
	 * @return 미입력 값
	 */
	public static <T> StateValue<T> unknown() {
		return new StateValue<>(null, StateSource.UNKNOWN, null, null);
	}

	/**
	 * 사용자가 입력한 값.
	 * @param <T> 값 형식
	 * @param value 입력한 값
	 * @param period 값의 기준 기간
	 * @param updatedAt 입력 시각
	 * @return 수동 입력 값
	 */
	public static <T> StateValue<T> manual(T value, DatePeriod period, LocalDateTime updatedAt) {
		return new StateValue<>(Objects.requireNonNull(value, "value"), StateSource.MANUAL,
				Objects.requireNonNull(period, "period"), Objects.requireNonNull(updatedAt, "updatedAt"));
	}

	/**
	 * 계산에 쓸 수 있는지.
	 * @return 계산에 쓸 수 있으면 {@code true}
	 */
	public boolean usable() {
		return this.source != StateSource.UNKNOWN && this.blockedReason == null;
	}

	/**
	 * 쓸 수 없는 이유. 쓸 수 있으면 {@code null}.
	 * @return 쓸 수 없는 이유, 쓸 수 있으면 {@code null}
	 */
	public StateReason reason() {
		if (usable()) {
			return null;
		}
		return (this.source == StateSource.UNKNOWN) ? StateReason.NOT_ENTERED : this.blockedReason;
	}

}
