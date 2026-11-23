package com.beone.api.benefit.engine;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;

import com.beone.api.benefit.state.BasisPeriod;
import com.beone.api.benefit.state.StateSource;

/**
 * 출처와 기준 기간이 붙은 입력값 하나.
 *
 * <p>{@code value=null}은 모름이고 0이 아니다. 기준 기간이 주문 기준 기간과 맞는지는 엔진이 판단하므로
 * {@code reason}에 {@code PERIOD_MISMATCH}를 넣을 수 없다. {@code usable=false}는 업무 API만 아는 사유에만 쓴다.
 *
 * @param value 값. {@code UNKNOWN}이면 null
 * @param source {@code MANUAL}·{@code CODEF}·{@code SYNTHETIC}·{@code UNKNOWN}. 원장 기반 산출({@code INFERRED})은 받지 않는다(D-16)
 * @param periodFrom 기준 기간 시작. 값이 있으면 필수
 * @param periodTo 기준 기간 끝. 값이 있으면 필수
 * @param updatedAt 입력·조회 시각. {@code MANUAL}·{@code CODEF}면 필수
 * @param usable 계산에 쓸 수 있으면 참
 * @param reason {@code usable=false}의 사유
 */
public record StateInput<T>(T value, String source, LocalDate periodFrom, LocalDate periodTo,
		OffsetDateTime updatedAt, boolean usable, String reason) {

	private static final Set<String> UNKNOWN_REASONS = Set.of("NOT_ENTERED", "SOURCE_CONFLICT");

	private static final Set<String> KNOWN_VALUE_REASONS = Set.of("SOURCE_CONFLICT", "NEGATIVE_UNCONFIRMED_ATTRIBUTION");

	public StateInput {
		EngineChecks.requireEnumName(StateSource.class, source, "source");
		StateSource parsed = StateSource.valueOf(source);
		if (parsed == StateSource.INFERRED) {
			throw new IllegalArgumentException("INFERRED state is not accepted by the engine entry");
		}
		if ((periodFrom == null) != (periodTo == null)) {
			throw new IllegalArgumentException("period needs both ends or neither");
		}
		if (periodFrom != null) {
			BasisPeriod.of(periodFrom, periodTo);
		}
		if ("PERIOD_MISMATCH".equals(reason)) {
			throw new IllegalArgumentException("the engine judges the period; do not pass PERIOD_MISMATCH");
		}
		if (usable && reason != null) {
			throw new IllegalArgumentException("a usable value must not carry a reason");
		}
		if (parsed == StateSource.UNKNOWN) {
			checkUnknown(value, usable, reason);
		}
		else {
			checkKnown(value, parsed, periodFrom, updatedAt, usable, reason);
		}
	}

	private static void checkUnknown(Object value, boolean usable, String reason) {
		if (value != null) {
			throw new IllegalArgumentException("UNKNOWN state must not carry a value");
		}
		if (usable) {
			throw new IllegalArgumentException("UNKNOWN state is not usable");
		}
		EngineChecks.requireOneOf(UNKNOWN_REASONS, reason, "reason");
	}

	private static void checkKnown(Object value, StateSource source, LocalDate periodFrom, OffsetDateTime updatedAt,
			boolean usable, String reason) {
		if (value == null) {
			throw new IllegalArgumentException("value is required for source " + source);
		}
		if (periodFrom == null) {
			throw new IllegalArgumentException("period is required for source " + source);
		}
		if (updatedAt == null && source != StateSource.SYNTHETIC) {
			throw new IllegalArgumentException("updatedAt is required for source " + source);
		}
		if (!usable) {
			EngineChecks.requireOneOf(KNOWN_VALUE_REASONS, reason, "reason");
		}
	}

	/** 입력하지 않은 값. */
	public static <T> StateInput<T> notEntered() {
		return new StateInput<>(null, "UNKNOWN", null, null, null, false, "NOT_ENTERED");
	}

	/** 사용자가 입력한 값. */
	public static <T> StateInput<T> manual(T value, LocalDate periodFrom, LocalDate periodTo, OffsetDateTime updatedAt) {
		return new StateInput<>(value, "MANUAL", periodFrom, periodTo, updatedAt, true, null);
	}

	/** 가상 포트폴리오의 시드 값. */
	public static <T> StateInput<T> synthetic(T value, LocalDate periodFrom, LocalDate periodTo) {
		return new StateInput<>(value, "SYNTHETIC", periodFrom, periodTo, null, true, null);
	}

}
