package com.beone.api.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * 세션 ID·CSRF 토큰의 형식과 비교를 확인한다. 기대 형식은 docs/api/auth.md(43자 Base64URL)와 D-24(해시 저장)에서 옮겼다.
 */
class SessionTokensTest {

	@Test
	void newSessionIdIs43CharacterBase64UrlAndRandom() {
		Set<String> ids = new HashSet<>();
		for (int i = 0; i < 100; i++) {
			String id = SessionTokens.newSessionId();
			assertThat(id).hasSize(43).matches("[A-Za-z0-9_-]{43}");
			ids.add(id);
		}
		assertThat(ids).hasSize(100);
	}

	@Test
	void hashIsLowercaseHexSha256AndNotTheRawValue() {
		// SHA-256("abc")는 표준 시험 벡터다.
		assertThat(SessionTokens.hash("abc"))
			.isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
		String id = SessionTokens.newSessionId();
		assertThat(SessionTokens.hash(id)).hasSize(64).matches("[0-9a-f]{64}").doesNotContain(id);
	}

	@Test
	void csrfTokenIsBoundToSessionAndDiffersFromSessionIdAndHash() {
		String id = SessionTokens.newSessionId();
		String other = SessionTokens.newSessionId();

		String token = SessionTokens.csrfTokenOf(id);

		assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]{43}").isEqualTo(SessionTokens.csrfTokenOf(id));
		assertThat(token).isNotEqualTo(id).isNotEqualTo(SessionTokens.csrfTokenOf(other));
		assertThat(SessionTokens.hash(id)).doesNotContain(token);
	}

	@Test
	void csrfMatchesOnlyTheExactTokenOfTheSameSession() {
		String id = SessionTokens.newSessionId();
		String token = SessionTokens.csrfTokenOf(id);

		assertThat(SessionTokens.csrfMatches(id, token)).isTrue();
		assertThat(SessionTokens.csrfMatches(id, null)).isFalse();
		assertThat(SessionTokens.csrfMatches(id, "")).isFalse();
		assertThat(SessionTokens.csrfMatches(id, token + "x")).isFalse();
		assertThat(SessionTokens.csrfMatches(id, token.substring(1))).isFalse();
		assertThat(SessionTokens.csrfMatches(id, " " + token)).isFalse();
		assertThat(SessionTokens.csrfMatches(id, SessionTokens.csrfTokenOf(SessionTokens.newSessionId()))).isFalse();
		assertThat(SessionTokens.csrfMatches(id, id)).isFalse();
	}

	@Test
	void wellFormedAcceptsOnly43Base64UrlCharacters() {
		assertThat(SessionTokens.isWellFormed(SessionTokens.newSessionId())).isTrue();
		assertThat(SessionTokens.isWellFormed(null)).isFalse();
		assertThat(SessionTokens.isWellFormed("")).isFalse();
		assertThat(SessionTokens.isWellFormed("a".repeat(42))).isFalse();
		assertThat(SessionTokens.isWellFormed("a".repeat(44))).isFalse();
		assertThat(SessionTokens.isWellFormed("a".repeat(42) + "=")).isFalse();
		assertThat(SessionTokens.isWellFormed("a".repeat(42) + "+")).isFalse();
		assertThat(SessionTokens.isWellFormed("a".repeat(42) + "/")).isFalse();
	}

}
