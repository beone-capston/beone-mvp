package com.beone.api.benefit.rule;

import java.util.Objects;

/**
 * 규칙 조건 값. 검증된 근거로 확정됐거나 아직 미확정({@code UNRESOLVED})이다.
 *
 * <p>
 * 미확정은 {@code 확인 필요}를 뜻한다. 기본값이나 0원 대체 값이 없으며,
 * 값이 필요한 계산은 해당 혜택을 확인 필요로 처리해야 한다(D-03, D-05).
 */
public sealed interface RuleTerm<T> {

	static <T> Resolved<T> resolved(T value) {
		return new Resolved<>(value);
	}

	static <T> Unresolved<T> unresolved(String reason) {
		return new Unresolved<>(reason);
	}

	boolean isResolved();

	/**
	 * 확정 값을 반환한다.
	 * @throws IllegalStateException 미확정 조건인 경우
	 */
	T resolvedValue();

	record Resolved<T>(T value) implements RuleTerm<T> {

		public Resolved {
			Objects.requireNonNull(value, "value");
		}

		@Override
		public boolean isResolved() {
			return true;
		}

		@Override
		public T resolvedValue() {
			return value;
		}

	}

	record Unresolved<T>(String reason) implements RuleTerm<T> {

		public Unresolved {
			if (reason == null || reason.isBlank()) {
				throw new IllegalArgumentException("reason is required for an unresolved term");
			}
		}

		@Override
		public boolean isResolved() {
			return false;
		}

		@Override
		public T resolvedValue() {
			throw new IllegalStateException("rule term is unresolved: " + reason);
		}

	}

}
