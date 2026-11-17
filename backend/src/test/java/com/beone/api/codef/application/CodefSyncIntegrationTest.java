package com.beone.api.codef.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
import org.springframework.dao.DuplicateKeyException;
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
 * CODEF 동기화 POST를 Testcontainers MySQL(V12 적용, {@code ddl-auto: validate})과 가짜 게이트웨이의 합성 fixture로 확인한다.
 *
 * <p>connectedId 암호화 키와 승인번호 HMAC 키는 테스트 설정의 합성 키다(P5·P6). 기대값은 docs/api/codef.md(보유카드 → 매핑 후보, 승인내역 →
 * CODEF 원장, 실적 → 참고값·usedInCalculation=false, 재조회 중복 금지, 부분 실패 200·전체 실패 502, 409 실패)와 관리자 판단
 * K1(UNMAPPED·candidateCardProductId=null)·K2(codef 전용 테이블, 원장 불변)·P2·P8·P9·Q1~Q8, 결정 20 R2(승인번호 원문 저장 금지),
 * D-09(사용자별 분리)에서 썼다. 실 CODEF는 부르지 않는다.
 */
@IntegrationTest
@ActiveProfiles("local")
@Import(CodefSyncIntegrationTest.SyntheticConfiguration.class)
class CodefSyncIntegrationTest {

	private static final String CONNECTION = "/api/v1/me/codef/connection";

	private static final String SYNCS = "/api/v1/me/codef/syncs";

	private static final Instant START = Instant.parse("2026-11-04T00:00:00Z");

	private static final AtomicReference<Instant> NOW = new AtomicReference<>(START);

	private static final String CARD_A = "가짜KB 테스트카드 A";

	private static final String CARD_B = "가짜KB 테스트카드 B";

	private static final String LONG_CARD = "가짜KB 테스트카드 이름이 서른 글자를 넘도록 길게 지은 합성 카드";

	private static final String UNAVAILABLE = """
			{"code":"CODEF_UNAVAILABLE","message":"CODEF 응답을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.","errors":[]}
			""";

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void clearRows() {
		NOW.set(START);
		this.jdbcTemplate.update("DELETE FROM codef_approval_rows");
		this.jdbcTemplate.update("DELETE FROM codef_performance_references");
		this.jdbcTemplate.update("DELETE FROM codef_held_cards");
		this.jdbcTemplate.update("DELETE FROM codef_syncs");
		this.jdbcTemplate.update("DELETE FROM user_cards WHERE holding_source = 'CODEF'");
		this.jdbcTemplate.update("DELETE FROM codef_connections");
	}

	@Test
	void firstSyncCreatesUnmappedCardsAndStoresOnlyAcceptedApprovals() {
		connect("MY_CARDS", "kbuser01");
		int ledgerRows = count("SELECT COUNT(*) FROM ledger_transactions");

		ResponseEntity<String> response = send("POST", SYNCS, "MY_CARDS", null);

		long cardA = cardId(CARD_A);
		long cardB = cardId(CARD_B);
		assertJson(response, """
				{"data":{"syncId":"sync-%1$d","syncedAt":"2026-11-04T09:00:00+09:00",
				 "heldCards":{"fetched":3,"newCandidates":[
				  {"userCardId":"uc-%2$d","cardName":"%4$s","candidateCardProductId":null,"mappingStatus":"UNMAPPED"},
				  {"userCardId":"uc-%3$d","cardName":"%5$s","candidateCardProductId":null,"mappingStatus":"UNMAPPED"}]},
				 "approvals":{"imported":3,"skippedDuplicates":0,"incompleteRows":[
				  {"userCardId":"uc-%2$d","approvalNoPresent":true,"reason":"PARTIAL_CANCELLATION_SUSPECTED"},
				  {"userCardId":null,"approvalNoPresent":true,"reason":"UNDETERMINED_ROW"}]},
				 "performances":[{"userCardId":"uc-%2$d","label":"카드사 조회 실적(참고)",
				  "period":{"from":"2026-10-01","to":"2026-10-31"},"currentUseWon":120000,
				  "fetchedAt":"2026-11-04T09:00:00+09:00","usedInCalculation":false}],
				 "partialFailures":[]}}
				""".formatted(syncId(), cardA, cardB, CARD_A, CARD_B), 200);

		// 30자 넘는 카드는 보유 카드로 만들지 않고 이름을 자르지도 않는다(Q2)
		assertThat(this.jdbcTemplate.queryForList("SELECT display_name, holding_source, mapping_status"
				+ " FROM user_cards WHERE holding_source = 'CODEF' ORDER BY user_card_id"))
			.containsExactly(
					Map.of("display_name", CARD_A, "holding_source", "CODEF", "mapping_status", "UNMAPPED"),
					Map.of("display_name", CARD_B, "holding_source", "CODEF", "mapping_status", "UNMAPPED"));
		assertThat(count("SELECT COUNT(*) FROM user_cards WHERE holding_source = 'CODEF'"
				+ " AND (card_product_id IS NOT NULL OR confirmation_method IS NOT NULL)")).isZero();
		assertThat(count("SELECT COUNT(*) FROM codef_held_cards WHERE card_name = ?", LONG_CARD)).isZero();
		// 받아들인 행만 저장: A 정상, B 정상과 짝지은 Type2 취소. 부분취소 의심·건너뛴 카드의 행은 저장하지 않는다
		assertThat(this.jdbcTemplate.queryForList("SELECT user_card_id, row_kind, signed_amount FROM codef_approval_rows"
				+ " ORDER BY used_time"))
			.containsExactly(Map.of("user_card_id", cardA, "row_kind", "APPROVAL", "signed_amount", 12000L),
					Map.of("user_card_id", cardB, "row_kind", "APPROVAL", "signed_amount", 30000L),
					Map.of("user_card_id", cardB, "row_kind", "CANCELLATION", "signed_amount", -30000L));
		// 승인번호 원문은 어디에도 없고 64자 HMAC 지문만 있다(결정 20 R2, P6)
		assertThat(this.jdbcTemplate.queryForList("SELECT approval_fingerprint FROM codef_approval_rows", String.class))
			.allSatisfy(value -> assertThat(value).matches("[0-9a-f]{64}"));
		assertThat(count("SELECT COUNT(*) FROM codef_approval_rows WHERE approval_fingerprint LIKE '%FAKE%'")).isZero();
		assertThat(response.getBody()).doesNotContain("FAKE-APR").doesNotContain("fake-cid-");
		// 원장 행은 바뀌지 않는다(K2). 새 카드는 원장 조회 coverage에 나오지만 CODEF는 완전하다고 선언하지 않는다(R7)
		assertThat(count("SELECT COUNT(*) FROM ledger_transactions")).isEqualTo(ledgerRows);
		String ledger = send("GET", "/api/v1/me/ledger-transactions?month=2026-11&source=CODEF", "MY_CARDS", null)
			.getBody();
		assertThat(ledger).contains("\"items\":[]")
			.contains("{\"userCardId\":\"uc-" + cardA + "\",\"source\":\"CODEF\",\"period\":{\"from\":\"2026-11-01\","
					+ "\"to\":\"2026-11-30\"},\"complete\":false,\"incompleteReasons\":[]}")
			.doesNotContain("\"complete\":true");
		// 실적은 참고값으로만 저장하고 월간 상태에 넣지 않는다(D-16)
		assertThat(this.jdbcTemplate.queryForList("SELECT user_card_id, period_from, period_to, current_use_won"
				+ " FROM codef_performance_references"))
			.singleElement()
			.satisfies(row -> {
				assertThat(row.get("user_card_id")).isEqualTo(cardA);
				assertThat(row.get("current_use_won")).isEqualTo(120000L);
			});
		assertThat(count("SELECT COUNT(*) FROM monthly_states WHERE user_card_id IN (?, ?)", cardA, cardB)).isZero();
		assertThat(send("GET", CONNECTION, "MY_CARDS", null).getBody())
			.contains("\"lastSyncedAt\":\"2026-11-04T09:00:00+09:00\"");
	}

	@Test
	void newCodefCardsAppearAsUnmappedCodefHoldingsInTheCardList() {
		connect("MY_CARDS", "kbuser01");
		send("POST", SYNCS, "MY_CARDS", null);

		String cards = send("GET", "/api/v1/me/cards", "MY_CARDS", null).getBody();

		assertThat(cards).contains("\"userCardId\":\"uc-" + cardId(CARD_A) + "\"")
			.contains("\"displayName\":\"" + CARD_A + "\"")
			.contains("\"holdingSource\":\"CODEF\"")
			.doesNotContain(LONG_CARD);
	}

	@Test
	void resyncSkipsRecordedRowsAndDoesNotDuplicateCards() {
		connect("MY_CARDS", "kbuser01");
		send("POST", SYNCS, "MY_CARDS", null);
		long cardA = cardId(CARD_A);
		long cardB = cardId(CARD_B);
		NOW.set(START.plusSeconds(3600));

		ResponseEntity<String> again = send("POST", SYNCS, "MY_CARDS", null);

		// 불완전 행은 저장하지 않았으므로 다시 판정돼 같은 사유로 나온다
		assertJson(again, """
				{"data":{"syncId":"sync-%1$d","syncedAt":"2026-11-04T10:00:00+09:00",
				 "heldCards":{"fetched":3,"newCandidates":[]},
				 "approvals":{"imported":0,"skippedDuplicates":3,"incompleteRows":[
				  {"userCardId":"uc-%2$d","approvalNoPresent":true,"reason":"PARTIAL_CANCELLATION_SUSPECTED"},
				  {"userCardId":null,"approvalNoPresent":true,"reason":"UNDETERMINED_ROW"}]},
				 "performances":[{"userCardId":"uc-%2$d","label":"카드사 조회 실적(참고)",
				  "period":{"from":"2026-10-01","to":"2026-10-31"},"currentUseWon":120000,
				  "fetchedAt":"2026-11-04T10:00:00+09:00","usedInCalculation":false}],
				 "partialFailures":[]}}
				""".formatted(syncId(), cardA, cardB), 200);
		assertThat(count("SELECT COUNT(*) FROM user_cards WHERE holding_source = 'CODEF'")).isEqualTo(2);
		assertThat(count("SELECT COUNT(*) FROM codef_approval_rows")).isEqualTo(3);
		assertThat(count("SELECT COUNT(*) FROM codef_syncs")).isEqualTo(2);
		assertThat(count("SELECT COUNT(*) FROM codef_performance_references")).isEqualTo(2);
	}

	@Test
	void duplicateKeyDistinguishesTrailingSpacesInCardNames() {
		connect("MY_CARDS", "kbuser01");
		send("POST", SYNCS, "MY_CARDS", null);
		String copy = "INSERT INTO codef_approval_rows (user_id, user_card_id, sync_id, card_name, approval_fingerprint, "
				+ "used_date, used_time, signed_amount, row_kind, recorded_at) SELECT user_id, user_card_id, sync_id, %s, "
				+ "approval_fingerprint, used_date, used_time, signed_amount, row_kind, recorded_at "
				+ "FROM codef_approval_rows ORDER BY codef_approval_row_id LIMIT 1";

		// 후행 공백만 다른 카드명은 다른 키다(NO PAD 정렬). 완전히 같은 키는 계속 막는다
		this.jdbcTemplate.update(copy.formatted("CONCAT(card_name, ' ')"));
		assertThat(count("SELECT COUNT(*) FROM codef_approval_rows")).isEqualTo(4);
		assertThatThrownBy(() -> this.jdbcTemplate.update(copy.formatted("card_name")))
			.isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void releasedCodefCardIsNotReusedAndANewCardIsCreated() {
		connect("MY_CARDS", "kbuser01");
		send("POST", SYNCS, "MY_CARDS", null);
		long cardA = cardId(CARD_A);
		assertThat(send("DELETE", "/api/v1/me/cards/uc-" + cardA, "MY_CARDS", null).getStatusCode().value())
			.isEqualTo(204);

		send("POST", SYNCS, "MY_CARDS", null);

		assertThat(this.jdbcTemplate.queryForList("SELECT user_card_id FROM user_cards WHERE holding_source = 'CODEF'"
				+ " AND display_name = ? AND released_at IS NULL", Long.class, CARD_A))
			.singleElement()
			.isNotEqualTo(cardA);
	}

	@Test
	void partialFailureIs200AndFailedTargetsAreNotDisguisedAsEmpty() {
		connect("MY_CARDS", FakeCodefGateway.SYNC_PARTIAL_ID);

		ResponseEntity<String> response = send("POST", SYNCS, "MY_CARDS", null);

		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(response.getBody()).contains("\"performances\":[]")
			.contains("\"partialFailures\":[{\"target\":\"PERFORMANCES\",\"code\":\"CODEF_UNAVAILABLE\","
					+ "\"message\":\"CODEF 응답을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.\"}]")
			.contains("\"imported\":3");
		assertThat(count("SELECT COUNT(*) FROM codef_performance_references")).isZero();
	}

	@Test
	void totalFailureIs502AndStoresNothing() {
		connect("MY_CARDS", FakeCodefGateway.SYNC_UNAVAILABLE_ID);

		assertJson(send("POST", SYNCS, "MY_CARDS", null), UNAVAILABLE, 502);
		assertThat(count("SELECT COUNT(*) FROM codef_syncs")).isZero();
		assertThat(count("SELECT COUNT(*) FROM user_cards WHERE holding_source = 'CODEF'")).isZero();
		assertThat(send("GET", CONNECTION, "MY_CARDS", null).getBody()).contains("\"lastSyncedAt\":null");
	}

	@Test
	void invalidConnectionMarksReconnectRequired() {
		connect("MY_CARDS", FakeCodefGateway.SYNC_RECONNECT_ID);
		String reconnect = """
				{"code":"CODEF_RECONNECT_REQUIRED","message":"CODEF 재연결이 필요합니다.","errors":[]}
				""";

		assertJson(send("POST", SYNCS, "MY_CARDS", null), reconnect, 409);
		assertThat(send("GET", CONNECTION, "MY_CARDS", null).getBody())
			.contains("\"status\":\"RECONNECT_REQUIRED\"")
			.contains("\"reconnectRequired\":true");
		assertJson(send("POST", SYNCS, "MY_CARDS", null), reconnect, 409);
		assertThat(count("SELECT COUNT(*) FROM codef_syncs")).isZero();

		// 다시 연결하면 CONNECTED로 돌아오고 동기화된다
		connect("MY_CARDS", "kbuser01");
		assertThat(send("POST", SYNCS, "MY_CARDS", null).getStatusCode().value()).isEqualTo(200);
	}

	@Test
	void notConnectedPortfolioAndPeriodFailuresStoreNothing() {
		assertJson(send("POST", SYNCS, "MY_CARDS", null),
				"{\"code\":\"CODEF_NOT_CONNECTED\",\"message\":\"CODEF가 연결되어 있지 않습니다.\",\"errors\":[]}", 409);
		assertJson(send("POST", SYNCS, "VIRTUAL_PORTFOLIO", null), """
				{"code":"PORTFOLIO_READ_ONLY","message":"가상 포트폴리오에서는 CODEF 연결을 바꿀 수 없습니다.","errors":[]}
				""", 409);
		connect("MY_CARDS", "kbuser01");
		assertJson(send("POST", SYNCS, "MY_CARDS", "{\"to\":\"2026-11-05\"}"), """
				{"code":"INVALID_REQUEST","message":"요청 값을 확인해 주세요.",
				 "errors":[{"field":"to","message":"오늘보다 늦을 수 없습니다."}]}
				""", 400);
		assertJson(send("POST", SYNCS, "MY_CARDS", "{\"from\":\"2025-11-04\",\"to\":\"2026-11-04\"}"), """
				{"code":"INVALID_REQUEST","message":"요청 값을 확인해 주세요.",
				 "errors":[{"field":null,"message":"조회 기간은 최대 12개월입니다."}]}
				""", 400);
		assertThat(count("SELECT COUNT(*) FROM codef_syncs")).isZero();
	}

	@Test
	void requestedStartDateIsPassedToTheGateway() {
		connect("MY_CARDS", "kbuser01");

		send("POST", SYNCS, "MY_CARDS", "{\"from\":\"2025-11-05\",\"to\":\"2026-11-04\"}");

		// 가짜 게이트웨이는 승인 사용일자를 요청 시작일로 채운다
		assertThat(this.jdbcTemplate.queryForList("SELECT DISTINCT used_date FROM codef_approval_rows", String.class))
			.containsExactly("2025-11-05");
		assertThat(this.jdbcTemplate.queryForList("SELECT period_from, period_to FROM codef_syncs"))
			.singleElement()
			.satisfies(row -> {
				assertThat(row.get("period_from")).hasToString("2025-11-05");
				assertThat(row.get("period_to")).hasToString("2026-11-04");
			});
	}

	@Test
	void otherDemoUserSeesNoneOfTheSyncedData() {
		connect("MY_CARDS", "kbuser01");
		String before = send("GET", "/api/v1/me/cards", "VIRTUAL_PORTFOLIO", null).getBody();

		send("POST", SYNCS, "MY_CARDS", null);

		assertThat(send("GET", "/api/v1/me/cards", "VIRTUAL_PORTFOLIO", null).getBody()).isEqualTo(before)
			.doesNotContain(CARD_A);
		long myCards = this.jdbcTemplate.queryForObject("SELECT user_id FROM users WHERE demo_user = 'MY_CARDS'",
				Long.class);
		assertThat(count("SELECT COUNT(*) FROM codef_approval_rows WHERE user_id <> ?", myCards)).isZero();
		assertThat(count("SELECT COUNT(*) FROM user_cards WHERE holding_source = 'CODEF' AND user_id <> ?", myCards))
			.isZero();
	}

	private void connect(String demoUser, String loginId) {
		int status = send("POST", CONNECTION, demoUser,
				"{\"organization\":\"0301\",\"loginType\":\"ID\",\"loginId\":\"" + loginId + "\",\"password\":\"pw\"}")
			.getStatusCode()
			.value();
		assertThat(status).isIn(200, 201);
	}

	private long cardId(String cardName) {
		return this.jdbcTemplate.queryForObject("SELECT user_card_id FROM codef_held_cards WHERE card_name = ?",
				Long.class, cardName);
	}

	private long syncId() {
		return this.jdbcTemplate.queryForObject("SELECT MAX(sync_id) FROM codef_syncs", Long.class);
	}

	private int count(String sql, Object... args) {
		return this.jdbcTemplate.queryForObject(sql, Integer.class, args);
	}

	private static void assertJson(ResponseEntity<String> response, String expected, int status) {
		assertThat(response.getStatusCode().value()).isEqualTo(status);
		assertThat(response.getHeaders().getContentType()).isNotNull();
		assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch(expected, response.getBody());
	}

	private ResponseEntity<String> send(String method, String path, String demoUser, String body) {
		RestClient client = RestClient.builder()
			.baseUrl("http://localhost:" + this.port)
			.defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
			})
			.build();
		RestClient.RequestBodySpec spec = client.method(HttpMethod.valueOf(method))
			.uri(path)
			.header("X-BeONE-Demo-User", demoUser);
		if (body != null) {
			spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
		}
		return spec.retrieve().toEntity(String.class);
	}

	/** 테스트가 옮길 수 있는 시계와 합성 키 두 개. */
	@TestConfiguration(proxyBeanMethods = false)
	static class SyntheticConfiguration {

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
