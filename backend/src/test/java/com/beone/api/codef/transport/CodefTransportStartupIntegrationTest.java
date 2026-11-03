package com.beone.api.codef.transport;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

import com.beone.api.support.IntegrationTest;

/**
 * 키 없는 환경에서 서버가 뜨는지 확인한다. Gradle이 테스트 프로세스에서 CODEF 환경 변수를 뺀다.
 */
@IntegrationTest
class CodefTransportStartupIntegrationTest {

	@LocalServerPort
	int port;

	@Autowired
	CodefClient client;

	@Test
	void serverStartsWithoutKeysAndCallFailsWithoutSending() {
		assertThat(System.getenv("CODEF_CLIENT_ID")).isNull();
		assertThat(System.getenv("CODEF_CLIENT_SECRET")).isNull();

		String health = RestClient.create("http://localhost:" + port).get().uri("/actuator/health").retrieve().body(String.class);
		assertThat(health).contains("\"status\":\"UP\"");

		var result = (CodefCallResult.Failed<?>) client.startObservation().heldCards(Map.of());
		assertThat(result.reason()).isEqualTo(CodefCallResult.Reason.CREDENTIALS_MISSING);
	}

}
