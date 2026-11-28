package com.beone.api.ledger;

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
import org.assertj.core.api.InstanceOfAssertFactories;
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
 * Testcontainers MySQL에서 모의 승인·부분취소가 남긴 원장 행을 실제 HTTP로 조회한다. 행은 모의 결제 API로만 만든다.
 *
 * <p>기대값: 계약 응답 예시의 모양(승인 양수·취소 음수·원 승인 연결·{@code attributionMonth} CONFIRMED·SIMULATION 완전)과 관리자 승인
 * 규칙(#118: 거래일 기준 달, 거래일·ID 오름차순, 1차 CODEF는 {@code complete=false}·사유 없음, 해제 카드·다른 사용자 카드 404),
 * D-09 사용자별 분리. 주문·월간 상태 값은 평가 자료 C01(09271, 100,000원 직접 결제, 전월 실적 0원)을 2026-11로 옮겼다.
 */
@IntegrationTest
@ActiveProfiles("local")
@Import(LedgerTransactionIntegrationTest.ClockConfiguration.class)
class LedgerTransactionIntegrationTest {

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
	void showsTheSimulatedApprovalAndCancellationOfTheMonth() {
		String card = registerWithC01State();
		String order = createOrder();
		NOW.set(START.plusSeconds(60));
		Map<String, Object> approval = data(send("POST", "/api/v1/me/orders/" + order + "/simulated-payments",
				"MY_CARDS", "key-a", null).getBody());
		String payment = (String) approval.get("simulatedPaymentId");
		String approvalLedger = (String) approval.get("ledgerTransactionId");
		NOW.set(START.plusSeconds(120));
		String cancellationLedger = (String) data(send("POST", "/api/v1/me/simulated-payments/" + payment
				+ "/cancellations", "MY_CARDS", "key-c", "{\"amountWon\":40000}").getBody()).get("ledgerTransactionId");

		ResponseEntity<String> response = get("MY_CARDS", "month=2026-11&userCardId=" + card + "&source=SIMULATION");

		assertThat(response.getStatusCode().value()).isEqualTo(200);
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch("""
				{"data":{"month":"2026-11","items":[
				 {"ledgerTransactionId":"%1$s","userCardId":"%3$s","source":"SIMULATION","entryType":"APPROVAL",
				  "amountWon":100000,"transactionDate":"2026-11-17","transactionTime":null,"merchantClass":"ORDINARY_DOMESTIC",
				  "paymentMethod":"DIRECT","transactionNature":null,"merchantName":"동네 마트","originalTransactionId":null,
				  "simulatedPaymentId":"%4$s","attributionMonth":{"value":"2026-11","status":"CONFIRMED"},"syncId":null,"fullyCanceled":null},
				 {"ledgerTransactionId":"%2$s","userCardId":"%3$s","source":"SIMULATION","entryType":"CANCELLATION",
				  "amountWon":-40000,"transactionDate":"2026-11-17","transactionTime":null,"merchantClass":"ORDINARY_DOMESTIC",
				  "paymentMethod":"DIRECT","transactionNature":null,"merchantName":"동네 마트","originalTransactionId":"%1$s",
				  "simulatedPaymentId":"%4$s","attributionMonth":{"value":"2026-11","status":"CONFIRMED"},"syncId":null,"fullyCanceled":null}],
				 "coverage":[{"userCardId":"%3$s","source":"SIMULATION","period":{"from":"2026-11-01","to":"2026-11-30"},
				  "complete":true,"incompleteReasons":[]}]}}
				""".formatted(approvalLedger, cancellationLedger, card, payment), response.getBody());

		// 출처 생략: 카드마다 SIMULATION·CODEF 한 항목씩, 1차 CODEF는 완전 선언 안 함
		Map<String, Object> all = data(get("MY_CARDS", "month=2026-11").getBody());
		assertThat((List<?>) all.get("items")).hasSize(2);
		assertThat(all.get("coverage")).asInstanceOf(InstanceOfAssertFactories.LIST).containsExactly(
				Map.of("userCardId", card, "source", "SIMULATION",
						"period", Map.of("from", "2026-11-01", "to", "2026-11-30"), "complete", true,
						"incompleteReasons", List.of()),
				Map.of("userCardId", card, "source", "CODEF",
						"period", Map.of("from", "2026-11-01", "to", "2026-11-30"), "complete", false,
						"incompleteReasons", List.of()));
		// CODEF만: 행 없음
		assertThat((List<?>) data(get("MY_CARDS", "month=2026-11&source=CODEF").getBody()).get("items")).isEmpty();
		// 다른 달: 거래일 기준이라 행 없음, 완전성은 그 달 기간
		Map<String, Object> october = data(get("MY_CARDS", "month=2026-10").getBody());
		assertThat((List<?>) october.get("items")).isEmpty();
		assertThat(map(((List<?>) october.get("coverage")).get(0)).get("period"))
			.isEqualTo(Map.of("from", "2026-10-01", "to", "2026-10-31"));
	}

	@Test
	void ledgerIsSeparatedByUser() {
		String card = registerWithC01State();
		String order = createOrder();
		send("POST", "/api/v1/me/orders/" + order + "/simulated-payments", "MY_CARDS", "key-a", null);

		// 가상 포트폴리오 사용자는 MY_CARDS의 행을 보지 못하고, 그 카드 ID는 없는 카드다
		Map<String, Object> portfolio = data(get("VIRTUAL_PORTFOLIO", "month=2026-11").getBody());
		assertThat((List<?>) portfolio.get("items")).isEmpty();
		assertThat((List<?>) portfolio.get("coverage")).isNotEmpty()
			.allSatisfy(item -> assertThat(map(item).get("userCardId")).isNotEqualTo(card));
		assertError(get("VIRTUAL_PORTFOLIO", "month=2026-11&userCardId=" + card), 404, "USER_CARD_NOT_FOUND");
		assertError(get("MY_CARDS", "month=2026-11&userCardId=uc-x"), 404, "USER_CARD_NOT_FOUND");
		assertError(get("MY_CARDS", "month=2026-13"), 400, "INVALID_REQUEST");
		assertError(get("MY_CARDS", "month=2026-11&source=MANUAL"), 400, "INVALID_REQUEST");

		// 해제한 카드는 없는 카드이고 그 행도 보이지 않는다
		assertThat(send("DELETE", "/api/v1/me/cards/" + card, "MY_CARDS", null, null).getStatusCode().is2xxSuccessful())
			.isTrue();
		assertError(get("MY_CARDS", "month=2026-11&userCardId=" + card), 404, "USER_CARD_NOT_FOUND");
		assertThat((List<?>) data(get("MY_CARDS", "month=2026-11").getBody()).get("items")).isEmpty();
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

	private String createOrder() {
		ResponseEntity<String> created = send("POST", "/api/v1/me/orders", "MY_CARDS", null, C01_ORDER);
		assertThat(created.getStatusCode().value()).isEqualTo(201);
		return (String) data(created.getBody()).get("orderId");
	}

	private ResponseEntity<String> get(String demoUser, String query) {
		return send("GET", "/api/v1/me/ledger-transactions?" + query, demoUser, null, null);
	}

	private static void assertError(ResponseEntity<String> response, int status, String code) {
		assertThat(response.getStatusCode().value()).isEqualTo(status);
		assertThat(JsonMapper.builder().build().readValue(response.getBody(), Map.class)).containsEntry("code", code);
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
