package com.beone.api.monthlystate.application;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 상태값의 기준 기간(코드 {@code BasisPeriod}와 같은 모양). 카드의 실적·한도 기간과 맞는지는 판단하지 않는다(D-22).
 *
 * @param from 시작일
 * @param to 종료일. 시작일보다 앞설 수 없다
 */
public record DatePeriod(LocalDate from, LocalDate to) {

	public DatePeriod {
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
		if (from.isAfter(to)) {
			throw new IllegalArgumentException("from must not be after to");
		}
	}

}
