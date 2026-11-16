package com.beone.api.order.application;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import com.beone.api.order.persistence.PlannedSpendRepository;
import com.beone.api.user.application.CurrentUser;
import com.beone.api.user.application.DemoUser;
import org.junit.jupiter.api.Test;

/**
 * 교체 직전 이번 달 재확인을 확인한다. 웹 검증 뒤 달이 바뀐 경우 기존 값을 지우지 않고 요청 오류로 끝나야 한다(PR #107 리뷰).
 */
class PlannedSpendServiceTest {

	private static final CurrentUser USER = new CurrentUser(1L, DemoUser.MY_CARDS);

	private final PlannedSpendRepository repository = mock(PlannedSpendRepository.class);

	@Test
	void novemberItemAfterSeoulMonthRolloverIsRejectedWithoutDeleting() {
		// 서울 2026-12-01 00:00:00
		PlannedSpendService service = serviceAt("2026-11-30T15:00:00Z");

		assertThatThrownBy(() -> service.replace(USER, List.of(item(LocalDate.of(2026, 11, 30)))))
			.isInstanceOf(PlannedDateOutOfMonthException.class);
		verify(this.repository, never()).deleteAllOfUser(anyLong());
		verify(this.repository, never()).saveAllAndFlush(any());
	}

	@Test
	void moreThanFiveItemsIsInvariantViolation() {
		PlannedSpendService service = serviceAt("2026-11-17T03:00:00Z");

		assertThatIllegalArgumentException()
			.isThrownBy(() -> service.replace(USER, Collections.nCopies(6, item(LocalDate.of(2026, 11, 20)))));
		verify(this.repository, never()).deleteAllOfUser(anyLong());
	}

	private PlannedSpendService serviceAt(String instant) {
		return new PlannedSpendService(this.repository,
				new OrderClock(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC)));
	}

	private static NewPlannedSpend item(LocalDate date) {
		return new NewPlannedSpend(date, null, 1000, "TAX", PaymentMethod.DIRECT, null);
	}

}
