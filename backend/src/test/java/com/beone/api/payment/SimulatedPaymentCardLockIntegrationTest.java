package com.beone.api.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
 * 같은 카드 동시 모의 승인의 한도 경합 방지(#122)를 Testcontainers MySQL과 실제 HTTP로 확인한다.
 *
 * <p>기대값의 근거: 1차 4종 규칙에는 한도 차감액이 확정되는 승인이 없다. 09174 GREAT(스타벅스 50%, 월 한도 10,000원, 전월 실적
 * 300,000원 이상)는 취소 귀속이 미확정이라 차감액을 모르고(D-18 K1, 원장에는 {@code null}로 남는다), #120 규칙은 스냅샷 뒤 차감액을 모르는
 * 승인이 있으면 그 버킷 사용액을 확인 필요로 넘긴다(D-16). 그래서 앞 승인의 원장을 읽은 재계산은 엔진이 "GREAT 사용액을 모름"을 사유로
 * 내고, 읽지 못한 재계산은 실적 정책(취소 귀속) 사유만 낸다. 승인은 대표 카드 고정 모드라 금액이 확인 필요여도 진행한다
 * (docs/api/mock-payments.md). 같은 사용자의 두 주문을 동시에 승인해도 뒤 승인이 앞 승인의 원장을 읽어야 한다.
 */
@IntegrationTest
@ActiveProfiles("local")
@Import(SimulatedPaymentCardLockIntegrationTest.ClockConfiguration.class)
class SimulatedPaymentCardLockIntegrationTest {

	private static final String HEADER = "X-BeONE-Demo-User";

	private static final Instant START = Instant.parse("2026-11-17T01:30:00Z");

	private static final String STARBUCKS_ORDER = """
			{"amountWon":15000,"orderDate":"2026-11-17","merchantName":"스타벅스 송도점",
			 "merchantClass":"STARBUCKS_STORE","paymentMethod":"DIRECT"}
			""";

	private static final String NOVEMBER = "\"period\":{\"from\":\"2026-11-01\",\"to\":\"2026-11-30\"}";

	private static final String GREAT_STATE = """
			{"previousMonthPerformanceWon":{"value":300000,"period":{"from":"2026-10-01","to":"2026-10-31"}},
			 "limitBucketUsages":[{"limitBucketId":"GREAT","value":0,%1$s},{"limitBucketId":"ENJOY","value":0,%1$s},
			  {"limitBucketId":"CHECK","value":0,%1$s},{"limitBucketId":"BASIC","value":0,%1$s}],
			 "otherBucketsUsedWon":{"value":0,%1$s},
			 "registeredOn":{"value":"2026-01-01","period":{"from":"2026-01-01","to":"2026-01-01"}}}
			""".formatted(NOVEMBER);

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
	void aLaterApprovalOnTheSameCardSeesTheEarlierApprovalInItsRecalculation() {
		registerFixedGreatCard();
		String first = createOrder();
		String second = createOrder();
		NOW.set(START.plusSeconds(60));

		List<Map<String, Object>> approvals = List.of(data(approve(first, "key-1").getBody()),
				data(approve(second, "key-2").getBody()));

		assertSecondSawTheFirst(approvals);
	}

	@Test
	void concurrentApprovalsOfTwoOrdersOnTheSameCardAreSerializedByTheCardLock() throws Exception {
		registerFixedGreatCard();
		String first = createOrder();
		String second = createOrder();
		NOW.set(START.plusSeconds(60));
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		List<Future<ResponseEntity<String>>> results = new ArrayList<>();
		List<Map<String, Object>> approvals = new ArrayList<>();
		try {
			for (String[] request : new String[][] { { first, "key-1" }, { second, "key-2" } }) {
				results.add(pool.submit(() -> {
					ready.countDown();
					go.await();
					return approve(request[0], request[1]);
				}));
			}
			assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
			go.countDown();
			for (Future<ResponseEntity<String>> result : results) {
				ResponseEntity<String> response = result.get(60, TimeUnit.SECONDS);
				assertThat(response.getStatusCode().value()).isEqualTo(201);
				approvals.add(data(response.getBody()));
			}
		}
		finally {
			pool.shutdownNow();
		}

		// 먼저 잠금을 얻은 승인(원장 행 ID가 작은 쪽)과 뒤 승인을 나눠 본다
		approvals.sort(Comparator.comparingLong(approval -> ledgerKey((String) approval.get("ledgerTransactionId"))));
		assertSecondSawTheFirst(approvals);
	}

	// 두 승인 모두 확정되고 원장 차감액은 0이 아니라 모름(null)으로 남는다. 뒤 승인의 재계산만 앞 승인 때문에 GREAT 사용액을 모른다
	private void assertSecondSawTheFirst(List<Map<String, Object>> approvals) {
		assertThat(approvals).extracting(approval -> approval.get("status")).containsExactly("APPROVED", "APPROVED");
		assertThat(this.jdbcTemplate.queryForList("SELECT e.deduction_won FROM ledger_usage_effects e"
				+ " JOIN ledger_transactions t ON t.ledger_id = e.ledger_id"
				+ " WHERE e.limit_bucket_id = 'GREAT' ORDER BY t.ledger_id", Long.class))
			.containsExactly(null, null);
		Map<String, Object> earlier = pendingReason(approvals.get(0));
		assertThat(earlier).containsEntry("kind", "PERFORMANCE_POLICY").containsEntry("serviceIds", List.of("GREAT"));
		Map<String, Object> later = pendingReason(approvals.get(1));
		assertThat(later).containsEntry("kind", "NEEDS_CONFIRMATION").containsEntry("serviceIds", List.of("GREAT"));
		assertThat((String) later.get("detail")).contains("limit bucket GREAT is UNKNOWN");
	}

	// 승인 직전 재계산으로 저장한 추천의 카드 사유(카드 한 장이라 하나)
	private Map<String, Object> pendingReason(Map<String, Object> approval) {
		String recommendation = (String) map(approval.get("recalculation")).get("recommendationId");
		Map<String, Object> document = data(
				send("GET", "/api/v1/me/recommendations/" + recommendation, "MY_CARDS", null, null).getBody());
		Map<String, Object> card = map(((List<?>) document.get("excludedCards")).get(0));
		List<?> reasons = (List<?>) card.get("pendingReasons");
		assertThat(reasons).hasSize(1);
		return map(reasons.get(0));
	}

	private static long ledgerKey(String ledgerTransactionId) {
		return Long.parseLong(ledgerTransactionId.substring("lt-".length()));
	}

	private ResponseEntity<String> approve(String order, String key) {
		return send("POST", "/api/v1/me/orders/" + order + "/simulated-payments", "MY_CARDS", key, null);
	}

	private void registerFixedGreatCard() {
		ResponseEntity<String> created = send("POST", "/api/v1/me/cards", "MY_CARDS", null,
				"{\"displayName\":\"그레이트\",\"cardProductId\":\"09174\"}");
		assertThat(created.getStatusCode().value()).isEqualTo(201);
		String card = (String) data(created.getBody()).get("userCardId");
		assertThat(send("PUT", "/api/v1/me/cards/" + card + "/monthly-states/2026-11", "MY_CARDS", null, GREAT_STATE)
			.getStatusCode()
			.value()).isEqualTo(200);
		assertThat(send("PUT", "/api/v1/me/payment-preference", "MY_CARDS", null,
				"{\"selectionMode\":\"FIXED_REPRESENTATIVE\",\"representativeUserCardId\":\"" + card + "\"}")
			.getStatusCode()
			.value()).isEqualTo(200);
	}

	private String createOrder() {
		ResponseEntity<String> created = send("POST", "/api/v1/me/orders", "MY_CARDS", null, STARBUCKS_ORDER);
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
