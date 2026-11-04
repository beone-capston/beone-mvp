package com.beone.api.benefit.input;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

import com.beone.api.benefit.card.ServiceId;

/**
 * 사용자가 카드에 지정한 택1 선택과 적용 시작일(09271 FUEL/STORE, D-18 K1).
 *
 * <p>
 * 입력은 세 값이다. 입력 없음은 {@link CardMonthlyState#serviceSelections()}가 비어 있거나 값이 {@code UNKNOWN}인 경우이고, 서비스 선택과
 * 선택 없음({@code NONE})은 이 타입으로 나타낸다. {@code NONE}은 사용자가 명시 입력한 값이며 입력 없음과 다르다.
 *
 * @param service 선택한 서비스. 비어 있으면 선택 없음이다
 * @param effectiveFrom 이 선택의 적용 시작일
 */
public record ServiceSelection(Optional<ServiceId> service, LocalDate effectiveFrom) {

	public ServiceSelection {
		Objects.requireNonNull(service, "service");
		Objects.requireNonNull(effectiveFrom, "effectiveFrom");
	}

	public ServiceSelection(ServiceId service, LocalDate effectiveFrom) {
		this(Optional.of(service), effectiveFrom);
	}

	/**
	 * 사용자가 명시 입력한 선택 없음.
	 */
	public static ServiceSelection none(LocalDate effectiveFrom) {
		return new ServiceSelection(Optional.empty(), effectiveFrom);
	}

}
