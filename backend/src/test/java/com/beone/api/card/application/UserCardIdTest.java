package com.beone.api.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 보유 카드 ID 외부 표현({@code uc-}+번호, 결정 27 A2-1).
 */
class UserCardIdTest {

	@Test
	void formatsAndParsesRepositoryNumber() {
		assertThat(UserCardId.format(1001)).isEqualTo("uc-1001");
		assertThat(UserCardId.parse("uc-1001")).contains(1001L);
		assertThat(UserCardId.parse("uc-9223372036854775807")).contains(Long.MAX_VALUE);
	}

	@ParameterizedTest
	@ValueSource(strings = { "1001", "uc-", "uc-0", "uc-01001", "UC-1001", "uc-1001 ", "uc--1", "uc-1a",
			"uc-9223372036854775808", "ord-1001" })
	void malformedValuesAreEmpty(String value) {
		assertThat(UserCardId.parse(value)).isEmpty();
	}

	@Test
	void nullIsEmptyAndNonPositiveCannotBeFormatted() {
		assertThat(UserCardId.parse(null)).isEmpty();
		assertThatIllegalArgumentException().isThrownBy(() -> UserCardId.format(0));
	}

}
