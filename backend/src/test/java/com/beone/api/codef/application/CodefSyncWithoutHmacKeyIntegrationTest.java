package com.beone.api.codef.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.beone.api.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.web.client.RestClient;

/**
 * 승인번호 HMAC 키가 없을 때(2026-11-18 사용자 직접 결정 P6). 승인내역은 조회·저장하지 않고 {@code partialFailures}의 {@code APPROVALS}로 알린다.
 * 건수는 {@code null}로 두어 0건 수집으로 보이지 않게 한다. 보유카드·실적은 그대로 처리한다. 셸 환경과 무관하게 빈 키 공급을 주입한다.
 */
@IntegrationTest
@ActiveProfiles("local")
@Import(CodefSyncWithoutHmacKeyIntegrationTest.NoHmacKeyConfiguration.class)
class CodefSyncWithoutHmacKeyIntegrationTest {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void clearRows() {
		this.jdbcTemplate.update("DELETE FROM codef_approval_rows");
		this.jdbcTemplate.update("DELETE FROM codef_performance_references");
		this.jdbcTemplate.update("DELETE FROM codef_held_cards");
		this.jdbcTemplate.update("DELETE FROM codef_sync_incomplete_marks");
		this.jdbcTemplate.update("DELETE FROM codef_syncs");
		this.jdbcTemplate.update("DELETE FROM user_cards WHERE holding_source = 'CODEF'");
		this.jdbcTemplate.update("DELETE FROM codef_connections");
	}

	@Test
	void approvalsArePartialFailureAndNothingIsStored() {
		assertThat(send("/api/v1/me/codef/connection",
				"{\"organization\":\"0301\",\"loginType\":\"ID\",\"loginId\":\"kbuser01\",\"password\":\"pw\"}")
			.getStatusCode()
			.value()).isEqualTo(201);

		ResponseEntity<String> response = send("/api/v1/me/codef/syncs", null);

		assertThat(response.getStatusCode().value()).isEqualTo(200);
		JsonAssert.comparator(JsonCompareMode.LENIENT).assertIsMatch("""
				{"data":{"heldCards":{"fetched":3},
				 "approvals":{"imported":null,"skippedDuplicates":null,"incompleteRows":[]},
				 "partialFailures":[{"target":"APPROVALS","code":"CODEF_UNAVAILABLE",
				  "message":"CODEF 응답을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요."}]}}
				""", response.getBody());
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM codef_approval_rows", Integer.class)).isZero();
		// 승인 조회 실패로 남겨 그 기간을 CODEF 원장 완전성·추정 합계 판단에 쓰지 않는다(V18, #289)
		assertThat(this.jdbcTemplate.queryForObject("SELECT approvals_judged FROM codef_syncs", Boolean.class)).isFalse();
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM codef_sync_incomplete_marks", Integer.class))
			.isZero();
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_cards WHERE holding_source = 'CODEF'",
				Integer.class))
			.isEqualTo(2);
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM codef_performance_references",
				Integer.class))
			.isEqualTo(1);
	}

	private ResponseEntity<String> send(String path, String body) {
		RestClient client = RestClient.builder()
			.baseUrl("http://localhost:" + this.port)
			.defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
			})
			.build();
		RestClient.RequestBodySpec spec = client.method(HttpMethod.POST)
			.uri(path)
			.header("X-BeONE-Demo-User", "MY_CARDS");
		if (body != null) {
			spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
		}
		return spec.retrieve().toEntity(String.class);
	}

	/** connectedId 키는 합성 값, HMAC 키는 없음. */
	@TestConfiguration(proxyBeanMethods = false)
	static class NoHmacKeyConfiguration {

		@Bean
		@Primary
		ConnectedIdKeySource syntheticConnectedIdKey() {
			return () -> Optional.of(new byte[32]);
		}

		@Bean
		@Primary
		ApprovalFingerprintKeySource noApprovalKey() {
			return Optional::empty;
		}

		@Bean
		Clock fixedClock() {
			return Clock.fixed(Instant.parse("2026-11-04T00:00:00Z"), ZoneOffset.UTC);
		}

	}

}
