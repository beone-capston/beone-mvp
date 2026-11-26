package com.beone.api.auth.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.beone.api.auth.web.MemberApiTestClient.Session;
import com.beone.api.codef.application.ApprovalFingerprintKeySource;
import com.beone.api.codef.application.CodefPasswordEncryptor;
import com.beone.api.codef.application.ConnectedIdKeySource;
import com.beone.api.codef.transport.CodefApi;
import com.beone.api.codef.transport.CodefApiKeys;
import com.beone.api.codef.transport.CodefCallBudget;
import com.beone.api.codef.transport.CodefClient;
import com.beone.api.codef.transport.CodefStubServer;
import com.beone.api.support.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static com.beone.api.auth.web.MemberApiTestClient.CSRF_INVALID;
import static com.beone.api.auth.web.MemberApiTestClient.assertBody;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code member,codef-live}에서 실 게이트웨이를 회원 세션·CSRF·MySQL과 함께 확인한다(LP-1, 결정 38-8, R8).
 *
 * <p>CODEF는 루프백 스텁 HTTP로만 대신한다. 전송 계층 클라이언트를 스텁 주소와 합성 키로 만든 빈으로 바꾸고(테스트 전용 주입), CODEF 공개키·
 * connectedId 암호화 키·승인번호 HMAC 키도 합성 값이다. 기본 클라이언트는 테스트 작업이 키 환경 변수를 지우므로 어떤 요청도 보내지 않는다.
 * 실제 CODEF 호스트로 나간 요청이 없음은 스텁이 받은 요청 수 전체로 확인한다. 응답 본문은 가짜 게이트웨이의 합성 fixture를 쓴다. 스텁
 * 클라이언트도 DB 호출 총량 빈({@code JdbcCodefCallBudget}, LP-2)을 거친다.
 */
@IntegrationTest
@ActiveProfiles({ "member", "codef-live" })
// 호출 총량(LP-2)은 설정이 없으면 닫힘이라, 시험 시계(2026-11-18)를 포함하는 창과 검증 환경 권장값(API별 3)을 준다
@TestPropertySource(properties = { "beone.codef.budget.per-api=3", "beone.codef.budget.window-start=2026-11-01T00:00:00Z",
		"beone.codef.budget.window-end=2026-12-01T00:00:00Z" })
@Import({ AuthSessionIntegrationTest.MutableClockConfiguration.class,
		CodefLiveMemberIntegrationTest.StubCodefConfiguration.class })
class CodefLiveMemberIntegrationTest {

	private static final CodefStubServer STUB = startStub();

	private static final KeyPair KEYS = generateKeys();

	private static final Instant START = Instant.parse("2026-11-18T01:00:00Z");

	private static final String MEMBER_A = "usr-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

	private static final String MEMBER_B = "usr-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

	private static final String CONNECTION = "/api/v1/me/codef/connection";

	private static final String SYNCS = "/api/v1/me/codef/syncs";

	private static final String CREATE = "/v1/account/create";

	private static final String DELETE = "/v1/account/delete";

	private static final String CONNECTED_ID = "synthetic-live-connected-id";

	private static final String PASSWORD = "synthetic-card-pass";

	private static final String CONNECT_BODY = "{\"organization\":\"0301\",\"loginType\":\"ID\",\"loginId\":\"kbuser01\","
			+ "\"password\":\"" + PASSWORD + "\"}";

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	AuthSessionIntegrationTest.MutableClock clock;

	private MemberApiTestClient members;

	@BeforeEach
	void setUp() {
		this.members = new MemberApiTestClient(this.port, this.jdbcTemplate);
		clearTables();
		this.clock.set(START);
		// 스텁 서버는 클래스가 같이 쓰므로 시험마다 요청 기록을 비운다
		STUB.clearRequests();
		STUB.respond(CodefStubServer.TOKEN_PATH, 200, "{\"access_token\":\"synthetic-token\"}");
		STUB.respond(CREATE, 200, "{\"result\":{\"code\":\"CF-00000\"},\"data\":{\"connectedId\":\"" + CONNECTED_ID + "\"}}");
		STUB.respond(DELETE, 200, "{\"result\":{\"code\":\"CF-00000\"},\"data\":{}}");
		STUB.respond(CodefApi.HELD_CARDS.path(), 200, fixture("held-cards"));
		STUB.respond(CodefApi.APPROVALS.path(), 200, fixture("approvals").replace("{{USED_DATE}}", "20261117"));
		STUB.respond(CodefApi.CARD_PERFORMANCES.path(), 200,
				fixture("performances").replace("{{PREVIOUS_START}}", "20261001").replace("{{PREVIOUS_END}}", "20261031"));
	}

	@AfterEach
	void clearTables() {
		for (String table : new String[] { "codef_approval_rows", "codef_performance_references", "codef_held_cards",
				"codef_syncs" }) {
			this.jdbcTemplate.update("DELETE FROM " + table);
		}
		this.jdbcTemplate.update("DELETE FROM user_cards WHERE holding_source = 'CODEF'");
		this.jdbcTemplate.update("DELETE FROM codef_connections");
		this.jdbcTemplate.update("DELETE FROM codef_call_budgets");
		this.members.clearMemberData();
	}

	@AfterAll
	static void stopStub() {
		STUB.close();
	}

	@Test
	void memberConnectsSyncsAndDisconnectsThroughTheStubOnly() {
		Session a = this.members.loginNew(MEMBER_A, "a@example.com");
		Session b = this.members.loginNew(MEMBER_B, "b@example.com");
		int ledgerRows = count("SELECT COUNT(*) FROM ledger_transactions");

		// CSRF 없이 쓰면 403이고 CODEF를 부르지 않는다
		assertBody(this.members.send(HttpMethod.POST, CONNECTION, a.withoutCsrf(), CONNECT_BODY), 403, CSRF_INVALID);
		assertThat(STUB.requestsTo(CREATE)).isEmpty();

		ResponseEntity<String> connected = this.members.send(HttpMethod.POST, CONNECTION, a, CONNECT_BODY);
		assertThat(connected.getStatusCode().value()).isEqualTo(201);
		assertThat(connected.getBody()).contains("\"status\":\"CONNECTED\"").doesNotContain(CONNECTED_ID);
		// 비밀번호는 암호문으로만 나가고 connectedId는 암호문으로만 저장한다
		assertThat(STUB.requestsTo(CREATE)).singleElement().satisfies(r -> assertThat(r.body()).doesNotContain(PASSWORD));
		assertThat(this.jdbcTemplate.queryForObject("SELECT connected_id_ciphertext FROM codef_connections c"
				+ " JOIN users u ON u.user_id = c.user_id WHERE u.member_id = ?", String.class, MEMBER_A))
			.doesNotContain(CONNECTED_ID);

		ResponseEntity<String> synced = this.members.send(HttpMethod.POST, SYNCS, a, null);
		assertThat(synced.getStatusCode().value()).isEqualTo(200);
		assertThat(synced.getBody()).contains("\"partialFailures\":[]").doesNotContain(CONNECTED_ID);
		assertThat(STUB.requestsTo(CodefApi.HELD_CARDS.path())).singleElement()
			.satisfies(r -> assertThat(r.body()).contains(CONNECTED_ID));
		assertThat(STUB.requestsTo(CodefApi.APPROVALS.path())).hasSize(1);
		assertThat(STUB.requestsTo(CodefApi.CARD_PERFORMANCES.path())).hasSize(1);
		assertThat(count("SELECT COUNT(*) FROM user_cards uc JOIN users u ON u.user_id = uc.user_id"
				+ " WHERE u.member_id = ? AND uc.holding_source = 'CODEF'", MEMBER_A)).isPositive();
		// CODEF 저장은 전용 테이블에만 하고 앱 원장은 바뀌지 않는다(R8)
		assertThat(count("SELECT COUNT(*) FROM ledger_transactions")).isEqualTo(ledgerRows);

		// 다른 회원에게는 연결이 없다. 동기화는 409이고 CODEF를 부르지 않는다
		assertThat(this.members.send(HttpMethod.GET, CONNECTION, b, null).getBody())
			.contains("\"status\":\"NOT_CONNECTED\"");
		assertThat(this.members.send(HttpMethod.POST, SYNCS, b, null).getStatusCode().value()).isEqualTo(409);
		assertThat(count("SELECT COUNT(*) FROM user_cards uc JOIN users u ON u.user_id = uc.user_id"
				+ " WHERE u.member_id = ? AND uc.holding_source = 'CODEF'", MEMBER_B)).isZero();

		assertThat(this.members.send(HttpMethod.DELETE, CONNECTION, a, null).getStatusCode().value()).isEqualTo(204);
		assertThat(STUB.requestsTo(DELETE)).singleElement()
			.satisfies(r -> assertThat(r.body()).contains(CONNECTED_ID).doesNotContain(PASSWORD));

		// 등록 1, 상품 3, 삭제 1 외에는 아무 요청도 없다. 토큰은 클라이언트 빈이 캐시하므로 앞선 시험이 발급했으면 0건이다
		assertThat(STUB.requests()).extracting(CodefStubServer.Received::path)
			.filteredOn(path -> !path.equals(CodefStubServer.TOKEN_PATH))
			.containsExactly(CREATE, CodefApi.HELD_CARDS.path(), CodefApi.APPROVALS.path(),
					CodefApi.CARD_PERFORMANCES.path(), DELETE);
		assertThat(STUB.requestsTo(CodefStubServer.TOKEN_PATH)).hasSizeLessThanOrEqualTo(1);
	}

	@Test
	void serverErrorsDuringSyncAreNotRetried() {
		Session a = this.members.loginNew(MEMBER_A, "a@example.com");
		assertThat(this.members.send(HttpMethod.POST, CONNECTION, a, CONNECT_BODY).getStatusCode().value())
			.isEqualTo(201);
		for (CodefApi api : CodefApi.values()) {
			STUB.respond(api.path(), 500, "");
		}

		ResponseEntity<String> synced = this.members.send(HttpMethod.POST, SYNCS, a, null);

		assertThat(synced.getStatusCode().value()).isEqualTo(502);
		assertThat(synced.getBody()).contains("\"code\":\"CODEF_UNAVAILABLE\"");
		assertThat(STUB.requestsTo(CodefApi.HELD_CARDS.path())).hasSize(1);
		assertThat(STUB.requestsTo(CodefApi.APPROVALS.path())).hasSize(1);
		assertThat(STUB.requestsTo(CodefApi.CARD_PERFORMANCES.path())).hasSize(1);
	}

	@Test
	void controlCharactersAreRejectedBeforeAnyCodefCallOrBudgetUse() {
		Session a = this.members.loginNew(MEMBER_A, "a@example.com");
		String badLoginId = "{\"organization\":\"0301\",\"loginType\":\"ID\",\"loginId\":\"kbuser01\\u0000\","
				+ "\"password\":\"" + PASSWORD + "\"}";
		String badPassword = "{\"organization\":\"0301\",\"loginType\":\"ID\",\"loginId\":\"kbuser01\","
				+ "\"password\":\"" + PASSWORD + "\\n\"}";

		for (String body : List.of(badLoginId, badPassword)) {
			ResponseEntity<String> rejected = this.members.send(HttpMethod.POST, CONNECTION, a, body);
			assertThat(rejected.getStatusCode().value()).isEqualTo(400);
			assertThat(rejected.getBody()).contains("\"code\":\"INVALID_REQUEST\"").doesNotContain(PASSWORD).doesNotContain("kbuser01");
		}
		// 토큰·계정 등록 호출 0, 호출 총량 행 0, 연결 행 0
		assertThat(STUB.requests()).isEmpty();
		assertThat(count("SELECT COUNT(*) FROM codef_call_budgets")).isZero();
		assertThat(count("SELECT COUNT(*) FROM codef_connections")).isZero();
	}

	private int count(String sql, Object... args) {
		return this.jdbcTemplate.queryForObject(sql, Integer.class, args);
	}

	private static String fixture(String name) {
		try (InputStream in = CodefLiveMemberIntegrationTest.class.getResourceAsStream("/codef/fake/" + name + ".json")) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static CodefStubServer startStub() {
		try {
			return new CodefStubServer();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static KeyPair generateKeys() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** 스텁 주소·합성 키로 만든 전송 계층과 합성 공개키·암호화 키. 실 키·실 호스트는 쓰지 않는다. */
	@TestConfiguration
	static class StubCodefConfiguration {

		@Bean
		@Primary
		CodefClient stubCodefClient(CodefCallBudget budget) {
			return new CodefClient(
					CodefApiKeys.fromEnvironment(
							Map.of("CODEF_CLIENT_ID", "synthetic-id", "CODEF_CLIENT_SECRET", "synthetic-value")),
					STUB.settings(Duration.ofSeconds(5)), budget);
		}

		@Bean
		CodefPasswordEncryptor syntheticCodefPublicKey() {
			return CodefPasswordEncryptor.of(KEYS.getPublic());
		}

		@Bean
		@Primary
		ConnectedIdKeySource syntheticConnectedIdKey() {
			return () -> Optional.of(new byte[32]);
		}

		@Bean
		@Primary
		ApprovalFingerprintKeySource syntheticApprovalKey() {
			byte[] key = new byte[32];
			for (int i = 0; i < key.length; i++) {
				key[i] = (byte) (i + 1);
			}
			return () -> Optional.of(key.clone());
		}

	}

}
