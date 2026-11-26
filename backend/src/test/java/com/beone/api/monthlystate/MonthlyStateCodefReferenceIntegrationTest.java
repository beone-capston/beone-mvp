package com.beone.api.monthlystate;

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
 * CODEF 실적 참고값 미리 채우기·확인 시 MANUAL 저장·재조회 충돌(D-16, 사용자 결정 2026-11-27, #281)을 Testcontainers MySQL에서 V17과
 * 실제 HTTP로 확인한다.
 *
 * <p>참고값 행은 동기화 API 대신 저장소 표에 직접 넣는다(동기화는 CODEF 쪽 통합 테스트가 확인한다). 금액·시각은 합성 값이다. 시계는 서울
 * 2026-11-17 10:30으로 고정한다.
 */
@IntegrationTest
@ActiveProfiles("local")
@Import(MonthlyStateCodefReferenceIntegrationTest.FixedClockConfiguration.class)
class MonthlyStateCodefReferenceIntegrationTest {

	private static final String HEADER = "X-BeONE-Demo-User";

	private static final String CARDS = "/api/v1/me/cards";

	private static final String OCTOBER = "\"period\":{\"from\":\"2026-10-01\",\"to\":\"2026-10-31\"}";

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void clearRows() {
		this.jdbcTemplate.update("DELETE FROM monthly_states");
		this.jdbcTemplate.update("DELETE FROM codef_performance_references");
		this.jdbcTemplate.update("DELETE FROM codef_syncs");
		this.jdbcTemplate.update("DELETE FROM user_cards WHERE holding_source <> 'SYNTHETIC'");
	}

	@Test
	void referenceIsPrefilledConfirmedAsManualAndConflictsAgainAfterADifferentRefetch() {
		String card = register("{\"displayName\":\"올라운드\",\"cardProductId\":\"09271\"}");
		long cardId = Long.parseLong(card.substring("uc-".length()));
		long userId = this.jdbcTemplate.queryForObject("SELECT user_id FROM user_cards WHERE user_card_id = ?",
				Long.class, cardId);
		long syncId = sync(userId);
		long first = reference(userId, cardId, syncId, "2026-10-01", "2026-10-31", 518000L, "2026-11-04 09:01:00");
		// 당월 조회분(종료일=조회일 전날)과 카드 미연결 참고값은 전월 실적 후보가 아니다
		reference(userId, cardId, syncId, "2026-11-01", "2026-11-16", 120000L, "2026-11-17 09:00:00");
		reference(userId, null, syncId, "2026-10-01", "2026-10-31", 999000L, "2026-11-05 09:00:00");
		String path = CARDS + "/" + card + "/monthly-states/2026-11";

		// 1) 입력 전: 참고값만 보이고 전월 실적은 미입력(계산 미사용)
		Map<String, Object> before = previous(send("GET", path, null).getBody());
		assertThat(before).containsEntry("value", null).containsEntry("source", "UNKNOWN")
			.containsEntry("usable", false).containsEntry("reason", "NOT_ENTERED").containsEntry("referencedCodef", null);
		assertThat(map(before.get("codefReference"))).containsEntry("referenceId", "cpr-" + first)
			.containsEntry("label", "카드사 조회 실적(참고)").containsEntry("inquiryMonth", "2026-10")
			.containsEntry("currentUseWon", 518000).containsEntry("fetchedAt", "2026-11-04T09:01:00+09:00")
			.containsEntry("usedInCalculation", false);

		// 2) 참고해 확인: 값은 사용자가 정한 MANUAL, 참고한 값이 같이 남고 충돌이 아니다
		ResponseEntity<String> confirmed = send("PUT", path, """
				{"previousMonthPerformanceWon":{"value":510000,%s,"referencedCodefId":"cpr-%d"}}
				""".formatted(OCTOBER, first));
		assertThat(confirmed.getStatusCode().value()).isEqualTo(200);
		Map<String, Object> saved = previous(confirmed.getBody());
		assertThat(saved).containsEntry("value", 510000).containsEntry("source", "MANUAL").containsEntry("usable", true);
		assertThat(map(saved.get("referencedCodef"))).containsEntry("referenceId", "cpr-" + first)
			.containsEntry("syncId", "sync-" + syncId).containsEntry("currentUseWon", 518000)
			.containsEntry("fetchedAt", "2026-11-04T09:01:00+09:00");
		assertThat(data(confirmed.getBody()).get("conflicts")).isEqualTo(List.of());
		assertThat(this.jdbcTemplate.queryForMap("""
				SELECT previous_month_won, previous_month_codef_reference_id, previous_month_codef_sync_id,
				       previous_month_codef_won FROM monthly_states WHERE user_card_id = ?""", cardId))
			.containsEntry("previous_month_won", 510000L).containsEntry("previous_month_codef_reference_id", first)
			.containsEntry("previous_month_codef_sync_id", syncId).containsEntry("previous_month_codef_won", 518000L);

		// 3) 재조회로 다른 값이 오면 다시 충돌이고 계산에서 빠진다
		long second = reference(userId, cardId, syncId, "2026-10-01", "2026-10-31", 530000L, "2026-11-20 08:00:00");
		String conflicted = send("GET", path, null).getBody();
		assertThat(previous(conflicted)).containsEntry("value", 510000).containsEntry("usable", false)
			.containsEntry("reason", "SOURCE_CONFLICT");
		List<Map<String, Object>> conflicts = list(data(conflicted).get("conflicts"));
		assertThat(conflicts).hasSize(1);
		assertThat(conflicts.get(0)).containsEntry("item", "previousMonthPerformanceWon").containsEntry("selection", null);
		assertThat(list(conflicts.get(0).get("candidates"))).extracting(c -> c.get("source"), c -> c.get("value"))
			.containsExactly(org.assertj.core.groups.Tuple.tuple("MANUAL", 510000),
					org.assertj.core.groups.Tuple.tuple("CODEF", 530000));
		// 계산 기준 선택은 여전히 409이고 저장하지 않는다(Q3)
		assertThat(send("PUT", path + "/source-selections", """
				{"item":"previousMonthPerformanceWon",%s,"selectedSource":"CODEF","conflictVersion":"%s"}
				""".formatted(OCTOBER, conflicts.get(0).get("conflictVersion"))).getStatusCode().value()).isEqualTo(409);

		// 4) 해소: 최신 참고값을 보고 다시 저장한다
		ResponseEntity<String> resolved = send("PUT", path, """
				{"previousMonthPerformanceWon":{"value":525000,%s,"referencedCodefId":"cpr-%d"}}
				""".formatted(OCTOBER, second));
		assertThat(previous(resolved.getBody())).containsEntry("usable", true).containsEntry("reason", null);
		assertThat(data(resolved.getBody()).get("conflicts")).isEqualTo(List.of());

		// 5) 참고값 행이 지워져도 참고 스냅샷은 남고 ID만 비운다(ON DELETE SET NULL)
		this.jdbcTemplate.update("DELETE FROM codef_performance_references WHERE codef_performance_reference_id = ?",
				second);
		Map<String, Object> afterDelete = map(previous(send("GET", path, null).getBody()).get("referencedCodef"));
		assertThat(afterDelete).containsEntry("referenceId", null).containsEntry("currentUseWon", 530000);
	}

	@Test
	void manualValueWithoutReferenceConflictsAndInvalidReferencesAreRejected() {
		String card = register("{\"displayName\":\"올라운드\",\"cardProductId\":\"09271\"}");
		String other = register("{\"displayName\":\"노리\",\"cardProductId\":\"01664\"}");
		long cardId = Long.parseLong(card.substring("uc-".length()));
		long otherId = Long.parseLong(other.substring("uc-".length()));
		long userId = this.jdbcTemplate.queryForObject("SELECT user_id FROM user_cards WHERE user_card_id = ?",
				Long.class, cardId);
		long syncId = sync(userId);
		reference(userId, cardId, syncId, "2026-10-01", "2026-10-31", 518000L, "2026-11-04 09:01:00");
		long otherCard = reference(userId, otherId, syncId, "2026-10-01", "2026-10-31", 1000L, "2026-11-04 09:01:00");
		long otherPeriod = reference(userId, cardId, syncId, "2026-09-01", "2026-09-30", 1000L, "2026-11-04 09:01:00");
		String path = CARDS + "/" + card + "/monthly-states/2026-11";

		// Q1: 참고 없이 입력한 값도 같은 기간의 다른 참고값과 다르면 충돌
		String saved = send("PUT", path, "{\"previousMonthPerformanceWon\":{\"value\":500000," + OCTOBER + "}}")
			.getBody();
		assertThat(previous(saved)).containsEntry("reason", "SOURCE_CONFLICT");
		assertThat(list(data(saved).get("conflicts"))).hasSize(1);

		// 다른 카드·다른 기간·없는 참고값은 400이고 저장된 값은 그대로다
		for (long id : new long[] { otherCard, otherPeriod, 9_999_999L }) {
			ResponseEntity<String> rejected = send("PUT", path, """
					{"previousMonthPerformanceWon":{"value":1,%s,"referencedCodefId":"cpr-%d"}}
					""".formatted(OCTOBER, id));
			assertThat(rejected.getStatusCode().value()).as("reference %d", id).isEqualTo(400);
			assertThat(rejected.getBody()).contains("previousMonthPerformanceWon.referencedCodefId");
		}
		assertThat(previous(send("GET", path, null).getBody())).containsEntry("value", 500000);
	}

	private long sync(long userId) {
		this.jdbcTemplate.update(
				"INSERT INTO codef_syncs (user_id, period_from, period_to, synced_at) VALUES (?, '2026-10-01', '2026-11-16', '2026-11-04 09:01:00')",
				userId);
		return this.jdbcTemplate.queryForObject("SELECT MAX(sync_id) FROM codef_syncs", Long.class);
	}

	private long reference(long userId, Long cardId, long syncId, String from, String to, Long won, String fetchedAt) {
		this.jdbcTemplate.update("""
				INSERT INTO codef_performance_references
				  (user_id, user_card_id, sync_id, card_name, period_from, period_to, current_use_won, fetched_at)
				VALUES (?, ?, ?, 'SYN CARD', ?, ?, ?, ?)""", userId, cardId, syncId, from, to, won, fetchedAt);
		return this.jdbcTemplate.queryForObject("SELECT MAX(codef_performance_reference_id) FROM codef_performance_references",
				Long.class);
	}

	private String register(String body) {
		ResponseEntity<String> created = send("POST", CARDS, body);
		assertThat(created.getStatusCode().value()).isEqualTo(201);
		return (String) data(created.getBody()).get("userCardId");
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

	/** 입력 시각을 서울 2026-11-17 10:30으로 고정한다. */
	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClockConfiguration {

		@Bean
		Clock fixedClock() {
			return Clock.fixed(Instant.parse("2026-11-17T01:30:00Z"), ZoneOffset.UTC);
		}

	}

}
