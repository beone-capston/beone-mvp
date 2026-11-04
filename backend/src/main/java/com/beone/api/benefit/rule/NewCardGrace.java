package com.beone.api.benefit.rule;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.beone.api.benefit.card.ServiceId;
import com.beone.api.benefit.money.Won;

/**
 * 신규 카드 유예. 유예 기간 동안 대상 서비스는 전월 실적을 최소 {@code deemedPerformance}로 본다(D-03).
 * 09174의 값은 문서 결과와 같은 효과의 모델 표현이며 문서 문구가 아니다.
 *
 * @param partialPerformance 전월 실적이 0원보다 크고 간주 실적보다 작을 때의 처리(D-20)
 */
public record NewCardGrace(GraceSpan span, Optional<Integer> days, Won deemedPerformance, Set<ServiceId> services,
		GracePartialPerformance partialPerformance) {

	public NewCardGrace {
		Objects.requireNonNull(span, "span");
		Objects.requireNonNull(days, "days");
		Objects.requireNonNull(deemedPerformance, "deemedPerformance");
		Objects.requireNonNull(services, "services");
		if (span == GraceSpan.DAYS_FROM_REGISTRATION) {
			if (days.isEmpty() || days.get() <= 0) {
				throw new IllegalArgumentException("a day-based grace needs a positive number of days");
			}
		}
		else if (days.isPresent()) {
			throw new IllegalArgumentException("days apply only to a day-based grace");
		}
		if (!deemedPerformance.isPositive()) {
			throw new IllegalArgumentException("deemed performance must be positive");
		}
		if (services.isEmpty()) {
			throw new IllegalArgumentException("grace must list the services it applies to");
		}
		services = Set.copyOf(services);
		Objects.requireNonNull(partialPerformance, "partialPerformance");
	}

	/**
	 * 주문일이 유예 기간 안이면 참. 01664의 "등록일 포함 60일"처럼 등록일을 첫날로 센다.
	 *
	 * @param registeredAt 카드 등록일. 주문일보다 늦지 않아야 한다
	 * @param orderDate 주문일
	 */
	public boolean covers(LocalDate registeredAt, LocalDate orderDate) {
		LocalDate lastDay = switch (span) {
			case UNTIL_END_OF_NEXT_MONTH -> YearMonth.from(registeredAt).plusMonths(1).atEndOfMonth();
			case DAYS_FROM_REGISTRATION -> registeredAt.plusDays(days.orElseThrow() - 1L);
		};
		return !orderDate.isAfter(lastDay);
	}

}
