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
 */
public record StateValue<T>(T value, StateSource source, DatePeriod period, LocalDateTime updatedAt) {

	public StateValue {
		Objects.requireNonNull(source, "source");
		if ((source == StateSource.UNKNOWN) != (value == null)) {
			throw new IllegalArgumentException("value must be null exactly when source is UNKNOWN");
		}
	}

	/** 입력하지 않은 값. */
	public static <T> StateValue<T> unknown() {
		return new StateValue<>(null, StateSource.UNKNOWN, null, null);
	}

	/** 사용자가 입력한 값. */
	public static <T> StateValue<T> manual(T value, DatePeriod period, LocalDateTime updatedAt) {
		return new StateValue<>(Objects.requireNonNull(value, "value"), StateSource.MANUAL,
				Objects.requireNonNull(period, "period"), Objects.requireNonNull(updatedAt, "updatedAt"));
	}

	/** 계산에 쓸 수 있는지. */
	public boolean usable() {
		return this.source != StateSource.UNKNOWN;
	}

	/** 쓸 수 없는 이유. 쓸 수 있으면 {@code null}. */
	public StateReason reason() {
		return usable() ? null : StateReason.NOT_ENTERED;
	}

}
