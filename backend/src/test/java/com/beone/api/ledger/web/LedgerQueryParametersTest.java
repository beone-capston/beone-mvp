package com.beone.api.ledger.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.YearMonth;

import com.beone.api.ledger.application.LedgerSource;
import org.junit.jupiter.api.Test;

/**
 * 쿼리 값 검사: {@code month}는 {@code YYYY-MM}(docs/api.md 값 형식), {@code source}는 {@code SIMULATION}/{@code CODEF}만.
 */
class LedgerQueryParametersTest {

	@Test
	void acceptsContractValues() {
		assertThat(LedgerQueryParameters.month("2026-11")).isEqualTo(YearMonth.of(2026, 11));
		assertThat(LedgerQueryParameters.source(null)).isNull();
		assertThat(LedgerQueryParameters.source("SIMULATION")).isEqualTo(LedgerSource.SIMULATION);
		assertThat(LedgerQueryParameters.source("CODEF")).isEqualTo(LedgerSource.CODEF);
	}

	@Test
	void rejectsOtherFormatsWithTheFieldName() {
		for (String month : new String[] { "", "2026-1", "2026-13", "2026-00", "2026/11", "2026-11-01", "202611" }) {
			assertThatThrownBy(() -> LedgerQueryParameters.month(month)).as(month)
				.isInstanceOfSatisfying(InvalidLedgerQueryException.class, ex -> assertThat(ex.field()).isEqualTo("month"));
		}
		for (String source : new String[] { "", "simulation", "MANUAL", " CODEF" }) {
			assertThatThrownBy(() -> LedgerQueryParameters.source(source)).as(source)
				.isInstanceOfSatisfying(InvalidLedgerQueryException.class,
						ex -> assertThat(ex.field()).isEqualTo("source"));
		}
	}

}
