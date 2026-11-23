package com.beone.api.codef;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import com.beone.api.codef.application.ConnectedIdKeySource;
import com.beone.api.codef.application.FakeCodefGateway;
import com.beone.api.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.web.client.RestClient;

/**
 * connectedId 암호화 키가 없으면 연결이 안전하게 실패한다(2026-11-18 사용자 직접 결정 P5: 평문 저장 대체 금지, 502
 * {@code CODEF_UNAVAILABLE}). 실행 셸의 환경 변수와 무관하게 키가 없는 공급을 주입한다(키 해석 규칙은
 * {@code EnvironmentConnectedIdKeySourceTest}).
 */
@IntegrationTest
@ActiveProfiles("local")
@Import(CodefConnectionWithoutKeyIntegrationTest.NoKeyConfiguration.class)
class CodefConnectionWithoutKeyIntegrationTest {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	FakeCodefGateway gateway;

	@AfterEach
	void clearRows() {
		this.jdbcTemplate.update("DELETE FROM codef_connections");
	}

	@Test
	void connectionFailsWithoutCallingCodefAndStoresNothing() {
		int active = this.gateway.activeCount();

		ResponseEntity<String> response = RestClient.builder()
			.baseUrl("http://localhost:" + this.port)
			.defaultStatusHandler(HttpStatusCode::isError, (request, failure) -> {
			})
			.build()
			.post()
			.uri("/api/v1/me/codef/connection")
			.header("X-BeONE-Demo-User", "MY_CARDS")
			.contentType(MediaType.APPLICATION_JSON)
			.body("{\"organization\":\"0301\",\"loginType\":\"ID\",\"loginId\":\"kbuser01\",\"password\":\"pw\"}")
			.retrieve()
			.toEntity(String.class);

		assertThat(response.getStatusCode().value()).isEqualTo(502);
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch("""
				{"code":"CODEF_UNAVAILABLE","message":"CODEF 응답을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.","errors":[]}
				""", response.getBody());
		assertThat(this.gateway.activeCount()).isEqualTo(active);
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM codef_connections", Integer.class)).isZero();
	}

	/** 키가 없는 공급. */
	@TestConfiguration(proxyBeanMethods = false)
	static class NoKeyConfiguration {

		@Bean
		@Primary
		ConnectedIdKeySource noConnectedIdKey() {
			return Optional::empty;
		}

	}

}
