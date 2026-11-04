package com.beone.api.codef.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

class CodefApiKeysTest {

	@Test
	void configuredWhenBothVariablesHaveValues() {
		CodefApiKeys keys = CodefApiKeys.fromEnvironment(
				Map.of("CODEF_CLIENT_ID", "synthetic-a", "CODEF_CLIENT_SECRET", "synthetic-b"));

		assertThat(keys.isConfigured()).isTrue();
		assertThat(keys.missingVariables()).isEmpty();
		assertThat(keys).hasToString("CodefApiKeys[configured]");
	}

	@Test
	void notConfiguredWhenEitherVariableIsMissingOrBlank() {
		assertThat(CodefApiKeys.fromEnvironment(Map.of()).missingVariables()).containsExactly("CODEF_CLIENT_ID",
				"CODEF_CLIENT_SECRET");
		assertThat(CodefApiKeys.fromEnvironment(Map.of("CODEF_CLIENT_ID", "synthetic-a")).missingVariables())
			.containsExactly("CODEF_CLIENT_SECRET");
		assertThat(CodefApiKeys.fromEnvironment(Map.of("CODEF_CLIENT_ID", " ", "CODEF_CLIENT_SECRET", "synthetic-b"))
			.missingVariables()).containsExactly("CODEF_CLIENT_ID");
	}

	@Test
	void notConfiguredKeysRefuseAuthorizationHeaderAndHideOtherValue() {
		CodefApiKeys keys = CodefApiKeys.fromEnvironment(Map.of("CODEF_CLIENT_ID", "synthetic-a"));

		assertThat(keys.isConfigured()).isFalse();
		assertThat(keys).hasToString("CodefApiKeys[not configured]");
		assertThatThrownBy(keys::basicAuthorization).isInstanceOf(IllegalStateException.class)
			.hasMessageNotContaining("synthetic-a");
	}

}
