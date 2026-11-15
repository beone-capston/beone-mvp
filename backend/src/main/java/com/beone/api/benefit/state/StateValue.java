package com.beone.api.benefit.state;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

import com.beone.api.benefit.rule.RuleVersionRef;

/**
 * 출처, 기준 기간, 갱신 시각을 함께 갖는 상태값(D-04).
 *
 * <ul>
 * <li>{@code UNKNOWN}은 값이 없고 기준 기간도 모를 수 있다.</li>
 * <li>그 밖의 출처는 값과 기준 기간이 필수다.</li>
 * <li>{@code CODEF}, {@code MANUAL}, {@code INFERRED}는 갱신 시각이 필수다.
 * {@code SYNTHETIC}은 시나리오 데이터에 없는 시각을 만들지 않도록 생략할 수 있다.</li>
 * <li>{@code INFERRED}는 산출에 쓴 규칙 버전이 필수다.</li>
 * </ul>
 */
public final class StateValue<T> {

	private final T value;

	private final StateSource source;

	private final BasisPeriod basisPeriod;

	private final OffsetDateTime refreshedAt;

	private final RuleVersionRef derivedFrom;

	private StateValue(T value, StateSource source, BasisPeriod basisPeriod, OffsetDateTime refreshedAt,
			RuleVersionRef derivedFrom) {
		Objects.requireNonNull(source, "source");
		if (source == StateSource.UNKNOWN) {
			if (value != null) {
				throw new IllegalArgumentException("UNKNOWN state must not carry a value");
			}
		}
		else {
			Objects.requireNonNull(value, "value is required for source " + source);
			Objects.requireNonNull(basisPeriod, "basis period is required for source " + source);
		}
		if (refreshedAt == null && requiresRefreshTime(source)) {
			throw new IllegalArgumentException("refresh time is required for source " + source);
		}
		if (source == StateSource.INFERRED && derivedFrom == null) {
			throw new IllegalArgumentException("INFERRED state requires the rule version it was computed with");
		}
		if (source != StateSource.INFERRED && derivedFrom != null) {
			throw new IllegalArgumentException("only INFERRED state records a rule version, not " + source);
		}
		this.value = value;
		this.source = source;
		this.basisPeriod = basisPeriod;
		this.refreshedAt = refreshedAt;
		this.derivedFrom = derivedFrom;
	}

	private static boolean requiresRefreshTime(StateSource source) {
		return source == StateSource.CODEF || source == StateSource.MANUAL || source == StateSource.INFERRED;
	}

	public static <T> StateValue<T> codef(T value, BasisPeriod basisPeriod, OffsetDateTime refreshedAt) {
		return new StateValue<>(value, StateSource.CODEF, basisPeriod, refreshedAt, null);
	}

	public static <T> StateValue<T> manual(T value, BasisPeriod basisPeriod, OffsetDateTime refreshedAt) {
		return new StateValue<>(value, StateSource.MANUAL, basisPeriod, refreshedAt, null);
	}

	public static <T> StateValue<T> inferred(T value, BasisPeriod basisPeriod, OffsetDateTime refreshedAt,
			RuleVersionRef derivedFrom) {
		return new StateValue<>(value, StateSource.INFERRED, basisPeriod, refreshedAt, derivedFrom);
	}

	public static <T> StateValue<T> synthetic(T value, BasisPeriod basisPeriod) {
		return new StateValue<>(value, StateSource.SYNTHETIC, basisPeriod, null, null);
	}

	public static <T> StateValue<T> unknown(BasisPeriod basisPeriodOrNull, OffsetDateTime refreshedAtOrNull) {
		return new StateValue<>(null, StateSource.UNKNOWN, basisPeriodOrNull, refreshedAtOrNull, null);
	}

	public boolean isKnown() {
		return source != StateSource.UNKNOWN;
	}

	/**
	 * 값을 반환한다.
	 * @throws IllegalStateException 상태가 {@code UNKNOWN}인 경우
	 */
	public T knownValue() {
		if (!isKnown()) {
			throw new IllegalStateException("state value is UNKNOWN");
		}
		return value;
	}

	public StateSource source() {
		return source;
	}

	public Optional<BasisPeriod> basisPeriod() {
		return Optional.ofNullable(basisPeriod);
	}

	public Optional<OffsetDateTime> refreshedAt() {
		return Optional.ofNullable(refreshedAt);
	}

	public Optional<RuleVersionRef> derivedFrom() {
		return Optional.ofNullable(derivedFrom);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof StateValue<?> that && Objects.equals(value, that.value) && source == that.source
				&& Objects.equals(basisPeriod, that.basisPeriod) && Objects.equals(refreshedAt, that.refreshedAt)
				&& Objects.equals(derivedFrom, that.derivedFrom);
	}

	@Override
	public int hashCode() {
		return Objects.hash(value, source, basisPeriod, refreshedAt, derivedFrom);
	}

	@Override
	public String toString() {
		return "StateValue[" + source + (isKnown() ? ", " + value : "") + ", period=" + basisPeriod + "]";
	}

}
