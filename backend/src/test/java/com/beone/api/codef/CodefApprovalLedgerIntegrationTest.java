package com.beone.api.codef;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import com.beone.api.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * CODEF 승인 행의 원장 조회 표시와 전월 승인 합계 추정값(INFERRED) 미리 채우기·확인 저장(사용자 결정 2026-11-27 T4, #289)을 Testcontainers
 * MySQL(V18)과 실제 HTTP로 확인한다.
 *
 * <p>동기화·승인 행·불완전 표시는 동기화 API 대신 표에 직접 넣는다(동기화 저장은 {@code CodefSyncIntegrationTest}가 확인한다). 금액·시각·지문은
 * 합성 값이다. 시계는 서울 2026-11-17 10:30으로 고정한다.
 */
@IntegrationTest
@ActiveProfiles("local")
@Import(CodefApprovalLedgerIntegrationTest.FixedClockConfiguration.class)
class CodefApprovalLedgerIntegrationTest {

	private static final String HEADER = "X-BeONE-Demo-User";

	private static final String OCTOBER = "\"period\":{\"from\":\"2026-10-01\",\"to\":\"2026-10-31\"}";

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbcTemplate;

	private long userId;

	private long cardId;

	private long syncId;

	private int fingerprint;

	@BeforeEach
	@AfterEach
	void clearRows() {
		this.jdbcTemplate.update("DELETE FROM monthly_states");
		this.jdbcTemplate.update("DELETE FROM codef_approval_rows");
		this.jdbcTemplate.update("DELETE FROM codef_sync_incomplete_marks");
		this.jdbcTemplate.update("DELETE FROM codef_held_cards");
		this.jdbcTemplate.update("DELETE FROM codef_syncs");
		this.jdbcTemplate.update("DELETE FROM user_cards WHERE holding_source <> 'SYNTHETIC'");
	}

	// 보유 카드 하나를 CODEF 카드로 만들고, 10월 전체(조회일 11-17, 전날까지)를 덮는 승인 판정 동기화를 둔다
	private String codefCardWithOctoberSync() {
		ResponseEntity<String> created = send("POST", "/api/v1/me/cards",
				"{\"displayName\":\"올라운드\",\"cardProductId\":\"09271\"}");
		assertThat(created.getStatusCode().value()).isEqualTo(201);
		String card = (String) data(created.getBody()).get("userCardId");
		this.cardId = Long.parseLong(card.substring("uc-".length()));
		this.userId = this.jdbcTemplate.queryForObject("SELECT user_id FROM user_cards WHERE user_card_id = ?",
				Long.class, this.cardId);
		this.jdbcTemplate.update("""
				INSERT INTO codef_syncs (user_id, period_from, period_to, synced_at, approvals_judged)
				VALUES (?, '2026-10-01', '2026-11-16', '2026-11-17 09:00:00', TRUE)""", this.userId);
		this.syncId = this.jdbcTemplate.queryForObject("SELECT MAX(sync_id) FROM codef_syncs", Long.class);
		this.jdbcTemplate.update("INSERT INTO codef_held_cards (user_card_id, user_id, card_name, issue_date, first_sync_id)"
				+ " VALUES (?, ?, 'SYN CARD', NULL, ?)", this.cardId, this.userId, this.syncId);
		return card;
	}

	private long approvalRow(String kind, String date, long amount) {
		this.fingerprint++;
		this.jdbcTemplate.update("""
				INSERT INTO codef_approval_rows (user_id, user_card_id, sync_id, card_name, approval_fingerprint,
				  used_date, used_time, signed_amount, row_kind, recorded_at)
				VALUES (?, ?, ?, 'SYN CARD', ?, ?, '12:30:05', ?, ?, '2026-11-17 09:00:00')""", this.userId, this.cardId,
				this.syncId, String.format("%064x", this.fingerprint), date, amount, kind);
		return this.jdbcTemplate.queryForObject("SELECT MAX(codef_approval_row_id) FROM codef_approval_rows",
				Long.class);
	}

	private void mark(Long card, String month, String reason) {
		this.jdbcTemplate.update("""
				INSERT INTO codef_sync_incomplete_marks (sync_id, user_id, user_card_id, used_month, reason, row_count)
				VALUES (?, ?, ?, ?, ?, 1)""", this.syncId, this.userId, card, month, reason);
	}

	@Test
	void codefRowsAppearInTheLedgerAndTheEstimateIsConfirmedAsManual() {
		String card = codefCardWithOctoberSync();
		long first = approvalRow("APPROVAL", "2026-10-02", 120000);
		approvalRow("APPROVAL", "2026-10-20", 392000);
		approvalRow("APPROVAL", "2026-11-03", 5000);

		// A) 10월 CODEF 원장: 출처 CODEF 행, 원문 식별자 없음, 10월 전체를 덮어 완전
		String october = send("GET", "/api/v1/me/ledger-transactions?month=2026-10&source=CODEF&userCardId=" + card,
				null).getBody();
		List<Map<String, Object>> items = list(data(october).get("items"));
		assertThat(items).extracting(item -> item.get("ledgerTransactionId"), item -> item.get("amountWon"))
			.containsExactly(org.assertj.core.groups.Tuple.tuple("car-" + first, 120000),
					org.assertj.core.groups.Tuple.tuple("car-" + (first + 1), 392000));
		assertThat(items.get(0)).containsEntry("source", "CODEF").containsEntry("entryType", "APPROVAL")
			.containsEntry("transactionTime", "12:30:05").containsEntry("syncId", "sync-" + this.syncId)
			.containsEntry("fullyCanceled", false).containsEntry("merchantName", null);
		assertThat(october).doesNotContain("SYN CARD").doesNotContain("0000000000000001");
		assertThat(list(data(october).get("coverage"))).singleElement()
			.satisfies(c -> assertThat(c).containsEntry("complete", true).containsEntry("incompleteReasons", List.of()));
		// 이번 달은 동기화가 11-16까지만 덮어 불완전
		assertThat(list(data(send("GET", "/api/v1/me/ledger-transactions?month=2026-11&source=CODEF", null).getBody())
			.get("coverage")))
			.filteredOn(c -> ("uc-" + this.cardId).equals(c.get("userCardId")))
			.singleElement()
			.satisfies(c -> assertThat(c).containsEntry("complete", false)
				.containsEntry("incompleteReasons", List.of("PERIOD_NOT_COVERED")));

		// B) 11월 월간 상태: 전월 승인 합계 추정값을 보여 주되 전월 실적은 미입력
		String path = "/api/v1/me/cards/" + card + "/monthly-states/2026-11";
		Map<String, Object> before = previous(send("GET", path, null).getBody());
		assertThat(before).containsEntry("source", "UNKNOWN").containsEntry("referencedInferred", null);
		assertThat(map(before.get("inferredReference"))).containsEntry("label", "카드사 승인내역 합계(추정)")
			.containsEntry("source", "INFERRED").containsEntry("month", "2026-10").containsEntry("totalWon", 512000)
			.containsEntry("complete", true).containsEntry("basisSyncId", "sync-" + this.syncId)
			.containsEntry("basisSyncedAt", "2026-11-17T09:00:00+09:00").containsEntry("usedInCalculation", false);

		// 확인 저장: 값은 사용자가 정한 MANUAL, 참고한 합계·산출 시각이 남는다
		ResponseEntity<String> saved = send("PUT", path, """
				{"previousMonthPerformanceWon":{"value":512000,%s,"referencedInferredSyncId":"sync-%d"}}
				""".formatted(OCTOBER, this.syncId));
		assertThat(saved.getStatusCode().value()).isEqualTo(200);
		assertThat(previous(saved.getBody())).containsEntry("source", "MANUAL").containsEntry("usable", true);
		assertThat(map(previous(saved.getBody()).get("referencedInferred")))
			.containsEntry("basisSyncId", "sync-" + this.syncId).containsEntry("totalWon", 512000)
			.containsEntry("computedAt", "2026-11-17T10:30:00+09:00");
		assertThat(this.jdbcTemplate.queryForMap("""
				SELECT previous_month_won, previous_month_inferred_won, previous_month_inferred_sync_id
				FROM monthly_states WHERE user_card_id = ?""", this.cardId))
			.containsEntry("previous_month_won", 512000L).containsEntry("previous_month_inferred_won", 512000L)
			.containsEntry("previous_month_inferred_sync_id", this.syncId);

		// 불완전 행이 생기면 추정값은 null과 사유이고 0이 아니며, 그 근거로는 저장할 수 없다
		mark(this.cardId, "2026-10", "UNDETERMINED_ROW");
		Map<String, Object> incomplete = map(previous(send("GET", path, null).getBody()).get("inferredReference"));
		assertThat(incomplete).containsEntry("totalWon", null).containsEntry("complete", false)
			.containsEntry("incompleteReasons", List.of("UNDETERMINED_ROW"));
		// 수동 값은 추정값과 상관없이 계산에 쓸 수 있다(Q2)
		assertThat(previous(send("GET", path, null).getBody())).containsEntry("usable", true);
		ResponseEntity<String> rejected = send("PUT", path, """
				{"previousMonthPerformanceWon":{"value":512000,%s,"referencedInferredSyncId":"sync-%d"}}
				""".formatted(OCTOBER, this.syncId));
		assertThat(rejected.getStatusCode().value()).isEqualTo(400);
		assertThat(rejected.getBody()).contains("previousMonthPerformanceWon.referencedInferredSyncId");
	}

	@Test
	void cancellationsBlockTheEstimateAndUnlinkedMarksAreShownSeparately() {
		String card = codefCardWithOctoberSync();
		approvalRow("APPROVAL", "2026-10-02", 120000);
		approvalRow("APPROVAL_FULLY_CANCELED", "2026-10-05", 30000);
		approvalRow("CANCELLATION", "2026-10-09", -5000);
		mark(null, "2026-10", "UNDETERMINED_ROW");

		String october = send("GET", "/api/v1/me/ledger-transactions?month=2026-10&source=CODEF", null).getBody();
		List<Map<String, Object>> items = list(data(october).get("items"));
		// Type1은 한 항목 APPROVAL + fullyCanceled, Type2 취소는 귀속월을 정하지 않는다(Q4, D-17)
		assertThat(items.get(1)).containsEntry("entryType", "APPROVAL").containsEntry("fullyCanceled", true);
		assertThat(items.get(2)).containsEntry("entryType", "CANCELLATION").containsEntry("amountWon", -5000);
		assertThat(map(items.get(2).get("attributionMonth"))).containsEntry("value", null)
			.containsEntry("status", "NEEDS_CONFIRMATION");
		// 카드 미연결 불완전 행은 이 카드를 불완전으로 만들지 않고 userCardId=null 항목으로만 보인다(Q5)
		List<Map<String, Object>> coverage = list(data(october).get("coverage"));
		assertThat(coverage).filteredOn(c -> card.equals(c.get("userCardId"))).singleElement()
			.satisfies(c -> assertThat(c).containsEntry("complete", true));
		assertThat(coverage).filteredOn(c -> c.get("userCardId") == null).singleElement()
			.satisfies(c -> assertThat(c).containsEntry("incompleteReasons", List.of("UNDETERMINED_ROW")));

		ResponseEntity<String> state = send("GET", "/api/v1/me/cards/" + card + "/monthly-states/2026-11", null);
		assertThat(state.getStatusCode().value()).as(state.getBody()).isEqualTo(200);
		Map<String, Object> estimate = map(previous(state.getBody()).get("inferredReference"));
		assertThat(estimate).containsEntry("totalWon", null)
			.containsEntry("incompleteReasons", List.of("CANCELLATION_ATTRIBUTION_UNCONFIRMED"));
	}

	@Test
	void manualCardsAndPreV18SyncsGiveNoEstimate() {
		String card = codefCardWithOctoberSync();
		// V18 전 동기화(approvals_judged NULL)만 있으면 그 달을 판단하지 않는다(Q6)
		this.jdbcTemplate.update("UPDATE codef_syncs SET approvals_judged = NULL WHERE sync_id = ?", this.syncId);
		assertThat(previous(send("GET", "/api/v1/me/cards/" + card + "/monthly-states/2026-11", null).getBody()))
			.containsEntry("inferredReference", null);
		assertThat(list(data(send("GET", "/api/v1/me/ledger-transactions?month=2026-10&source=CODEF&userCardId=" + card,
				null).getBody()).get("coverage"))).singleElement()
			.satisfies(c -> assertThat(c).containsEntry("incompleteReasons", List.of("PERIOD_NOT_COVERED")));

		this.jdbcTemplate.update("UPDATE codef_syncs SET approvals_judged = TRUE WHERE sync_id = ?", this.syncId);
		this.jdbcTemplate.update("DELETE FROM codef_held_cards");
		// CODEF 카드가 아니면 추정값도 CODEF 사유도 없다
		assertThat(previous(send("GET", "/api/v1/me/cards/" + card + "/monthly-states/2026-11", null).getBody()))
			.containsEntry("inferredReference", null);
	}

	private static Map<String, Object> previous(String body) {
		return map(data(body).get("previousMonthPerformanceWon"));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> map(Object value) {
		return (Map<String, Object>) value;
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> list(Object value) {
		return (List<Map<String, Object>>) value;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> data(String body) {
		return (Map<String, Object>) JsonMapper.builder().build().readValue(body, Map.class).get("data");
	}

	private ResponseEntity<String> send(String method, String path, String body) {
		RestClient client = RestClient.builder()
			.baseUrl("http://localhost:" + this.port)
			.defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
			})
			.build();
		RestClient.RequestBodySpec spec = client.method(HttpMethod.valueOf(method)).uri(path).header(HEADER, "MY_CARDS");
		if (body != null) {
			spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
		}
		return spec.retrieve().toEntity(String.class);
	}

	/** 시각을 서울 2026-11-17 10:30으로 고정한다. */
	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClockConfiguration {

		@Bean
		Clock fixedClock() {
			return Clock.fixed(Instant.parse("2026-11-17T01:30:00Z"), ZoneOffset.UTC);
		}

	}

}
