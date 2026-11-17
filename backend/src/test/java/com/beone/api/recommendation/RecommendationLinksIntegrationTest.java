package com.beone.api.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * 추천 연결 후속(#120)을 Testcontainers MySQL과 실제 HTTP로 확인한다. 순환 없는 조회 포트가 실제 빈으로 연결되는지도 함께 본다.
 *
 * <p>기대값: 주문 {@code latestRecommendationId}는 docs/api/orders.md 172행("이 주문으로 마지막에 계산한 추천"), 카드
 * {@code invalidatedRecommendationIds}는 docs/api/cards.md 415·465행과 관리자 승인 규칙(변경 직전 유효했고 이 카드 변경으로
 * {@code MAPPING_CHANGED}가 된 추천만, ID 오름차순), 월간 상태 {@code afterSnapshot}은 docs/api/monthly-states.md 97·109행(스냅샷 뒤
 * 모의 승인 건수·승인 금액 합·취소 건수)에서 썼다. 평가 자료 C01(09271, 100,000원 일반 국내 직접 결제)을 고정 시계의 달(2026-11)로 옮겼다.
 */
@IntegrationTest
@ActiveProfiles("local")
@Import(RecommendationLinksIntegrationTest.ClockConfiguration.class)
class RecommendationLinksIntegrationTest {

	private static final String HEADER = "X-BeONE-Demo-User";

	private static final Instant START = Instant.parse("2026-11-17T01:30:00Z");

	private static final String C01_ORDER = """
			{"amountWon":100000,"orderDate":"2026-11-17","merchantName":"동네 마트",
			 "merchantClass":"ORDINARY_DOMESTIC","paymentMethod":"DIRECT"}
			""";

	private static final String C01_STATE = """
			{"previousMonthPerformanceWon":{"value":0,"period":{"from":"2026-10-01","to":"2026-10-31"}},
			 "registeredOn":{"value":"2026-01-01","period":{"from":"2026-01-01","to":"2026-01-01"}}}
			""";

	private static final AtomicReference<Instant> NOW = new AtomicReference<>(START);

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void clearRows() {
		NOW.set(START);
		this.jdbcTemplate.update("DELETE FROM idempotency_keys");
		this.jdbcTemplate.update("DELETE FROM simulated_payments");
		this.jdbcTemplate.update("DELETE FROM recommendations");
		this.jdbcTemplate.update("DELETE FROM payment_preferences");
		this.jdbcTemplate.update("DELETE FROM monthly_states");
		this.jdbcTemplate.update("DELETE FROM planned_spends");
		this.jdbcTemplate.update("DELETE FROM orders");
		this.jdbcTemplate.update("DELETE FROM user_cards WHERE holding_source <> 'SYNTHETIC'");
	}

	@Test
	void ordersShowTheLatestRecommendationAndCardChangesListWhatTheyInvalidated() {
		String card = registerWithC01State();
		String order = createOrder("MY_CARDS");
		String orderPath = "/api/v1/me/orders/" + order;
		assertThat(data(send("GET", orderPath, "MY_CARDS", null, null).getBody()))
			.containsEntry("latestRecommendationId", null);

		String first = recommend(order);
		String second = recommend(order);
		assertThat(data(send("GET", orderPath, "MY_CARDS", null, null).getBody()))
			.containsEntry("latestRecommendationId", second);

		// 표시 이름만 바꾸면 무효화하지 않고 필드를 담지 않는다(cards.md 465행)
		Map<String, Object> renamed = data(
				send("PATCH", "/api/v1/me/cards/" + card, "MY_CARDS", null, "{\"displayName\":\"주력\"}").getBody());
		assertThat(renamed).doesNotContainKey("invalidatedRecommendationIds");

		// 추천 제외가 실제로 바뀌면 그 카드를 근거로 쓴 유효한 추천이 무효가 된다
		Map<String, Object> excluded = data(send("PATCH", "/api/v1/me/cards/" + card, "MY_CARDS", null,
				"{\"excludedFromRecommendation\":true}").getBody());
		assertThat(excluded).containsEntry("invalidatedRecommendationIds", List.of(first, second));
		assertThat(data(send("GET", "/api/v1/me/recommendations/" + first, "MY_CARDS", null, null).getBody()))
			.containsEntry("invalidated", true)
			.containsEntry("invalidatedReason", "MAPPING_CHANGED");

		// 되돌리면: 변경 직전 이미 무효였던 추천은 담지 않는다(관리자 승인 규칙)
		Map<String, Object> restored = data(send("PATCH", "/api/v1/me/cards/" + card, "MY_CARDS", null,
				"{\"excludedFromRecommendation\":false}").getBody());
		assertThat(restored).containsEntry("invalidatedRecommendationIds", List.of());

		// 매핑 변경은 늘 배열을 담는다. 지금 유효한 세 추천이 모두 무효가 된다
		String third = recommend(order);
		Map<String, Object> unmapped = data(send("PATCH", "/api/v1/me/cards/" + card + "/mapping", "MY_CARDS", null,
				"{\"status\":\"UNMAPPED\"}").getBody());
		assertThat(unmapped).containsEntry("invalidatedRecommendationIds", List.of(first, second, third));
		Map<String, Object> again = data(send("PATCH", "/api/v1/me/cards/" + card + "/mapping", "MY_CARDS", null,
				"{\"status\":\"UNMAPPED\"}").getBody());
		assertThat(again).containsEntry("invalidatedRecommendationIds", List.of());
		assertThat(data(send("GET", orderPath, "MY_CARDS", null, null).getBody()))
			.containsEntry("latestRecommendationId", third);

		// 다른 사용자에게 이 주문은 없는 주문이다(D-09)
		assertThat(send("GET", orderPath, "VIRTUAL_PORTFOLIO", null, null).getStatusCode().value()).isEqualTo(404);
	}

	@Test
	void afterSnapshotCountsSimulatedApprovalsAndCancellationsRecordedAfterTheSnapshot() {
		String card = registerWithC01State();
		String statePath = "/api/v1/me/cards/" + card + "/monthly-states/2026-11";
		assertThat(data(send("GET", statePath, "MY_CARDS", null, null).getBody())).containsEntry("afterSnapshot",
				Map.of("approvalCount", 0, "approvedAmountWon", 0, "cancellationCount", 0));
		String order = createOrder("MY_CARDS");
		NOW.set(START.plusSeconds(60));

		Map<String, Object> approved = data(send("POST", "/api/v1/me/orders/" + order + "/simulated-payments",
				"MY_CARDS", "key-a", null).getBody());

		// 승인 직전 재계산도 이 주문으로 계산한 추천이므로 마지막 추천이 된다(orders.md 172행)
		String recalculated = (String) map(approved.get("recalculation")).get("recommendationId");
		assertThat(data(send("GET", "/api/v1/me/orders/" + order, "MY_CARDS", null, null).getBody()))
			.containsEntry("status", "PAID")
			.containsEntry("latestRecommendationId", recalculated);
		assertThat(data(send("GET", statePath, "MY_CARDS", null, null).getBody())).containsEntry("afterSnapshot",
				Map.of("approvalCount", 1, "approvedAmountWon", 100000, "cancellationCount", 0));

		// 취소는 승인 금액에서 빼지 않고 건수로만 센다(monthly-states.md 109행, 관리자 승인 규칙)
		NOW.set(START.plusSeconds(120));
		String payment = (String) approved.get("simulatedPaymentId");
		assertThat(send("POST", "/api/v1/me/simulated-payments/" + payment + "/cancellations", "MY_CARDS", "key-c",
				"{\"amountWon\":40000}").getStatusCode().value()).isEqualTo(201);
		assertThat(data(send("GET", statePath, "MY_CARDS", null, null).getBody())).containsEntry("afterSnapshot",
				Map.of("approvalCount", 1, "approvedAmountWon", 100000, "cancellationCount", 1));

		// 다시 입력하면 새 스냅샷 시각 뒤의 활동만 센다
		NOW.set(START.plusSeconds(180));
		assertThat(send("PUT", statePath, "MY_CARDS", null, C01_STATE).getStatusCode().value()).isEqualTo(200);
		assertThat(data(send("GET", statePath, "MY_CARDS", null, null).getBody())).containsEntry("afterSnapshot",
				Map.of("approvalCount", 0, "approvedAmountWon", 0, "cancellationCount", 0));
	}

	@Test
	void aCancellationBelongsToTheMonthOfItsOriginalApproval() {
		// 관리자 승인 규칙(monthly-states.md 218행, D-17): 취소는 원 승인 행의 거래일 월에 귀속한다. 11월 승인을 12월에 취소하면
		// 11월 요약에 들어가고(그 달 버킷은 확인 필요), 12월 요약에는 들어가지 않는다
		String card = registerWithC01State();
		String order = createOrder("MY_CARDS");
		NOW.set(START.plusSeconds(60));
		String payment = (String) data(send("POST", "/api/v1/me/orders/" + order + "/simulated-payments", "MY_CARDS",
				"key-a", null).getBody()).get("simulatedPaymentId");
		String december = "/api/v1/me/cards/" + card + "/monthly-states/2026-12";
		NOW.set(Instant.parse("2026-11-30T16:00:00Z"));
		assertThat(send("PUT", december, "MY_CARDS", null, """
				{"previousMonthPerformanceWon":{"value":100000,"period":{"from":"2026-11-01","to":"2026-11-30"}}}
				""").getStatusCode().value()).isEqualTo(200);
		NOW.set(Instant.parse("2026-12-02T01:00:00Z"));

		ResponseEntity<String> cancelled = send("POST", "/api/v1/me/simulated-payments/" + payment + "/cancellations",
				"MY_CARDS", "key-c", "{\"amountWon\":40000}");

		assertThat(cancelled.getStatusCode().value()).isEqualTo(201);
		assertThat(this.jdbcTemplate.queryForObject(
				"SELECT transaction_date FROM ledger_transactions WHERE entry_type = 'CANCELLATION'", java.sql.Date.class))
			.isEqualTo(java.sql.Date.valueOf("2026-12-02"));
		assertThat(data(send("GET", "/api/v1/me/cards/" + card + "/monthly-states/2026-11", "MY_CARDS", null, null)
			.getBody())).containsEntry("afterSnapshot",
					Map.of("approvalCount", 1, "approvedAmountWon", 100000, "cancellationCount", 1));
		assertThat(data(send("GET", december, "MY_CARDS", null, null).getBody())).containsEntry("afterSnapshot",
				Map.of("approvalCount", 0, "approvedAmountWon", 0, "cancellationCount", 0));
	}

	private String recommend(String order) {
		ResponseEntity<String> created = send("POST", "/api/v1/me/orders/" + order + "/recommendations", "MY_CARDS",
				null, null);
		assertThat(created.getStatusCode().value()).isEqualTo(201);
		return (String) data(created.getBody()).get("recommendationId");
	}

	private String registerWithC01State() {
		ResponseEntity<String> created = send("POST", "/api/v1/me/cards", "MY_CARDS", null,
				"{\"displayName\":\"올라운드\",\"cardProductId\":\"09271\"}");
		assertThat(created.getStatusCode().value()).isEqualTo(201);
		String card = (String) data(created.getBody()).get("userCardId");
		assertThat(send("PUT", "/api/v1/me/cards/" + card + "/monthly-states/2026-11", "MY_CARDS", null, C01_STATE)
			.getStatusCode()
			.value()).isEqualTo(200);
		return card;
	}

	private String createOrder(String demoUser) {
		ResponseEntity<String> created = send("POST", "/api/v1/me/orders", demoUser, null, C01_ORDER);
		assertThat(created.getStatusCode().value()).isEqualTo(201);
		return (String) data(created.getBody()).get("orderId");
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> map(Object value) {
		return (Map<String, Object>) value;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> data(String body) {
		return (Map<String, Object>) JsonMapper.builder().build().readValue(body, Map.class).get("data");
	}

	private ResponseEntity<String> send(String method, String path, String demoUser, String idempotencyKey,
			String body) {
		RestClient client = RestClient.builder()
			.baseUrl("http://localhost:" + this.port)
			.defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
			})
			.build();
		RestClient.RequestBodySpec spec = client.method(HttpMethod.valueOf(method)).uri(path).header(HEADER, demoUser);
		if (idempotencyKey != null) {
			spec = spec.header("Idempotency-Key", idempotencyKey);
		}
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
			return NOW.get();
		}

	}

}
