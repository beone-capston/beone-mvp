package com.beone.api.codef.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

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
 * CODEF 연결 GET·POST를 Testcontainers MySQL(V10 적용, {@code ddl-auto: validate})과 가짜 게이트웨이로 확인한다.
 *
 * <p>패키지 전용 {@link ConnectedIdCipher}로 저장값을 복호화해 가짜 게이트웨이가 발급한 값과 비교하려고 이 패키지에 둔다. connectedId 암호화
 * 키는 테스트 설정의 합성 키다(2026-11-18 사용자 직접 결정 P5). 기대값은 docs/api/codef.md(최초 201·재연결 200·해제 204, connectedId·아이디·비밀번호는 응답에 없음, 연결 없으면
 * NOT_CONNECTED, 해제 뒤 409 CODEF_NOT_CONNECTED)와 관리자 판단·사용자 결정(재연결 성공 시 교체·실패 시 기존 유지, 해제는 연결 행만 삭제,
 * 가상 포트폴리오는 쓰기 409, 사용자별 분리 D-09)에서 썼다. 실 CODEF는 부르지 않는다.
 */
@IntegrationTest
@ActiveProfiles("local")
@Import(CodefConnectionIntegrationTest.ClockConfiguration.class)
class CodefConnectionIntegrationTest {

	private static final String PATH = "/api/v1/me/codef/connection";

	private static final Instant START = Instant.parse("2026-11-04T00:00:00Z");

	private static final byte[] KEY = new byte[32];

	private static final AtomicReference<Instant> NOW = new AtomicReference<>(START);

	private static final String NOT_CONNECTED = """
			{"data":{"organization":"0301","status":"NOT_CONNECTED","connectedAt":null,"lastSyncedAt":null,
			 "reconnectRequired":false}}
			""";

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	FakeCodefGateway gateway;

	@BeforeEach
	@AfterEach
	void clearRows() {
		NOW.set(START);
		this.jdbcTemplate.update("DELETE FROM codef_connections");
	}

	@Test
	void tableHoldsNoCredentialColumns() {
		assertThat(this.jdbcTemplate.queryForList("SELECT COLUMN_NAME FROM information_schema.COLUMNS"
				+ " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'codef_connections' ORDER BY ORDINAL_POSITION",
				String.class))
			.containsExactly("user_id", "organization", "connected_id_ciphertext", "status", "connected_at",
					"last_synced_at");
	}

	@Test
	void connectThenReconnectReplacesTheConnectedIdAndNeverExposesIt() {
		assertJson(send("GET", "MY_CARDS", null), NOT_CONNECTED, 200);
		int active = this.gateway.activeCount();

		ResponseEntity<String> first = send("POST", "MY_CARDS", body("kbuser01", "p@ss-word-9"));
		assertJson(first, """
				{"data":{"organization":"0301","status":"CONNECTED","connectedAt":"2026-11-04T09:00:00+09:00",
				 "lastSyncedAt":null,"reconnectRequired":false}}
				""", 201);
		String firstStored = storedConnectedId();
		assertThat(firstStored).startsWith("v1:").doesNotContain("fake-cid-");
		String firstId = decrypt(firstStored);
		assertThat(firstId).startsWith("fake-cid-");
		assertThat(this.gateway.isActive(firstId)).isTrue();
		assertThat(first.getBody()).doesNotContain("fake-cid-").doesNotContain("kbuser01").doesNotContain("p@ss-word-9");
		assertThat(this.gateway.activeCount()).isEqualTo(active + 1);
		assertJson(send("GET", "MY_CARDS", null), first.getBody(), 200);

		NOW.set(START.plusSeconds(3600));
		ResponseEntity<String> second = send("POST", "MY_CARDS", body("kbuser01", "p@ss-word-9"));

		assertJson(second, """
				{"data":{"organization":"0301","status":"CONNECTED","connectedAt":"2026-11-04T10:00:00+09:00",
				 "lastSyncedAt":null,"reconnectRequired":false}}
				""", 200);
		// 저장값이 가짜 게이트웨이가 새로 발급한 connectedId로 바뀌고, 이전 값은 정리된다
		String secondId = decrypt(storedConnectedId());
		assertThat(secondId).startsWith("fake-cid-").isNotEqualTo(firstId);
		assertThat(this.gateway.isActive(secondId)).isTrue();
		assertThat(this.gateway.isActive(firstId)).isFalse();
		assertThat(this.gateway.activeCount()).isEqualTo(active + 1);
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM codef_connections", Integer.class))
			.isEqualTo(1);
	}

	@Test
	void disconnectRemovesOnlyTheConnectionAndReleasesItThenReportsNotConnected() {
		int active = this.gateway.activeCount();
		send("POST", "MY_CARDS", body("kbuser01", "pw"));
		String connectedId = decrypt(storedConnectedId());
		int cards = this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_cards", Integer.class);

		ResponseEntity<String> deleted = send("DELETE", "MY_CARDS", null);

		assertThat(deleted.getStatusCode().value()).isEqualTo(204);
		assertThat(deleted.getBody()).isNull();
		assertThat(this.gateway.isActive(connectedId)).isFalse();
		assertThat(this.gateway.activeCount()).isEqualTo(active);
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM codef_connections", Integer.class)).isZero();
		// 해제는 데이터 완전 삭제가 아니다. 보유 카드 등 다른 행은 그대로다(P4)
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_cards", Integer.class)).isEqualTo(cards);
		assertJson(send("GET", "MY_CARDS", null), NOT_CONNECTED, 200);
		assertJson(send("DELETE", "MY_CARDS", null),
				"{\"code\":\"CODEF_NOT_CONNECTED\",\"message\":\"CODEF가 연결되어 있지 않습니다.\",\"errors\":[]}", 409);
		assertThat(send("POST", "MY_CARDS", body("kbuser01", "pw")).getStatusCode().value()).isEqualTo(201);
	}

	@Test
	void currentUserCodefConnectedFollowsTheConnectionRow() {
		// #136: GET /api/v1/me codefConnected는 연결 행 존재다. RECONNECT_REQUIRED도 연결이 있는 상태이고, 가상 포트폴리오는 항상 false
		assertThat(codefConnected("MY_CARDS")).isFalse();
		send("POST", "MY_CARDS", body("kbuser01", "pw"));
		assertThat(codefConnected("MY_CARDS")).isTrue();
		assertThat(codefConnected("VIRTUAL_PORTFOLIO")).isFalse();

		this.jdbcTemplate.update("UPDATE codef_connections SET status = 'RECONNECT_REQUIRED'");
		assertThat(codefConnected("MY_CARDS")).isTrue();

		send("DELETE", "MY_CARDS", null);
		assertThat(codefConnected("MY_CARDS")).isFalse();
	}

	@Test
	void failedReconnectionsKeepTheExistingConnection() {
		send("POST", "MY_CARDS", body("kbuser01", "pw"));
		String connected = send("GET", "MY_CARDS", null).getBody();
		String stored = storedConnectedId();
		String connectedId = decrypt(stored);
		int active = this.gateway.activeCount();

		assertJson(send("POST", "MY_CARDS", body(FakeCodefGateway.LOGIN_REJECTED_ID, "pw")),
				"{\"code\":\"CODEF_LOGIN_FAILED\",\"message\":\"카드사 로그인에 실패했습니다.\",\"errors\":[]}", 400);
		assertJson(send("POST", "MY_CARDS", body(FakeCodefGateway.CARD_VERIFICATION_ID, "pw")), """
				{"code":"CODEF_CARD_VERIFICATION_NOT_SUPPORTED",
				 "message":"카드소지확인이 필요한 계정은 지원하지 않습니다. 수동 입력을 이용해 주세요.","errors":[]}
				""", 409);
		assertJson(send("POST", "MY_CARDS", body(FakeCodefGateway.UNAVAILABLE_ID, "pw")), """
				{"code":"CODEF_UNAVAILABLE","message":"CODEF 응답을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.","errors":[]}
				""", 502);

		assertThat(storedConnectedId()).isEqualTo(stored);
		assertThat(this.gateway.isActive(connectedId)).isTrue();
		assertThat(this.gateway.activeCount()).isEqualTo(active);
		assertJson(send("GET", "MY_CARDS", null), connected, 200);
	}

	@Test
	void failedFirstConnectionStoresNothing() {
		send("POST", "MY_CARDS", body(FakeCodefGateway.LOGIN_REJECTED_ID, "pw"));

		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM codef_connections", Integer.class))
			.isZero();
		assertJson(send("GET", "MY_CARDS", null), NOT_CONNECTED, 200);
	}

	@Test
	void portfolioUserCannotConnectAndDoesNotSeeTheOtherUsersConnection() {
		send("POST", "MY_CARDS", body("kbuser01", "pw"));

		String readOnly = "{\"code\":\"PORTFOLIO_READ_ONLY\",\"message\":\"가상 포트폴리오에서는 CODEF 연결을 바꿀 수 없습니다.\",\"errors\":[]}";
		assertJson(send("POST", "VIRTUAL_PORTFOLIO", body("kbuser01", "pw")), readOnly, 409);
		assertJson(send("DELETE", "VIRTUAL_PORTFOLIO", null), readOnly, 409);
		assertJson(send("GET", "VIRTUAL_PORTFOLIO", null), NOT_CONNECTED, 200);
		List<Map<String, Object>> rows = this.jdbcTemplate.queryForList(
				"SELECT u.demo_user FROM codef_connections c JOIN users u ON u.user_id = c.user_id");
		assertThat(rows).extracting(row -> row.get("demo_user")).containsExactly("MY_CARDS");
	}

	private String decrypt(String stored) {
		long userId = this.jdbcTemplate.queryForObject("SELECT user_id FROM codef_connections", Long.class);
		return new ConnectedIdCipher(KEY).decrypt(userId, stored);
	}

	private String storedConnectedId() {
		return this.jdbcTemplate.queryForObject("SELECT connected_id_ciphertext FROM codef_connections", String.class);
	}

	private static String body(String loginId, String password) {
		return """
				{"organization":"0301","loginType":"ID","loginId":"%s","password":"%s"}
				""".formatted(loginId, password);
	}

	private static void assertJson(ResponseEntity<String> response, String expected, int status) {
		assertThat(response.getStatusCode().value()).isEqualTo(status);
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch(expected, response.getBody());
	}

	private boolean codefConnected(String demoUser) {
		ResponseEntity<String> me = RestClient.create("http://localhost:" + this.port)
			.get()
			.uri("/api/v1/me")
			.header("X-BeONE-Demo-User", demoUser)
			.retrieve()
			.toEntity(String.class);
		assertThat(me.getStatusCode().value()).isEqualTo(200);
		assertThat(me.getBody()).containsAnyOf("\"codefConnected\":true", "\"codefConnected\":false");
		return me.getBody().contains("\"codefConnected\":true");
	}

	private ResponseEntity<String> send(String method, String demoUser, String body) {
		RestClient client = RestClient.builder()
			.baseUrl("http://localhost:" + this.port)
			.defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
			})
			.build();
		RestClient.RequestBodySpec spec = client.method(HttpMethod.valueOf(method))
			.uri(PATH)
			.header("X-BeONE-Demo-User", demoUser);
		if (body != null) {
			spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
		}
		return spec.retrieve().toEntity(String.class);
	}

	/** 연결 시각을 테스트가 옮길 수 있는 시계와 합성 암호화 키. */
	@TestConfiguration(proxyBeanMethods = false)
	static class ClockConfiguration {

		@Bean
		@Primary
		ConnectedIdKeySource syntheticConnectedIdKey() {
			return () -> Optional.of(KEY.clone());
		}

		@Bean
		Clock movableClock() {
			return new MovableClock(ZoneOffset.UTC);
		}

	}

	private static final class MovableClock extends Clock {

		private final ZoneId zone;

		MovableClock(ZoneId zone) {
			this.zone = zone;
		}

		@Override
		public ZoneId getZone() {
			return this.zone;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return new MovableClock(zone);
		}

		@Override
		public Instant instant() {
			return NOW.get();
		}

	}

}
