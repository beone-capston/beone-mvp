package com.beone.api.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.beone.api.order.application.OrderClock;
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
import tools.jackson.databind.json.JsonMapper;

/**
 * Testcontainers MySQL에서 V7 마이그레이션(ddl-auto: validate 통과)·추천 계산 저장과 조회·사용자별 분리(D-09)·지연 무효화를 실제
 * HTTP로 확인한다. 주문·보유 카드·월간 상태는 각 공개 API로 만든다.
 *
 * <p>계산 기대값은 평가 자료 C01(09271, 일반 국내 100,000원 직접 결제, 전월 실적 없음 → 1,000원 APPLIED)의 주문일을 고정 시계의 달
 * (2026-11)로 옮긴 것이다. 무효화 우선순위는 결정 27 B2-4, 가상 포트폴리오의 확인 필요는 결정 27 A3이다. 월간 상태 재입력 시각을 바꾸려고
 * 시계를 테스트 안에서 옮긴다. 추천 행이 주문·보유 카드를 참조하므로(외래 키) 다른 통합 테스트의 정리를 막지 않게 앞뒤로 지운다.
 */
@IntegrationTest
@ActiveProfiles("local")
@Import(RecommendationIntegrationTest.ClockConfiguration.class)
class RecommendationIntegrationTest {

	private static final String HEADER = "X-BeONE-Demo-User";

	private static final Instant START = Instant.parse("2026-11-17T01:30:00Z");

	private static final String NOW = "2026-11-17T10:30:00+09:00";

	private static final String C01_ORDER = """
			{"amountWon":100000,"orderDate":"2026-11-17","merchantName":"동네 마트",
			 "merchantClass":"ORDINARY_DOMESTIC","paymentMethod":"DIRECT"}
			""";

	// C01: 전월(10월) 적격 거래 없음 → 전월 실적 0원, 등록일은 fixture 기본값 2026-01-01
	private static final String C01_STATE = """
			{"previousMonthPerformanceWon":{"value":0,"period":{"from":"2026-10-01","to":"2026-10-31"}},
			 "registeredOn":{"value":"2026-01-01","period":{"from":"2026-01-01","to":"2026-01-01"}}}
			""";

	private static final String NOT_FOUND = "{\"code\":\"RECOMMENDATION_NOT_FOUND\",\"message\":\"추천 결과를 찾을 수 없습니다.\",\"errors\":[]}";

	private static final AtomicReference<Instant> NOW_INSTANT = new AtomicReference<>(START);

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void clearRows() {
		NOW_INSTANT.set(START);
		this.jdbcTemplate.update("DELETE FROM recommendations");
		this.jdbcTemplate.update("DELETE FROM payment_preferences");
		this.jdbcTemplate.update("DELETE FROM monthly_states");
		this.jdbcTemplate.update("DELETE FROM planned_spends");
		this.jdbcTemplate.update("DELETE FROM orders");
		this.jdbcTemplate.update("DELETE FROM user_cards WHERE holding_source <> 'SYNTHETIC'");
	}

	@Test
	void recommendationMigrationIsApplied() {
		Boolean success = this.jdbcTemplate.queryForObject(
				"SELECT success FROM flyway_schema_history WHERE version = '7'", Boolean.class);

		assertThat(success).isTrue();
	}

	@Test
	void calculatesStoresAndReadsBackWithoutRecalculation() {
		String card = register("09271");
		assertThat(send("PUT", monthlyPath(card), "MY_CARDS", C01_STATE).getStatusCode().value()).isEqualTo(200);
		String order = createOrder("MY_CARDS");

		ResponseEntity<String> created = send("POST", "/api/v1/me/orders/" + order + "/recommendations", "MY_CARDS",
				null);

		assertThat(created.getStatusCode().value()).isEqualTo(201);
		Map<String, Object> data = data(created.getBody());
		String id = (String) data.get("recommendationId");
		assertThat(id).matches("rec-[1-9][0-9]*");
		assertThat(data).containsEntry("orderId", order)
			.containsEntry("calculatedAt", NOW)
			.containsEntry("decisionDate", "2026-11-17")
			.containsEntry("selectionMode", "AUTO_RECOMMEND")
			.containsEntry("outcome", "SELECTED")
			.containsEntry("conditional", false)
			.containsEntry("excludedCards", List.of())
			.containsEntry("fixedCardComparison", null);
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch("""
				{"userCardId":"%s","cardProductId":"09271",
				 "basis":{"decidedBy":null,"arbitraryTieBreak":false,"tiedUserCardIds":[],"gapToRunnerUpWon":null}}
				""".formatted(card), json(data.get("selected")));
		Map<String, Object> compared = map(((List<?>) data.get("comparedCards")).get(0));
		assertThat(compared).containsEntry("userCardId", card)
			.containsEntry("displayName", "올라운드")
			.containsEntry("applicationStatus", "APPLIED")
			.containsEntry("currentOrderBenefit", Map.of("amountWon", 1000, "status", "CONFIRMED"))
			.containsEntry("monthlyTotalBenefit", Map.of("amountWon", 1000, "status", "CONFIRMED"))
			.containsEntry("mapping", Map.of("status", "USER_CONFIRMED", "note", "사용자 확인 매핑, 공식 SKU·약관 버전 미검증"));
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch("""
				{"order":{"amountWon":100000,"orderDate":"2026-11-17","merchantClass":"ORDINARY_DOMESTIC","paymentMethod":"DIRECT"},
				 "representativeUserCardId":null,"monthlyStateSnapshotAt":{"%s":"%s"}}
				""".formatted(card, NOW), json(data.get("inputSnapshot")));
		assertThat(data.get("notes")).isEqualTo(List.of("예정 소비는 이번 버전에서 월간 합계에 반영하지 않습니다(D-18 K4).",
				"비교할 수 있는 카드가 1장이라 다른 카드와 비교한 결과가 아닙니다."));

		// 조회는 계산 응답에 무효화 필드만 더한다
		Map<String, Object> expected = new java.util.LinkedHashMap<>(data);
		expected.put("invalidated", false);
		expected.put("invalidatedReason", null);
		ResponseEntity<String> read = send("GET", "/api/v1/me/recommendations/" + id, "MY_CARDS", null);
		assertThat(read.getStatusCode().value()).isEqualTo(200);
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch(json(expected), json(data(read.getBody())));

		// 같은 주문이라도 부를 때마다 새 추천이다
		String second = (String) data(
				send("POST", "/api/v1/me/orders/" + order + "/recommendations", "MY_CARDS", null).getBody())
			.get("recommendationId");
		assertThat(second).isNotEqualTo(id);
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM recommendations", Integer.class)).isEqualTo(2);
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM recommendation_card_bases", Integer.class))
			.isEqualTo(2);
	}

	@Test
	void invalidationFollowsPriority() {
		String card = register("09271");
		send("PUT", monthlyPath(card), "MY_CARDS", C01_STATE);
		String id = recommend("MY_CARDS", createOrder("MY_CARDS"));

		// 월간 상태 재입력(스냅샷 시각 변경) → MONTHLY_STATE_CHANGED
		NOW_INSTANT.set(START.plusSeconds(60));
		assertThat(send("PUT", monthlyPath(card), "MY_CARDS", C01_STATE).getStatusCode().value()).isEqualTo(200);
		assertInvalidation(id, "MONTHLY_STATE_CHANGED");

		// 추천 제외 변경은 MAPPING_CHANGED이고 월간 상태 변경보다 앞선다
		assertThat(send("PATCH", "/api/v1/me/cards/" + card, "MY_CARDS", "{\"excludedFromRecommendation\":true}")
			.getStatusCode()
			.value()).isEqualTo(200);
		assertInvalidation(id, "MAPPING_CHANGED");

		// 다시 넣어도 월간 상태가 바뀌었으므로 유효해지지 않는다
		send("PATCH", "/api/v1/me/cards/" + card, "MY_CARDS", "{\"excludedFromRecommendation\":false}");
		assertInvalidation(id, "MONTHLY_STATE_CHANGED");
	}

	@Test
	void releasedCardInvalidatesAsMappingChanged() {
		String card = register("09271");
		send("PUT", monthlyPath(card), "MY_CARDS", C01_STATE);
		String id = recommend("MY_CARDS", createOrder("MY_CARDS"));

		assertThat(send("DELETE", "/api/v1/me/cards/" + card, "MY_CARDS", null).getStatusCode().value())
			.isEqualTo(204);

		assertInvalidation(id, "MAPPING_CHANGED");
	}

	@Test
	void recommendationsAreSeparatedPerUser() {
		register("09271");
		String order = createOrder("MY_CARDS");
		String id = recommend("MY_CARDS", order);

		assertBody(send("GET", "/api/v1/me/recommendations/" + id, "VIRTUAL_PORTFOLIO", null), 404, NOT_FOUND);
		assertBody(send("POST", "/api/v1/me/orders/" + order + "/recommendations", "VIRTUAL_PORTFOLIO", null), 404,
				"{\"code\":\"ORDER_NOT_FOUND\",\"message\":\"주문을 찾을 수 없습니다.\",\"errors\":[]}");
		for (String malformed : List.of("rec-0", "rec-01", "3001", "rec-x")) {
			assertBody(send("GET", "/api/v1/me/recommendations/" + malformed, "MY_CARDS", null), 404, NOT_FOUND);
		}
		assertBody(send("GET", "/api/v1/me/recommendations/rec-999999999", "MY_CARDS", null), 404, NOT_FOUND);
	}

	@Test
	void virtualPortfolioWithoutMonthlyStateKeepsUnknownsUnconfirmed() {
		String order = createOrder("VIRTUAL_PORTFOLIO");

		Map<String, Object> data = data(
				send("POST", "/api/v1/me/orders/" + order + "/recommendations", "VIRTUAL_PORTFOLIO", null).getBody());

		List<Map<String, Object>> cards = new ArrayList<>();
		((List<?>) data.get("comparedCards")).forEach(card -> cards.add(map(card)));
		((List<?>) data.get("excludedCards")).forEach(card -> cards.add(map(card)));
		assertThat(cards).extracting(card -> card.get("cardProductId"))
			.containsExactlyInAnyOrder("09271", "09174", "01664", "01914");
		// 가상 포트폴리오 카드는 매핑 대상이 아니다(결정 27 B2-7)
		((List<?>) data.get("comparedCards")).forEach(card -> assertThat(map(card).get("mapping")).isNull());
		// 월간 시드가 없어(결정 27 A3) 제외 카드 대부분이 확인 필요이고, 확인 필요 금액은 0이 아니라 null이다
		List<Map<String, Object>> excluded = new ArrayList<>();
		((List<?>) data.get("excludedCards")).forEach(card -> excluded.add(map(card)));
		assertThat(excluded).filteredOn(card -> "NEEDS_CONFIRMATION".equals(card.get("reason"))).hasSizeGreaterThan(2);
		assertThat(cards).allSatisfy(card -> {
			Map<String, Object> total = map(card.get("monthlyTotalBenefit"));
			if ("NEEDS_CONFIRMATION".equals(total.get("status"))) {
				assertThat(total.get("amountWon")).isNull();
			}
		});
		assertThat(json(data)).doesNotContain("최적");
	}

	private void assertInvalidation(String id, String reason) {
		Map<String, Object> data = data(send("GET", "/api/v1/me/recommendations/" + id, "MY_CARDS", null).getBody());
		assertThat(data).containsEntry("invalidated", true).containsEntry("invalidatedReason", reason);
	}

	private String recommend(String demoUser, String order) {
		ResponseEntity<String> created = send("POST", "/api/v1/me/orders/" + order + "/recommendations", demoUser,
				null);
		assertThat(created.getStatusCode().value()).isEqualTo(201);
		return (String) data(created.getBody()).get("recommendationId");
	}

	private String createOrder(String demoUser) {
		ResponseEntity<String> created = send("POST", "/api/v1/me/orders", demoUser, C01_ORDER);
		assertThat(created.getStatusCode().value()).isEqualTo(201);
		return (String) data(created.getBody()).get("orderId");
	}

	private String register(String productId) {
		ResponseEntity<String> created = send("POST", "/api/v1/me/cards", "MY_CARDS",
				"{\"displayName\":\"올라운드\",\"cardProductId\":\"" + productId + "\"}");
		assertThat(created.getStatusCode().value()).isEqualTo(201);
		return (String) data(created.getBody()).get("userCardId");
	}

	private static String monthlyPath(String card) {
		return "/api/v1/me/cards/" + card + "/monthly-states/2026-11";
	}

	private static void assertBody(ResponseEntity<String> response, int status, String body) {
		assertThat(response.getStatusCode().value()).isEqualTo(status);
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch(body, response.getBody());
	}

	private static String json(Object value) {
		return JsonMapper.builder().build().writeValueAsString(value);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> map(Object value) {
		return (Map<String, Object>) value;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> data(String body) {
		return (Map<String, Object>) JsonMapper.builder().build().readValue(body, Map.class).get("data");
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

	/** 기능 시계는 테스트가 옮길 수 있게 하고, 주문의 "이번 달"은 서울 2026-11-17로 고정한다. */
	@TestConfiguration(proxyBeanMethods = false)
	static class ClockConfiguration {

		@Bean
		Clock movableClock() {
			return new MovableClock(ZoneOffset.UTC);
		}

		@Bean
		@Primary
		OrderClock fixedOrderClock() {
			return new OrderClock(Clock.fixed(Instant.parse("2026-11-17T03:00:00Z"), ZoneOffset.UTC));
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
			return NOW_INSTANT.get();
		}

	}

}
