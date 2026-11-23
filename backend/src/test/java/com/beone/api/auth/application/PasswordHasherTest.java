package com.beone.api.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

/**
 * Argon2id 해시를 확인한다.
 *
 * <p>기대값: 구현 정확성은 Argon2 참조 구현(phc-winner-argon2) 시험 벡터로 본다. argon2id, v=19, 비밀번호 "password", 솔트
 * "somesalt", t=2, m=65536KiB, p=1, 32바이트 → 16진수 {@code 09316115...913cf7}, PHC
 * {@code $argon2id$v=19$m=65536,t=2,p=1$c29tZXNhbHQ$CTFh...PPc}. 저장 파라미터는 docs/api/auth.md의 m=19456KiB·t=2·p=1이다.
 */
class PasswordHasherTest {

	private static final String REFERENCE_PHC = "$argon2id$v=19$m=65536,t=2,p=1$c29tZXNhbHQ$CTFhFdXPJO1aFaMaO6Mm5c8y7cJHAph8ArZWb2GRPPc";

	private final PasswordHasher hasher = new PasswordHasher();

	@Test
	void derivesReferenceTestVector() {
		byte[] out = PasswordHasher.derive("password", "somesalt".getBytes(StandardCharsets.US_ASCII), 65536, 2, 1, 32);

		assertThat(HexFormat.of().formatHex(out))
			.isEqualTo("09316115d5cf24ed5a15a31a3ba326e5cf32edc24702987c02b6566f61913cf7");
	}

	@Test
	void matchesReferencePhcStringWithItsOwnParameters() {
		assertThat(this.hasher.matches("password", REFERENCE_PHC)).isTrue();
		assertThat(this.hasher.matches("Password", REFERENCE_PHC)).isFalse();
	}

	@Test
	void hashUsesApprovedParametersAndRandomSalt() {
		String first = this.hasher.hash("abcdefgh1!x");
		String second = this.hasher.hash("abcdefgh1!x");

		assertThat(first).startsWith("$argon2id$v=19$m=19456,t=2,p=1$")
			.matches("\\$argon2id\\$v=19\\$m=19456,t=2,p=1\\$[A-Za-z0-9+/]{22}\\$[A-Za-z0-9+/]{43}")
			.doesNotContain("abcdefgh1!x");
		assertThat(second).isNotEqualTo(first);
		assertThat(this.hasher.matches("abcdefgh1!x", first)).isTrue();
		assertThat(this.hasher.matches("abcdefgh1!x", second)).isTrue();
		assertThat(this.hasher.matches("abcdefgh1!y", first)).isFalse();
	}

	@Test
	void malformedStoredValueDoesNotMatch() {
		assertThat(this.hasher.matches("password", "plain-text")).isFalse();
		assertThat(this.hasher.matches("password", "$argon2i$v=19$m=65536,t=2,p=1$c29tZXNhbHQ$CTFhFdXPJO1aFaMaO6Mm5c8y7cJHAph8ArZWb2GRPPc"))
			.isFalse();
	}

}
