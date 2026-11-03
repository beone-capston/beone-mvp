package com.beone.api.codef.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;

class CodefTransportSettingsTest {

	private static final Duration ONE = Duration.ofSeconds(1);

	@Test
	void defaultsMatchTransportContract() {
		CodefTransportSettings settings = CodefTransportSettings.defaults();

		assertThat(settings.tokenUri()).isEqualTo(URI.create("https://oauth.codef.io/oauth/token"));
		assertThat(settings.productHost()).isEqualTo(URI.create("https://sandbox.codef.io"));
		assertThat(settings.productResponseTimeout()).isEqualTo(Duration.ofSeconds(310));
		assertThat(settings.tokenResponseTimeout()).isEqualTo(Duration.ofSeconds(110));
		assertThat(settings.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
	}

	@Test
	void acceptsOfficialHostsOverHttps() {
		for (String host : new String[] { "https://sandbox.codef.io", "https://development.codef.io", "https://api.codef.io" }) {
			assertThat(settingsWithHost(host).productHost().toString()).isEqualTo(host);
		}
	}

	@Test
	void rejectsPlainHttpAndUnknownHosts() {
		for (String host : new String[] { "http://sandbox.codef.io", "https://example.com", "https://sandbox.codef.io.example.com" }) {
			assertThatThrownBy(() -> settingsWithHost(host)).isInstanceOf(IllegalArgumentException.class);
		}
		assertThatThrownBy(() -> new CodefTransportSettings(URI.create("https://sandbox.codef.io/oauth/token"),
				CodefTransportSettings.SANDBOX_HOST, ONE, ONE, ONE))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsNonPositiveTimeouts() {
		assertThatThrownBy(() -> new CodefTransportSettings(CodefTransportSettings.DEFAULT_TOKEN_URI,
				CodefTransportSettings.SANDBOX_HOST, Duration.ZERO, ONE, ONE))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private static CodefTransportSettings settingsWithHost(String host) {
		return new CodefTransportSettings(CodefTransportSettings.DEFAULT_TOKEN_URI, URI.create(host), ONE, ONE, ONE);
	}

}
