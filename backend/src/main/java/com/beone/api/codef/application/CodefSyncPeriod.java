package com.beone.api.codef.application;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 승인내역 조회 기간(docs/api/codef.md, 관리자 판단 P9).
 *
 * <p>둘 다 없으면 이번 달 1일~오늘이다. {@code to}만 없으면 오늘, {@code from}만 없으면 {@code to}가 속한 달의 1일이다. {@code to}는 오늘보다
 * 늦을 수 없고, {@code from}은 {@code to}보다 늦을 수 없으며, 최대 12개월({@code from >= to.minusMonths(12).plusDays(1)})이다. 역순
 * 기간을 CODEF 조회 조건으로 넘기지 않는다.
 * @param from 시작일
 * @param to 종료일
 */
public record CodefSyncPeriod(LocalDate from, LocalDate to) {

	static final String AFTER_TODAY = "오늘보다 늦을 수 없습니다.";

	static final String REVERSED = "시작일이 종료일보다 늦습니다.";

	static final String TOO_LONG = "조회 기간은 최대 12개월입니다.";

	/**
	 * 값을 확인한다.
	 * @throws NullPointerException 값이 null인 경우
	 */
	public CodefSyncPeriod {
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
	}

	/**
	 * 요청 값과 오늘 날짜로 기간을 정한다.
	 * @param from 요청 시작일. 없으면 {@code null}
	 * @param to 요청 종료일. 없으면 {@code null}
	 * @param today 서울 기준 오늘
	 * @return 기간
	 * @throws CodefSyncPeriodException 규칙에 맞지 않는 경우
	 */
	public static CodefSyncPeriod resolve(LocalDate from, LocalDate to, LocalDate today) {
		Objects.requireNonNull(today, "today");
		LocalDate end = (to == null) ? today : to;
		if (end.isAfter(today)) {
			throw new CodefSyncPeriodException("to", AFTER_TODAY);
		}
		LocalDate start = (from == null) ? end.withDayOfMonth(1) : from;
		if (start.isAfter(end)) {
			throw new CodefSyncPeriodException(null, REVERSED);
		}
		if (start.isBefore(end.minusMonths(12).plusDays(1))) {
			throw new CodefSyncPeriodException(null, TOO_LONG);
		}
		return new CodefSyncPeriod(start, end);
	}

}
