package com.beone.api.codef.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import com.beone.api.codef.persistence.CodefPerformanceReferenceEntity;
import com.beone.api.codef.persistence.CodefPerformanceReferenceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * CODEF 실적 참고값 조회 창구(#281). 카드에 연결되고 반영기간이 있는 참고값만 돌려주고, 해석하지 못한 금액은 0으로 바꾸지 않는다. 값은 합성이다.
 */
class CodefPerformanceReferenceQueryTest {

	private static final LocalDate FROM = LocalDate.of(2026, 10, 1);

	private static final LocalDate TO = LocalDate.of(2026, 10, 31);

	private static final LocalDateTime FETCHED = LocalDateTime.of(2026, 11, 4, 9, 1, 0);

	private final CodefPerformanceReferenceRepository repository = mock(CodefPerformanceReferenceRepository.class);

	private final CodefPerformanceReferenceQuery query = new CodefPerformanceReferenceQuery(this.repository);

	private static CodefPerformanceReferenceEntity entity(long id, Long userCardId, LocalDate from, LocalDate to,
			Long won) {
		CodefPerformanceReferenceEntity entity = new CodefPerformanceReferenceEntity(1L, userCardId, 40L, "SYN CARD",
				from, to, won, FETCHED);
		ReflectionTestUtils.setField(entity, "codefPerformanceReferenceId", id);
		return entity;
	}

	@Test
	void linkedReferenceIsReturnedWithUnknownAmountKeptNull() {
		given(this.repository
			.findFirstByUserIdAndUserCardIdAndPeriodFromAndPeriodToOrderByFetchedAtDescCodefPerformanceReferenceIdDesc(1L,
					11L, FROM, TO))
			.willReturn(Optional.of(entity(5, 11L, FROM, TO, null)));

		assertThat(this.query.latest(1L, 11L, FROM, TO))
			.contains(new CodefPerformanceReferenceQuery.Reference(5, 40, 11, FROM, TO, null, FETCHED));
	}

	@Test
	void unlinkedOrPeriodlessReferenceIsNotReturned() {
		given(this.repository.findByCodefPerformanceReferenceIdAndUserId(5L, 1L))
			.willReturn(Optional.of(entity(5, null, FROM, TO, 1000L)));
		given(this.repository.findByCodefPerformanceReferenceIdAndUserId(6L, 1L))
			.willReturn(Optional.of(entity(6, 11L, null, null, 1000L)));

		assertThat(this.query.find(1L, 5L)).isEmpty();
		assertThat(this.query.find(1L, 6L)).isEmpty();
		assertThat(this.query.find(1L, 7L)).isEmpty();
	}

	@Test
	void referenceIdHasAFixedExternalFormat() {
		assertThat(CodefPerformanceReferenceQuery.ReferenceId.format(12)).isEqualTo("cpr-12");
		assertThat(CodefPerformanceReferenceQuery.ReferenceId.parse("cpr-12")).contains(12L);
		for (String invalid : new String[] { null, "", "cpr-0", "cpr-012", "12", "cpr-", "uc-12", "cpr-99999999999999999999" }) {
			assertThat(CodefPerformanceReferenceQuery.ReferenceId.parse(invalid)).as(String.valueOf(invalid)).isEmpty();
		}
		assertThatThrownBy(() -> CodefPerformanceReferenceQuery.ReferenceId.format(0))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
