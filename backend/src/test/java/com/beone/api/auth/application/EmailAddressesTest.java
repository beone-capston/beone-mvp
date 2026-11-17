package com.beone.api.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 이메일 정규화와 형식 검사를 확인한다. 기대값은 docs/api/auth.md 「값 검증」(최대 254자, 앞뒤 공백 제거, 소문자 통일, 점·{@code +태그}
 * 유지)이다.
 */
class EmailAddressesTest {

	@Test
	void normalizesByTrimmingAndLowercasingButKeepsDotsAndTags() {
		assertThat(EmailAddresses.normalize("  First.Last+Card@Example.COM \t")).isEqualTo("first.last+card@example.com");
	}

	@ParameterizedTest
	@ValueSource(strings = { "user@example.com", " User@Example.com ", "a.b+tag@sub.example.co.kr" })
	void acceptsEmailAddresses(String value) {
		assertThat(EmailAddresses.isValid(value)).isTrue();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   ", "user", "user@", "@example.com", "user@example", "us er@example.com",
			"user@@example.com", "user@example.com.", "user@exam ple.com" })
	void rejectsNonEmailValues(String value) {
		assertThat(EmailAddresses.isValid(value)).isFalse();
	}

	@Test
	void lengthLimitIs254AfterNormalization() {
		String domain = "@example.com";
		String at254 = "a".repeat(254 - domain.length()) + domain;
		String at255 = "a".repeat(255 - domain.length()) + domain;

		assertThat(EmailAddresses.isValid(at254)).isTrue();
		assertThat(EmailAddresses.isValid("  " + at254 + "  ")).isTrue();
		assertThat(EmailAddresses.isValid(at255)).isFalse();
	}

}
