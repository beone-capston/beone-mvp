package com.beone.api.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 새 비밀번호 규칙을 확인한다. 기대값은 docs/api/auth.md 「값 검증」(11~32자, 영문·숫자·ASCII 특수기호 각 1개 이상, 공백·한글 불가,
 * 대·소문자 동시 사용 강제 안 함)이다.
 */
class NewPasswordTest {

	private final NewPassword.Validator validator = new NewPassword.Validator();

	@ParameterizedTest
	@ValueSource(strings = { "abcdefgh1!x", "ABCDEFGH1!X", "a1!a1!a1!a1", "Pass_word_123", "a1~bbbbbbbbbbbbbbbbbbbbbbbbbbbbb" })
	void acceptsPasswordsFollowingRules(String value) {
		assertThat(this.validator.isValid(value, null)).isTrue();
	}

	@Test
	void lengthBoundariesAre11And32() {
		assertThat(this.validator.isValid("abcdefg1!x", null)).isFalse();
		assertThat(this.validator.isValid("abcdefgh1!x", null)).isTrue();
		assertThat(this.validator.isValid("a1!" + "b".repeat(29), null)).isTrue();
		assertThat(this.validator.isValid("a1!" + "b".repeat(30), null)).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "abcdefghijk!", "12345678901!", "abcdefgh123", "abcd efgh1!x", "비밀번호abcdefg1!", "abcdefgh1!\t",
			" abcdefgh1!x", "abcdefgh1!é" })
	void rejectsMissingKindOrDisallowedCharacters(String value) {
		assertThat(this.validator.isValid(value, null)).isFalse();
	}

	@Test
	void nullIsLeftToNotNull() {
		assertThat(this.validator.isValid(null, null)).isTrue();
	}

}
