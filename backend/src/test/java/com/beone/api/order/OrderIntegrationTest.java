package com.beone.api.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import com.beone.api.order.application.OrderClock;
import com.beone.api.order.persistence.OrderEntity;
import com.beone.api.order.persistence.OrderRepository;
import com.beone.api.support.IntegrationTest;
import com.beone.api.user.application.DemoUser;
import com.beone.api.user.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.web.client.RestClient;

/**
 * Testcontainers MySQL에서 V3 마이그레이션(ddl-auto: validate 통과)·주문과 예정 소비의 저장·조회·사용자별 분리(D-09)를
 * 실제 HTTP로 확인한다.
 *
 * <p>기대 형식은 docs/api/orders.md, "이번 달"은 고정 시계 2026-11-17 12:00(Asia/Seoul)이다.
 */
@IntegrationTest
@ActiveProfiles("local")
@Import(OrderIntegrationTest.FixedClockConfiguration.class)
class OrderIntegrationTest {

	private static final String HEADER = "X-BeONE-Demo-User";

	private static final String ORDER_BODY = """
			{"amountWon":10000,"orderDate":"2026-11-03","merchantName":"스타벅스 송도점",
			 "merchantClass":"STARBUCKS_STORE","paymentMethod":"DIRECT"}
			""";

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	UserRepository userRepository;

	@Autowired
	OrderRepository orderRepository;

	@BeforeEach
	void clearOrderTables() {
		this.jdbcTemplate.update("DELETE FROM planned_spends");
		this.jdbcTemplate.update("DELETE FROM orders");
	}

	@Test
	void orderMigrationIsApplied() {
		Boolean success = this.jdbcTemplate.queryForObject(
				"SELECT success FROM flyway_schema_history WHERE version = '3'", Boolean.class);

		assertThat(success).isTrue();
	}

	@Test
	void createdOrderIsStoredAndReadBackByOwner() {
		ResponseEntity<String> created = send("POST", "/api/v1/me/orders", "MY_CARDS", ORDER_BODY);

		assertThat(created.getStatusCode().value()).isEqualTo(201);
		String orderId = (String) data(created.getBody()).get("orderId");
		assertThat(orderId).matches("ord-[1-9][0-9]*");
		String expected = """
				{"data":{"orderId":"%s","amountWon":10000,"orderDate":"2026-11-03","orderTime":null,
				 "merchantName":"스타벅스 송도점","merchantClass":"STARBUCKS_STORE","paymentMethod":"DIRECT",
				 "status":"CREATED","createdAt":"2026-11-17T12:00:00+09:00"%s}}
				""";
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch(expected.formatted(orderId, ""), created.getBody());

		ResponseEntity<String> read = send("GET", "/api/v1/me/orders/" + orderId, "MY_CARDS", null);

		assertThat(read.getStatusCode().value()).isEqualTo(200);
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch(expected.formatted(orderId,
				",\"simulatedPaymentId\":null,\"latestRecommendationId\":null"), read.getBody());

		Map<String, Object> row = this.jdbcTemplate.queryForMap("SELECT user_id, order_time FROM orders");
		assertThat(row.get("order_time")).isNull();
		assertThat(((Number) row.get("user_id")).longValue()).isEqualTo(userId(DemoUser.MY_CARDS));
	}

	@Test
	void otherDemoUsersOrderIsNotFound() {
		ResponseEntity<String> created = send("POST", "/api/v1/me/orders", "MY_CARDS", ORDER_BODY);
		String orderId = (String) data(created.getBody()).get("orderId");

		ResponseEntity<String> other = send("GET", "/api/v1/me/orders/" + orderId, "VIRTUAL_PORTFOLIO", null);
		ResponseEntity<String> missing = send("GET", "/api/v1/me/orders/ord-999999999", "MY_CARDS", null);
		ResponseEntity<String> malformed = send("GET", "/api/v1/me/orders/" + orderId.substring(4), "MY_CARDS",
				null);

		String notFound = "{\"code\":\"ORDER_NOT_FOUND\",\"message\":\"주문을 찾을 수 없습니다.\",\"errors\":[]}";
		for (ResponseEntity<String> response : List.of(other, missing, malformed)) {
			assertThat(response.getStatusCode().value()).isEqualTo(404);
			JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch(notFound, response.getBody());
		}
	}

	/**
	 * 결정 38-5 목록 조회(local): 시연 헤더의 사용자 주문만 최신순으로 담는다. 고정 시계라 생성 시각이 모두 같으므로 주문 번호 내림차순이다.
	 */
	@Test
	void orderListShowsOnlyTheDemoUsersOrdersNewestFirst() {
		String first = (String) data(send("POST", "/api/v1/me/orders", "MY_CARDS", ORDER_BODY).getBody()).get("orderId");
		String second = (String) data(send("POST", "/api/v1/me/orders", "MY_CARDS", ORDER_BODY).getBody())
			.get("orderId");
		String portfolio = (String) data(send("POST", "/api/v1/me/orders", "VIRTUAL_PORTFOLIO", ORDER_BODY).getBody())
			.get("orderId");
		String item = """
				{"orderId":"%s","amountWon":10000,"orderDate":"2026-11-03","merchantName":"스타벅스 송도점",
				 "merchantClass":"STARBUCKS_STORE","status":"CREATED","createdAt":"2026-11-17T12:00:00+09:00",
				 "simulatedPaymentId":null}""";

		ResponseEntity<String> list = send("GET", "/api/v1/me/orders", "MY_CARDS", null);

		assertThat(list.getStatusCode().value()).isEqualTo(200);
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch("{\"data\":{\"items\":[" + item.formatted(second)
				+ "," + item.formatted(first) + "],\"page\":0,\"size\":20,\"hasNext\":false}}", list.getBody());
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch("{\"data\":{\"items\":["
				+ item.formatted(portfolio) + "],\"page\":0,\"size\":1,\"hasNext\":false}}",
				send("GET", "/api/v1/me/orders?size=1", "VIRTUAL_PORTFOLIO", null).getBody());
	}

	/**
	 * 저장소 목록 조회는 조건에 {@code user_id}가 들어간다. 다른 사용자의 행이 더 많이 있어도 이 사용자의 행만 담고, 크기보다 한 건 더 있으면
	 * {@code hasNext}다(결정 38-5 관리자 확인 조건).
	 */
	@Test
	void repositoryListIsScopedToTheUser() {
		for (int i = 0; i < 3; i++) {
			send("POST", "/api/v1/me/orders", "VIRTUAL_PORTFOLIO", ORDER_BODY);
		}
		send("POST", "/api/v1/me/orders", "MY_CARDS", ORDER_BODY);
		send("POST", "/api/v1/me/orders", "MY_CARDS", ORDER_BODY);
		long myCards = userId(DemoUser.MY_CARDS);
		Sort order = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("orderId"));

		Slice<OrderEntity> first = this.orderRepository.findByUserId(myCards, PageRequest.of(0, 1, order));
		Slice<OrderEntity> second = this.orderRepository.findByUserId(myCards, PageRequest.of(1, 1, order));
		Slice<OrderEntity> all = this.orderRepository.findByUserId(myCards, PageRequest.of(0, 50, order));

		assertThat(first.getContent()).singleElement().extracting(OrderEntity::getUserId).isEqualTo(myCards);
		assertThat(first.hasNext()).isTrue();
		assertThat(second.getContent()).singleElement().extracting(OrderEntity::getUserId).isEqualTo(myCards);
		assertThat(second.hasNext()).isFalse();
		assertThat(all.getContent()).hasSize(2).allSatisfy(row -> assertThat(row.getUserId()).isEqualTo(myCards));
		assertThat(first.getContent().get(0).getOrderId()).isGreaterThan(second.getContent().get(0).getOrderId());
	}

	@Test
	void virtualPortfolioCreatesItsOwnOrder() {
		ResponseEntity<String> created = send("POST", "/api/v1/me/orders", "VIRTUAL_PORTFOLIO", ORDER_BODY);

		assertThat(created.getStatusCode().value()).isEqualTo(201);
		String orderId = (String) data(created.getBody()).get("orderId");
		assertThat(send("GET", "/api/v1/me/orders/" + orderId, "VIRTUAL_PORTFOLIO", null).getStatusCode().value())
			.isEqualTo(200);
		assertThat(send("GET", "/api/v1/me/orders/" + orderId, "MY_CARDS", null).getStatusCode().value())
			.isEqualTo(404);
	}

	@Test
	void plannedSpendsAreReplacedInRequestOrderAndSeparatedByUser() {
		ResponseEntity<String> first = send("PUT", "/api/v1/me/planned-spends", "MY_CARDS", """
				{"items":[
				  {"plannedDate":"2026-11-25","amountWon":30000,"merchantClass":"STARBUCKS_STORE","paymentMethod":"DIRECT","merchantName":"스타벅스"},
				  {"plannedDate":"2026-11-20","amountWon":5000,"merchantClass":"CU_STORE","paymentMethod":"ELIGIBLE_SIMPLE_PAY","plannedTime":"09:30:00"}
				]}
				""");

		assertThat(first.getStatusCode().value()).isEqualTo(200);
		long firstId = this.jdbcTemplate.queryForObject("SELECT MIN(planned_spend_id) FROM planned_spends", Long.class);
		String expected = """
				{"data":{"items":[
				  {"plannedSpendId":"ps-%d","plannedDate":"2026-11-25","amountWon":30000,"merchantClass":"STARBUCKS_STORE",
				   "paymentMethod":"DIRECT","merchantName":"스타벅스","plannedTime":null},
				  {"plannedSpendId":"ps-%d","plannedDate":"2026-11-20","amountWon":5000,"merchantClass":"CU_STORE",
				   "paymentMethod":"ELIGIBLE_SIMPLE_PAY","merchantName":null,"plannedTime":"09:30:00"}
				],"usedInRecommendation":false}}
				""".formatted(firstId, firstId + 1);
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch(expected, first.getBody());
		JsonAssert.comparator(JsonCompareMode.STRICT)
			.assertIsMatch(expected, send("GET", "/api/v1/me/planned-spends", "MY_CARDS", null).getBody());

		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch(
				"{\"data\":{\"items\":[],\"usedInRecommendation\":false}}",
				send("GET", "/api/v1/me/planned-spends", "VIRTUAL_PORTFOLIO", null).getBody());

		ResponseEntity<String> portfolio = send("PUT", "/api/v1/me/planned-spends", "VIRTUAL_PORTFOLIO",
				"{\"items\":[{\"plannedDate\":\"2026-11-30\",\"amountWon\":1,\"merchantClass\":\"TAX\",\"paymentMethod\":\"AUTOPAY\"}]}");
		assertThat(portfolio.getStatusCode().value()).isEqualTo(200);
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM planned_spends WHERE user_id = ?",
				Long.class, userId(DemoUser.MY_CARDS))).isEqualTo(2L);
	}

	@Test
	void replaceIssuesNewIdsAndClearsPreviousMonthRows() {
		long myCards = userId(DemoUser.MY_CARDS);
		this.jdbcTemplate.update("INSERT INTO planned_spends (user_id, planned_date, amount_won, merchant_class, "
				+ "payment_method) VALUES (?, '2026-10-20', 7000, 'TAX', 'DIRECT')", myCards);
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch(
				"{\"data\":{\"items\":[],\"usedInRecommendation\":false}}",
				send("GET", "/api/v1/me/planned-spends", "MY_CARDS", null).getBody());

		String body = "{\"items\":[{\"plannedDate\":\"2026-11-20\",\"amountWon\":100,\"merchantClass\":\"TAX\",\"paymentMethod\":\"DIRECT\"}]}";
		Object firstId = items(send("PUT", "/api/v1/me/planned-spends", "MY_CARDS", body).getBody()).get(0).get("plannedSpendId");
		Object secondId = items(send("PUT", "/api/v1/me/planned-spends", "MY_CARDS", body).getBody()).get(0).get("plannedSpendId");

		assertThat(secondId).isNotEqualTo(firstId);
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM planned_spends WHERE user_id = ?",
				Long.class, myCards)).isEqualTo(1L);

		ResponseEntity<String> cleared = send("PUT", "/api/v1/me/planned-spends", "MY_CARDS", "{\"items\":[]}");
		JsonAssert.comparator(JsonCompareMode.STRICT)
			.assertIsMatch("{\"data\":{\"items\":[],\"usedInRecommendation\":false}}", cleared.getBody());
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM planned_spends", Long.class)).isZero();
	}

	@Test
	void rejectedReplaceKeepsExistingPlannedSpends() {
		send("PUT", "/api/v1/me/planned-spends", "MY_CARDS",
				"{\"items\":[{\"plannedDate\":\"2026-11-20\",\"amountWon\":100,\"merchantClass\":\"TAX\",\"paymentMethod\":\"DIRECT\"}]}");

		ResponseEntity<String> rejected = send("PUT", "/api/v1/me/planned-spends", "MY_CARDS",
				"{\"items\":[{\"plannedDate\":\"2026-12-01\",\"amountWon\":100,\"merchantClass\":\"TAX\",\"paymentMethod\":\"DIRECT\"}]}");

		assertThat(rejected.getStatusCode().value()).isEqualTo(400);
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM planned_spends", Long.class)).isEqualTo(1L);
	}

	private long userId(DemoUser demoUser) {
		return this.userRepository.findByDemoUser(demoUser).orElseThrow().getUserId();
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> data(String body) {
		return (Map<String, Object>) tools.jackson.databind.json.JsonMapper.builder().build()
			.readValue(body, Map.class)
			.get("data");
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
		RestClient.RequestBodySpec spec = client.method(org.springframework.http.HttpMethod.valueOf(method))
			.uri(path)
			.header(HEADER, demoUser);
		if (body != null) {
			spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
		}
		return spec.retrieve().toEntity(String.class);
	}

	/** "이번 달"과 생성 시각을 서울 2026-11-17 12:00으로 고정한다. */
	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClockConfiguration {

		@Bean
		@Primary
		OrderClock fixedOrderClock() {
			return new OrderClock(Clock.fixed(Instant.parse("2026-11-17T03:00:00Z"), ZoneOffset.UTC));
		}

	}

}
