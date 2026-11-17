package com.beone.api.payment;

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
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Testcontainers MySQL에서 V8 마이그레이션(ddl-auto: validate 통과)·모의 승인과 원장 기록·멱등 키·부분/전액 취소·사용자별 분리(D-09)를
 * 실제 HTTP로 확인한다. 주문·보유 카드·월간 상태·추천은 각 공개 API로 만든다.
 *
 * <p>기대값: 평가 자료 C01(09271, 일반 국내 100,000원 직접 결제, 전월 실적 0원 → 기본 적립 BASE 1,000원)을 고정 시계의 달(2026-11)로
 * 옮겼다. 09271의 취소 귀속은 규칙 파일의 {@code RECEIPT_MONTH}(E13), 혜택 되돌림 조항은 없다(D-17). 가상 포트폴리오는 월간 상태 시드가
 * 없어 확정 불가다(결정 27 A3). 실패 응답은 docs/api/mock-payments.md 실패 목록, 멱등 규칙은 docs/api.md 중복 방지다.
 */
@IntegrationTest
@ActiveProfiles("local")
@Import(SimulatedPaymentIntegrationTest.ClockConfiguration.class)
class SimulatedPaymentIntegrationTest {

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
	void paymentMigrationIsApplied() {
		Boolean success = this.jdbcTemplate.queryForObject(
				"SELECT success FROM flyway_schema_history WHERE version = '8'", Boolean.class);

		assertThat(success).isTrue();
	}

	@Test
	void approvesOnceRecordsTheLedgerAndReplaysTheSameKey() {
		String card = registerWithC01State();
		String order = createOrder("MY_CARDS");
		String seen = (String) data(send("POST", "/api/v1/me/orders/" + order + "/recommendations", "MY_CARDS", null,
				null).getBody()).get("recommendationId");
		NOW.set(START.plusSeconds(60));
		String path = "/api/v1/me/orders/" + order + "/simulated-payments";
		String body = "{\"seenRecommendationId\":\"" + seen + "\"}";

		ResponseEntity<String> approved = send("POST", path, "MY_CARDS", "key-1", body);

		assertThat(approved.getStatusCode().value()).isEqualTo(201);
		Map<String, Object> data = data(approved.getBody());
		String payment = (String) data.get("simulatedPaymentId");
		String ledger = (String) data.get("ledgerTransactionId");
		assertThat(payment).matches("pay-[1-9][0-9]*");
		assertThat(ledger).matches("lt-[1-9][0-9]*");
		assertThat(data).containsEntry("orderId", order)
			.containsEntry("status", "APPROVED")
			.containsEntry("approvedAt", "2026-11-17T10:31:00+09:00")
			.containsEntry("amountWon", 100000)
			.containsEntry("selectionMode", "AUTO_RECOMMEND")
			.containsEntry("userCardId", card)
			.containsEntry("cardProductId", "09271")
			.containsEntry("conditional", false)
			.containsEntry("currentOrderBenefit", Map.of("amountWon", 1000, "status", "CONFIRMED"))
			.containsEntry("mappingNote", "사용자 확인 매핑, 공식 SKU·약관 버전 미검증")
			.containsEntry("cancellableAmountWon", 100000);
		Map<String, Object> recalculation = map(data.get("recalculation"));
		assertThat(recalculation).containsEntry("seenRecommendationId", seen)
			.containsEntry("changed", false)
			.containsEntry("differences", List.of());
		assertThat((String) recalculation.get("recommendationId")).isNotEqualTo(seen);

		// 원장: 자체 모의 승인 행 하나(출처 SIMULATION, 양수, 주문일·주문 업종), 주문은 PAID·결제 ID 연결
		assertThat(this.jdbcTemplate.queryForList(
				"SELECT source, entry_type, amount_won, transaction_date, merchant_class, attribution_month, attribution_status"
						+ " FROM ledger_transactions"))
			.singleElement()
			.satisfies(row -> assertThat(row).containsEntry("source", "SIMULATION")
				.containsEntry("entry_type", "APPROVAL")
				.containsEntry("amount_won", 100000L)
				.containsEntry("transaction_date", java.sql.Date.valueOf("2026-11-17"))
				.containsEntry("merchant_class", "ORDINARY_DOMESTIC")
				.containsEntry("attribution_month", "2026-11")
				.containsEntry("attribution_status", "CONFIRMED"));
		Map<String, Object> orderDetail = data(send("GET", "/api/v1/me/orders/" + order, "MY_CARDS", null, null).getBody());
		assertThat(orderDetail).containsEntry("status", "PAID").containsEntry("simulatedPaymentId", payment);

		// 같은 키·같은 지문 → 같은 결과, 원장 행은 늘지 않음
		ResponseEntity<String> replay = send("POST", path, "MY_CARDS", "key-1", body);
		assertThat(replay.getStatusCode().value()).isEqualTo(201);
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch(approved.getBody(), replay.getBody());
		assertThat(count("ledger_transactions")).isEqualTo(1);
		// 같은 키·다른 지문 → 409, 다른 키 → 이미 결제된 주문 409
		assertError(send("POST", path, "MY_CARDS", "key-1", "{}"), 409, "IDEMPOTENCY_KEY_REUSED");
		assertError(send("POST", path, "MY_CARDS", "key-2", body), 409, "ORDER_ALREADY_PAID");
		// 승인된 주문의 추천은 다시 계산하지 않는다
		assertError(send("POST", "/api/v1/me/orders/" + order + "/recommendations", "MY_CARDS", null, null), 409,
				"ORDER_ALREADY_PAID");
		// 승인 전에 본 추천은 조회 때 ORDER_PAID로 무효 표시된다(추천 계약 invalidatedReason)
		assertThat(data(send("GET", "/api/v1/me/recommendations/" + seen, "MY_CARDS", null, null).getBody()))
			.containsEntry("invalidated", true)
			.containsEntry("invalidatedReason", "ORDER_PAID");
	}

	@Test
	void partialThenFullCancellationWithReceiptMonthImpact() {
		registerWithC01State();
		String order = createOrder("MY_CARDS");
		NOW.set(START.plusSeconds(60));
		String payment = (String) data(send("POST", "/api/v1/me/orders/" + order + "/simulated-payments", "MY_CARDS",
				"key-a", null).getBody()).get("simulatedPaymentId");
		String cancellations = "/api/v1/me/simulated-payments/" + payment + "/cancellations";
		NOW.set(START.plusSeconds(120));

		ResponseEntity<String> partial = send("POST", cancellations, "MY_CARDS", "key-c1", "{\"amountWon\":40000}");

		assertThat(partial.getStatusCode().value()).isEqualTo(201);
		Map<String, Object> first = data(partial.getBody());
		assertThat(first).containsEntry("simulatedPaymentId", payment)
			.containsEntry("amountWon", 40000)
			.containsEntry("cancelledAt", "2026-11-17T10:32:00+09:00")
			.containsEntry("paymentStatus", "PARTIALLY_CANCELLED")
			.containsEntry("cancellableAmountWon", 60000);
		String card = (String) data(send("GET", "/api/v1/me/simulated-payments/" + payment, "MY_CARDS", null, null)
			.getBody()).get("userCardId");
		// 09271: 귀속 RECEIPT_MONTH 확정 + 승인(10:31)이 스냅샷(10:30) 뒤 → 실적 차감 확정. 혜택 되돌림 조항 없음 → BASE 확인 필요
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch("""
				{"confirmed":[{"target":"PERFORMANCE","userCardId":"%s","month":"2026-11","deltaWon":-40000,"basis":"RECEIPT_MONTH"}],
				 "needsConfirmation":[{"target":"BENEFIT","userCardId":"%s","serviceId":"BASE",
				  "reason":"혜택 되돌림 규칙이 약관에서 확정되지 않았습니다."}]}
				""".formatted(card, card), json(first.get("impact")));
		assertThat(this.jdbcTemplate.queryForList(
				"SELECT entry_type, amount_won, original_ledger_id IS NOT NULL AS linked FROM ledger_transactions ORDER BY ledger_id"))
			.extracting(row -> row.get("entry_type") + ":" + row.get("amount_won") + ":" + row.get("linked"))
			.containsExactly("APPROVAL:100000:0", "CANCELLATION:-40000:1");

		assertError(send("POST", cancellations, "MY_CARDS", "key-c2", "{\"amountWon\":60001}"), 409,
				"CANCELLATION_AMOUNT_EXCEEDED");
		Map<String, Object> rest = data(
				send("POST", cancellations, "MY_CARDS", "key-c3", "{\"amountWon\":60000}").getBody());
		assertThat(rest).containsEntry("paymentStatus", "CANCELLED").containsEntry("cancellableAmountWon", 0);
		assertThat(data(send("GET", "/api/v1/me/orders/" + order, "MY_CARDS", null, null).getBody()))
			.containsEntry("status", "CANCELLED");
		assertError(send("POST", cancellations, "MY_CARDS", "key-c4", "{\"amountWon\":1}"), 409,
				"PAYMENT_ALREADY_CANCELLED");
		// 앞 취소의 같은 키·같은 지문은 그 결과를 그대로 돌려준다
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch(partial.getBody(),
				send("POST", cancellations, "MY_CARDS", "key-c1", "{\"amountWon\":40000}").getBody());

		Map<String, Object> detail = data(
				send("GET", "/api/v1/me/simulated-payments/" + payment, "MY_CARDS", null, null).getBody());
		assertThat(detail).containsEntry("status", "CANCELLED").containsEntry("cancellableAmountWon", 0);
		assertThat((List<?>) detail.get("cancellations")).hasSize(2);
		assertThat(map(((List<?>) detail.get("cancellations")).get(0))).containsEntry("amountWon", 40000)
			.containsEntry("cancelledAt", "2026-11-17T10:32:00+09:00");
	}

	@Test
	void simulatedFailureKeepsTheOrderPayableWithAnotherKey() {
		registerWithC01State();
		String order = createOrder("MY_CARDS");
		String path = "/api/v1/me/orders/" + order + "/simulated-payments";

		ResponseEntity<String> failed = send("POST", path, "MY_CARDS", "key-f", "{\"simulateFailure\":true}");

		assertThat(failed.getStatusCode().value()).isEqualTo(201);
		assertThat(data(failed.getBody())).containsEntry("status", "FAILED")
			.containsEntry("ledgerTransactionId", null)
			.containsEntry("cancellableAmountWon", 0);
		assertThat(map(data(failed.getBody()).get("currentOrderBenefit"))).containsEntry("amountWon", null)
			.containsEntry("status", "NEEDS_CONFIRMATION");
		assertThat(count("ledger_transactions")).isZero();
		assertThat(data(send("GET", "/api/v1/me/orders/" + order, "MY_CARDS", null, null).getBody()))
			.containsEntry("status", "CREATED")
			.containsEntry("simulatedPaymentId", null);
		assertThat(data(send("POST", path, "MY_CARDS", "key-g", null).getBody())).containsEntry("status", "APPROVED");
	}

	@Test
	void resourcesAreSeparatedByUserAndKeysAreScopedPerUser() {
		registerWithC01State();
		String order = createOrder("MY_CARDS");
		String payment = (String) data(send("POST", "/api/v1/me/orders/" + order + "/simulated-payments", "MY_CARDS",
				"shared-key", null).getBody()).get("simulatedPaymentId");

		assertError(send("GET", "/api/v1/me/simulated-payments/" + payment, "VIRTUAL_PORTFOLIO", null, null), 404,
				"SIMULATED_PAYMENT_NOT_FOUND");
		assertError(send("POST", "/api/v1/me/simulated-payments/" + payment + "/cancellations", "VIRTUAL_PORTFOLIO",
				"k", "{\"amountWon\":1}"), 404, "SIMULATED_PAYMENT_NOT_FOUND");
		assertError(send("POST", "/api/v1/me/orders/" + order + "/simulated-payments", "VIRTUAL_PORTFOLIO", "k", null),
				404, "ORDER_NOT_FOUND");
		// 가상 포트폴리오는 월간 상태 시드가 없어 확정 불가(결정 27 A3). 같은 키라도 다른 사용자의 키와 별개라 409 재사용이 아니다
		String portfolioOrder = createOrder("VIRTUAL_PORTFOLIO");
		assertError(send("POST", "/api/v1/me/orders/" + portfolioOrder + "/simulated-payments", "VIRTUAL_PORTFOLIO",
				"shared-key", null), 409, "RECOMMENDATION_NOT_DETERMINED");
		assertThat(this.jdbcTemplate.queryForObject(
				"SELECT status FROM orders WHERE order_id = ?", String.class,
				Long.parseLong(portfolioOrder.substring("ord-".length())))).isEqualTo("CREATED");
		assertError(send("GET", "/api/v1/me/simulated-payments/pay-x", "MY_CARDS", null, null), 404,
				"SIMULATED_PAYMENT_NOT_FOUND");
		assertError(send("POST", "/api/v1/me/orders/" + order + "/simulated-payments", "MY_CARDS", null, null), 400,
				"INVALID_REQUEST");
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

	private int count(String table) {
		return this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
	}

	private static void assertError(ResponseEntity<String> response, int status, String code) {
		assertThat(response.getStatusCode().value()).isEqualTo(status);
		assertThat(JsonMapper.builder().build().readValue(response.getBody(), Map.class)).containsEntry("code", code);
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
