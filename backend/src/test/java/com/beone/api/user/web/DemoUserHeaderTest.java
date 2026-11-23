package com.beone.api.user.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.beone.api.user.application.DemoUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 기대값은 docs/api.md 업무 API 공통 규약(값 MY_CARDS·VIRTUAL_PORTFOLIO, 없거나 잘못되면 400)에서 정했다.
 */
class DemoUserHeaderTest {

	@Test
	void headerNameMatchesContract() {
		assertThat(DemoUserHeader.NAME).isEqualTo("X-BeONE-Demo-User");
	}

	@Test
	void acceptsMyCards() {
		assertThat(DemoUserHeader.parse(new String[] { "MY_CARDS" })).isEqualTo(DemoUser.MY_CARDS);
	}

	@Test
	void acceptsVirtualPortfolio() {
		assertThat(DemoUserHeader.parse(new String[] { "VIRTUAL_PORTFOLIO" })).isEqualTo(DemoUser.VIRTUAL_PORTFOLIO);
	}

	@Test
	void rejectsMissingHeader() {
		assertThatThrownBy(() -> DemoUserHeader.parse(null)).isInstanceOf(InvalidDemoUserHeaderException.class);
		assertThatThrownBy(() -> DemoUserHeader.parse(new String[0]))
			.isInstanceOf(InvalidDemoUserHeaderException.class);
		assertThatThrownBy(() -> DemoUserHeader.parse(new String[] { null }))
			.isInstanceOf(InvalidDemoUserHeaderException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", " ", "my_cards", "My_Cards", " MY_CARDS", "MY_CARDS ", "VIRTUAL", "REAL", "1",
			"MY_CARDS,VIRTUAL_PORTFOLIO" })
	void rejectsValuesOutsideContract(String value) {
		assertThatThrownBy(() -> DemoUserHeader.parse(new String[] { value }))
			.isInstanceOf(InvalidDemoUserHeaderException.class);
	}

	@Test
	void rejectsMultipleHeaderValues() {
		assertThatThrownBy(() -> DemoUserHeader.parse(new String[] { "MY_CARDS", "MY_CARDS" }))
			.isInstanceOf(InvalidDemoUserHeaderException.class);
	}

	@Test
	void exceptionCarriesNoInputValue() {
		assertThat(new InvalidDemoUserHeaderException().getMessage()).isNull();
	}

}
