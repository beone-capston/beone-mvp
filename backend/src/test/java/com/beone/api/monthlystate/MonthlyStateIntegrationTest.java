package com.beone.api.monthlystate;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

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
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Testcontainers MySQL에서 V5 마이그레이션(ddl-auto: validate 통과)·월간 상태 교체 저장과 조회·매핑 409·규칙 ID 검사·달과 사용자별
 * 분리(D-09)를 실제 HTTP로 확인한다.
 *
 * <p>기대 형식은 docs/api/monthly-states.md, 계약에 없던 값은 결정 27 A3절이다. 시계는 서울 2026-11-17 10:30으로 고정한다. 보유 카드
 * 테스트가 수동 카드 행을 지우므로 이 테스트가 만든 월간 상태 행은 앞뒤로 지운다.
 */
@IntegrationTest
@ActiveProfiles("local")
@Import(MonthlyStateIntegrationTest.FixedClockConfiguration.class)
class MonthlyStateIntegrationTest {

	private static final String HEADER = "X-BeONE-Demo-User";

	private static final String CARDS = "/api/v1/me/cards";

	private static final String NOW = "2026-11-17T10:30:00+09:00";

	private static final String UNKNOWN_STATE = """
			{"value":null,"source":"UNKNOWN","period":null,"updatedAt":null,"usable":false,"reason":"NOT_ENTERED"}""";

	private static final String NOVEMBER = "\"period\":{\"from\":\"2026-11-01\",\"to\":\"2026-11-30\"}";

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void clearRows() {
		this.jdbcTemplate.update("DELETE FROM monthly_states");
		this.jdbcTemplate.update("DELETE FROM user_cards WHERE holding_source <> 'SYNTHETIC'");
	}

	@Test
	void monthlyStateMigrationIsApplied() {
		Boolean success = this.jdbcTemplate.queryForObject(
				"SELECT success FROM flyway_schema_history WHERE version = '5'", Boolean.class);

		assertThat(success).isTrue();
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM monthly_states", Integer.class))
			.isZero();
	}

	@Test
	void manualStateIsReplacedAndReadBackPerMonth() {
		String card = register("{\"displayName\":\"올라운드\",\"cardProductId\":\"09271\"}");
		String path = CARDS + "/" + card + "/monthly-states/2026-11";

		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch("""
				{"data":{"userCardId":"%s","month":"2026-11","snapshotAt":null,
				 "previousMonthPerformanceWon":{"value":null,"source":"UNKNOWN","period":null,"updatedAt":null,
				   "usable":false,"reason":"NOT_ENTERED","referencedCodef":null},
				 "limitBucketUsages":[],"otherBucketsUsedWon":%s,"selectedServices":[],
				 "serviceSelectionStatus":"UNKNOWN","noServiceSelection":null,"registeredOn":%s,
				 "conflicts":[],"afterSnapshot":null}}
				""".formatted(card, UNKNOWN_STATE, UNKNOWN_STATE), send("GET", path, "MY_CARDS", null).getBody());

		ResponseEntity<String> saved = send("PUT", path, "MY_CARDS", """
				{"previousMonthPerformanceWon":{"value":-12000,"period":{"from":"2026-10-01","to":"2026-10-31"}},
				 "limitBucketUsages":[{"limitBucketId":"STORE","value":0,%s},{"limitBucketId":"FUEL","value":4000,%s}],
				 "otherBucketsUsedWon":{"value":1500,%s},
				 "selectedServices":[{"serviceId":"STORE","effectiveFrom":"2026-10-15"},{"serviceId":"FUEL"}],
				 "registeredOn":{"value":"2026-10-02","period":{"from":"2026-10-02","to":"2026-10-02"}}}
				""".formatted(NOVEMBER, NOVEMBER, NOVEMBER));
		String manualBody = """
				"userCardId":"%1$s","month":"2026-11","snapshotAt":"%2$s",
				"previousMonthPerformanceWon":{"value":-12000,"source":"MANUAL",
				  "period":{"from":"2026-10-01","to":"2026-10-31"},"updatedAt":"%2$s","usable":true,"reason":null,
				  "referencedCodef":null},
				"limitBucketUsages":[
				  {"limitBucketId":"STORE","value":0,"source":"MANUAL","period":{"from":"2026-11-01","to":"2026-11-30"},
				   "updatedAt":"%2$s","usable":true,"reason":null},
				  {"limitBucketId":"FUEL","value":4000,"source":"MANUAL","period":{"from":"2026-11-01","to":"2026-11-30"},
				   "updatedAt":"%2$s","usable":true,"reason":null}],
				"otherBucketsUsedWon":{"value":1500,"source":"MANUAL","period":{"from":"2026-11-01","to":"2026-11-30"},
				  "updatedAt":"%2$s","usable":true,"reason":null},
				"selectedServices":[
				  {"serviceId":"STORE","effectiveFrom":"2026-10-15","source":"MANUAL","updatedAt":"%2$s","usable":true,
				   "reason":null},
				  {"serviceId":"FUEL","effectiveFrom":null,"source":"MANUAL","updatedAt":"%2$s","usable":false,
				   "reason":"NOT_ENTERED"}],
				"serviceSelectionStatus":"SELECTED","noServiceSelection":null,
				"registeredOn":{"value":"2026-10-02","source":"MANUAL","period":{"from":"2026-10-02","to":"2026-10-02"},
				  "updatedAt":"%2$s","usable":true,"reason":null},
				"conflicts":[]
				""".formatted(card, NOW);

		assertThat(saved.getStatusCode().value()).isEqualTo(200);
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch("{\"data\":{" + manualBody + "}}",
				saved.getBody());
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch("""
				{"data":{%s,"afterSnapshot":{"approvalCount":0,"approvedAmountWon":0,"cancellationCount":0}}}
				""".formatted(manualBody), send("GET", path, "MY_CARDS", null).getBody());

		// 다른 달은 영향받지 않는다(registeredOn도 그 달 입력의 일부, 결정 27 A3-5)
		Map<String, Object> october = data(
				send("GET", CARDS + "/" + card + "/monthly-states/2026-10", "MY_CARDS", null).getBody());
		assertThat(october.get("snapshotAt")).isNull();
		assertThat(map(october.get("registeredOn"))).containsEntry("value", null)
			.containsEntry("source", "UNKNOWN");
		assertThat(map(october.get("previousMonthPerformanceWon"))).containsEntry("value", null);
	}

	@Test
	void replaceClearsOmittedItemsAndSwitchesToNoSelection() {
		String card = register("{\"displayName\":\"올라운드\",\"cardProductId\":\"09271\"}");
		String path = CARDS + "/" + card + "/monthly-states/2026-11";
		ResponseEntity<String> initial = send("PUT", path, "MY_CARDS", """
				{"limitBucketUsages":[{"limitBucketId":"FUEL","value":4000,%s}],
				 "selectedServices":[{"serviceId":"STORE","effectiveFrom":"2026-10-15"}],
				 "registeredOn":{"value":"2026-10-02","period":{"from":"2026-10-02","to":"2026-10-02"}}}
				""".formatted(NOVEMBER));
		assertThat(initial.getStatusCode().value()).isEqualTo(200);
		Map<String, Object> before = data(send("GET", path, "MY_CARDS", null).getBody());
		assertThat((List<?>) before.get("limitBucketUsages")).hasSize(1);
		assertThat((List<?>) before.get("selectedServices")).hasSize(1);

		ResponseEntity<String> replaced = send("PUT", path, "MY_CARDS",
				"{\"noServiceSelection\":{\"effectiveFrom\":\"2026-11-01\"}}");

		assertThat(replaced.getStatusCode().value()).isEqualTo(200);
		Map<String, Object> state = data(send("GET", path, "MY_CARDS", null).getBody());
		assertThat(state.get("limitBucketUsages")).isEqualTo(List.of());
		assertThat(state.get("selectedServices")).isEqualTo(List.of());
		assertThat(state.get("serviceSelectionStatus")).isEqualTo("NONE");
		assertThat(map(state.get("noServiceSelection"))).hasSize(5)
			.containsEntry("effectiveFrom", "2026-11-01")
			.containsEntry("source", "MANUAL")
			.containsEntry("updatedAt", NOW)
			.containsEntry("usable", true)
			.containsEntry("reason", null);
		assertThat(((Map<?, ?>) state.get("registeredOn")).get("source")).isEqualTo("UNKNOWN");
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM monthly_state_bucket_usages", Integer.class))
			.isZero();
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM monthly_state_service_selections",
				Integer.class))
			.isZero();
	}

	@Test
	void concurrentFirstSavesOfTheSameMonthAllSucceedWithOneRow() {
		String card = register("{\"displayName\":\"올라운드\",\"cardProductId\":\"09271\"}");
		String path = CARDS + "/" + card + "/monthly-states/2026-11";
		ExecutorService executor = Executors.newFixedThreadPool(6);
		try {
			List<CompletableFuture<ResponseEntity<String>>> futures = IntStream.range(0, 6)
				.mapToObj(i -> CompletableFuture.supplyAsync(() -> send("PUT", path, "MY_CARDS",
						"{\"limitBucketUsages\":[{\"limitBucketId\":\"FUEL\",\"value\":" + i + "," + NOVEMBER + "}]}"),
						executor))
				.toList();

			assertThat(futures).allSatisfy(future -> assertThat(future.join().getStatusCode().value()).isEqualTo(200));
		}
		finally {
			executor.shutdown();
		}
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM monthly_states", Integer.class)).isEqualTo(1);
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM monthly_state_bucket_usages", Integer.class))
			.isEqualTo(1);
	}

	@Test
	void unconfirmedCardRejectsRuleItemsButStoresPreviousMonthPerformance() {
		String card = register("{\"displayName\":\"모름\"}");
		String path = CARDS + "/" + card + "/monthly-states/2026-11";
		String mappingRequired = "{\"code\":\"USER_CARD_MAPPING_REQUIRED\",\"message\":\"카드 상품 매핑을 먼저 확인해 주세요.\",\"errors\":[]}";

		for (String body : List.of("{\"limitBucketUsages\":[{\"limitBucketId\":\"FUEL\",\"value\":1," + NOVEMBER + "}]}",
				"{\"selectedServices\":[{\"serviceId\":\"FUEL\"}]}", "{\"noServiceSelection\":{}}")) {
			ResponseEntity<String> response = send("PUT", path, "MY_CARDS", body);
			assertThat(response.getStatusCode().value()).isEqualTo(409);
			JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch(mappingRequired, response.getBody());
		}

		ResponseEntity<String> saved = send("PUT", path, "MY_CARDS",
				"{\"previousMonthPerformanceWon\":{\"value\":-500,\"period\":{\"from\":\"2026-10-01\",\"to\":\"2026-10-31\"}}}");
		assertThat(saved.getStatusCode().value()).isEqualTo(200);
		assertThat(((Map<?, ?>) data(saved.getBody()).get("previousMonthPerformanceWon")).get("value"))
			.isEqualTo(-500);
	}

	@Test
	void idsOutsideTheCardRuleAreRejected() {
		String card = register("{\"displayName\":\"톡톡\",\"cardProductId\":\"09174\"}");
		String path = CARDS + "/" + card + "/monthly-states/2026-11";

		ResponseEntity<String> response = send("PUT", path, "MY_CARDS",
				"{\"limitBucketUsages\":[{\"limitBucketId\":\"FUEL\",\"value\":1," + NOVEMBER + "}],"
						+ "\"noServiceSelection\":{}}");

		assertThat(response.getStatusCode().value()).isEqualTo(400);
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch("""
				{"code":"INVALID_REQUEST","message":"요청 값을 확인해 주세요.",
				 "errors":[{"field":"limitBucketUsages[0].limitBucketId","message":"카드 규칙에 없는 값입니다."},
				           {"field":"noServiceSelection","message":"선택형 서비스가 없는 카드입니다."}]}
				""", response.getBody());
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM monthly_states", Integer.class)).isZero();
	}

	@Test
	void mappingChangeHidesStoredBucketUsages() {
		String card = register("{\"displayName\":\"올라운드\",\"cardProductId\":\"09271\"}");
		String path = CARDS + "/" + card + "/monthly-states/2026-11";
		send("PUT", path, "MY_CARDS", """
				{"previousMonthPerformanceWon":{"value":300000,"period":{"from":"2026-10-01","to":"2026-10-31"}},
				 "limitBucketUsages":[{"limitBucketId":"FUEL","value":4000,%s}]}
				""".formatted(NOVEMBER));

		send("PATCH", CARDS + "/" + card + "/mapping", "MY_CARDS",
				"{\"status\":\"USER_CONFIRMED\",\"cardProductId\":\"09174\",\"confirmationMethod\":\"MANUAL_SELECTION\"}");

		Map<String, Object> state = data(send("GET", path, "MY_CARDS", null).getBody());
		assertThat(state.get("limitBucketUsages")).isEqualTo(List.of());
		assertThat(((Map<?, ?>) state.get("previousMonthPerformanceWon")).get("value")).isEqualTo(300000);
	}

	@Test
	void virtualPortfolioCanWriteAndOtherUsersCannotSeeIt() {
		List<Map<String, Object>> seeds = items(send("GET", CARDS, "VIRTUAL_PORTFOLIO", null).getBody());
		String fuelCard = (String) seeds.get(3).get("userCardId");
		String path = CARDS + "/" + fuelCard + "/monthly-states/2026-11";

		// 시드가 없어 처음에는 모두 미입력(결정 27 A3)
		assertThat(data(send("GET", path, "VIRTUAL_PORTFOLIO", null).getBody()).get("snapshotAt")).isNull();
		ResponseEntity<String> saved = send("PUT", path, "VIRTUAL_PORTFOLIO",
				"{\"limitBucketUsages\":[{\"limitBucketId\":\"FUEL\",\"value\":1000," + NOVEMBER + "}]}");
		assertThat(saved.getStatusCode().value()).isEqualTo(200);

		String notFound = "{\"code\":\"USER_CARD_NOT_FOUND\",\"message\":\"보유 카드를 찾을 수 없습니다.\",\"errors\":[]}";
		for (String method : List.of("GET", "PUT")) {
			ResponseEntity<String> response = send(method, path, "MY_CARDS", "GET".equals(method) ? null : "{}");
			assertThat(response.getStatusCode().value()).isEqualTo(404);
			JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch(notFound, response.getBody());
		}
		ResponseEntity<String> missing = send("GET", CARDS + "/uc-999999/monthly-states/2026-11", "MY_CARDS", null);
		assertThat(missing.getStatusCode().value()).isEqualTo(404);
	}

	@Test
	void releasedCardIsNotFound() {
		String card = register("{\"displayName\":\"올라운드\",\"cardProductId\":\"09271\"}");
		send("PUT", CARDS + "/" + card + "/monthly-states/2026-11", "MY_CARDS",
				"{\"otherBucketsUsedWon\":{\"value\":0," + NOVEMBER + "}}");
		send("DELETE", CARDS + "/" + card, "MY_CARDS", null);

		ResponseEntity<String> response = send("GET", CARDS + "/" + card + "/monthly-states/2026-11", "MY_CARDS",
				null);

		assertThat(response.getStatusCode().value()).isEqualTo(404);
	}

	private String register(String body) {
		ResponseEntity<String> created = send("POST", CARDS, "MY_CARDS", body);
		assertThat(created.getStatusCode().value()).isEqualTo(201);
		return (String) data(created.getBody()).get("userCardId");
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> map(Object value) {
		return (Map<String, Object>) value;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> data(String body) {
		return (Map<String, Object>) JsonMapper.builder().build().readValue(body, Map.class).get("data");
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> items(String body) {
		return (List<Map<String, Object>>) data(body).get("items");
	}

	private ResponseEntity<String> send(String method, String path, String demoUser, String body) {
		RestClient client = RestClient.builder()
			.baseUrl("http://localhost:" + this.port)
			.defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
			})
			.build();
		RestClient.RequestBodySpec spec = client.method(HttpMethod.valueOf(method)).uri(path).header(HEADER, demoUser);
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
