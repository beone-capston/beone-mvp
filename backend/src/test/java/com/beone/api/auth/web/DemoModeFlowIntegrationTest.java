package com.beone.api.auth.web;

import static com.beone.api.auth.web.MemberApiTestClient.PASSWORD;
import static com.beone.api.auth.web.MemberApiTestClient.userCardId;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.beone.api.auth.web.MemberApiTestClient.Session;
import com.beone.api.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

/**
 * 시연 모드 서버(사용자 결정 2026-11-27 04:10, 백엔드 {@code demo} 단독 + 현재 {@code main} 프런트 빌드)의 흐름을 실제 HTTP·MySQL로 확인한다.
 *
 * <p>{@code main} 프런트는 진입 때 회원 세션을 요구하고(AuthEntry), 주문·모의 결제는 쿠키 없이 {@code X-BeONE-Demo-User} 헤더로
 * 부른다(orders/orderApi.ts·payment/paymentApi.ts, credentials omit, 승인 본문 {@code {}}). 그래서 이 시험은
 * <ol>
 * <li>로그인: 가입 → (가짜 메일이라 인증 메일을 받을 수 없으므로 런북처럼) DB에서 인증 완료 표시 → 로그인 → 세션 조회</li>
 * <li>준비: 시연 사용자 MY_CARDS에 화면의 예시 카드 4종(09271·09174·01664·01914)을 등록하고 2026-11 월간 상태 입력. MY_CARDS 시드에는 카드가
 * 없다</li>
 * <li>주문 → 모의 승인(헤더만, 쿠키 없음) → 추천·결제·원장 조회</li>
 * </ol>
 * 를 이어 돈다. 금액 기대값은 평가 자료 C01(09271 한 장, 일반 국내 100,000원 직접 결제, 전월 실적 0원 → 기본 적립 1,000원,
 * {@code SimulatedPaymentIntegrationTest}와 같은 값)이다. 4장일 때는 승인 금액이 승인 직전 재계산한 추천의 선택 카드 혜택과 같은지만 본다
 * (4장 조합의 독립 정답은 이 시험에서 새로 정하지 않는다). 시계는 서울 2026-11-18 10:00이다.
 */
@IntegrationTest
@ActiveProfiles("demo")
@Import(AuthSessionIntegrationTest.MutableClockConfiguration.class)
class DemoModeFlowIntegrationTest {

	private static final Instant START = Instant.parse("2026-11-18T01:00:00Z");

	private static final String EMAIL = "demo-presenter@example.com";

	private static final String MY_CARDS = "MY_CARDS";

	private static final List<String> SAMPLE_PRODUCTS = List.of("09271", "09174", "01664", "01914");

	private static final String C01_ORDER = """
			{"amountWon":100000,"orderDate":"2026-11-18","merchantName":"동네 마트",
			 "merchantClass":"ORDINARY_DOMESTIC","paymentMethod":"DIRECT"}""";

	private static final String C01_STATE = """
			{"previousMonthPerformanceWon":{"value":0,"period":{"from":"2026-10-01","to":"2026-10-31"}},
			 "registeredOn":{"value":"2026-01-01","period":{"from":"2026-01-01","to":"2026-01-01"}}}""";

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	AuthSessionIntegrationTest.MutableClock clock;

	private MemberApiTestClient members;

	@BeforeEach
	void setUp() {
		this.members = new MemberApiTestClient(this.port, this.jdbcTemplate);
		clearRows();
		this.clock.set(START);
	}

	@AfterEach
	void clearRows() {
		for (String table : List.of("ledger_transactions", "simulated_payments", "idempotency_keys", "recommendations",
				"payment_preferences", "monthly_states", "planned_spends", "orders")) {
			this.jdbcTemplate.update("DELETE FROM " + table);
		}
		this.jdbcTemplate.update("DELETE FROM user_cards WHERE holding_source <> 'SYNTHETIC'");
		this.members.clearMemberData();
	}

	/** 진입: demo에서도 회원 인증 API가 있어 화면의 세션 확인을 통과할 수 있다. 인증 메일 대신 DB 표시가 필요하다. */
	@Test
	void presenterCanLogInOnTheDemoServerAfterMarkingTheEmailVerified() {
		Session anonymous = this.members.anonymous();
		assertStatus(this.members.send(HttpMethod.POST, "/api/v1/auth/sign-up", anonymous,
				"{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"), 202);
		// 인증 전 로그인은 403 EMAIL_NOT_VERIFIED
		assertStatus(this.members.send(HttpMethod.POST, "/api/v1/auth/login", anonymous,
				"{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"), 403);

		// 런북 단계: 가짜 메일이라 인증 링크를 받을 수 없으므로 DB에서 인증 완료로 표시한다
		assertThat(this.jdbcTemplate.update("UPDATE members SET email_verified_at = '2026-11-18 10:00:00' WHERE email = ?",
				EMAIL)).isOne();

		Session member = this.members.login(this.members.anonymous(), EMAIL);
		assertThat(data(this.members.send(HttpMethod.GET, "/api/v1/auth/session", member, null)))
			.containsEntry("email", EMAIL);
	}

	/** C01: 카드 한 장이면 실제 혜택 계산 결과 1,000원이 승인 응답에 나온다. 화면처럼 헤더만 보내고 쿠키는 없다. */
	@Test
	void headerOnlyOrderAndApprovalShowTheCalculatedBenefit() {
		String card = registerWithState("09271");
		String order = createOrder();

		Map<String, Object> approval = approve(order, "demo-key-1");

		assertThat(approval).containsEntry("status", "APPROVED")
			.containsEntry("amountWon", 100000)
			.containsEntry("userCardId", card)
			.containsEntry("cardProductId", "09271")
			.containsEntry("currentOrderBenefit", Map.of("amountWon", 1000, "status", "CONFIRMED"));
		String recommendation = (String) map(approval.get("recalculation")).get("recommendationId");
		assertStatus(get("/api/v1/me/recommendations/" + recommendation), 200);
		assertStatus(get("/api/v1/me/simulated-payments/" + approval.get("simulatedPaymentId")), 200);
		assertThat(items(get("/api/v1/me/ledger-transactions?month=2026-11"))).singleElement()
			.satisfies(item -> assertThat(item).containsEntry("simulatedPaymentId", approval.get("simulatedPaymentId"))
				.containsEntry("amountWon", 100000));
		assertThat(data(get("/api/v1/me/orders/" + order))).containsEntry("simulatedPaymentId",
				approval.get("simulatedPaymentId"));
	}

	/** 화면의 예시 카드 4종을 등록하면 추천이 그중 한 장을 고르고, 승인 금액은 그 추천의 계산 결과와 같다. */
	@Test
	void fourSampleCardsGiveADeterminedRecommendationAndMatchingApproval() {
		for (String product : SAMPLE_PRODUCTS) {
			registerWithState(product);
		}
		// 상품 ID는 보유 카드 응답의 mapping 안에 있다(docs/api/cards.md)
		assertThat(items(get("/api/v1/me/cards"))).extracting(item -> map(item.get("mapping")).get("cardProductId"))
			.containsExactlyInAnyOrderElementsOf(SAMPLE_PRODUCTS);
		String order = createOrder();

		Map<String, Object> approval = approve(order, "demo-key-4");

		assertThat(approval).containsEntry("status", "APPROVED").containsEntry("amountWon", 100000);
		assertThat(SAMPLE_PRODUCTS).contains((String) approval.get("cardProductId"));
		Map<String, Object> benefit = map(approval.get("currentOrderBenefit"));
		assertThat(benefit.get("amountWon")).isInstanceOf(Integer.class);
		Map<String, Object> recommendation = data(
				get("/api/v1/me/recommendations/" + map(approval.get("recalculation")).get("recommendationId")));
		// 비교 가능한 카드가 0장일 때만 NOT_DETERMINED다(recommendations.md, D-18 K2). 09271은 같은 입력으로 확정되므로 선택이 있다
		assertThat(recommendation).containsEntry("outcome", "SELECTED");
		assertThat(map(recommendation.get("selected"))).containsEntry("userCardId", approval.get("userCardId"));
	}

	/** 준비 없이 가상 포트폴리오로 승인하면 월간 상태 시드가 없어 확정할 수 없다(런북 준비가 필요한 이유). */
	@Test
	void virtualPortfolioWithoutPreparationCannotBeApproved() {
		ResponseEntity<String> created = this.members.sendWithDemoHeader(HttpMethod.POST, "/api/v1/me/orders",
				Session.NONE, C01_ORDER, "VIRTUAL_PORTFOLIO");
		assertStatus(created, 201);
		ResponseEntity<String> approved = this.members.sendWithHeaders(HttpMethod.POST,
				"/api/v1/me/orders/" + data(created).get("orderId") + "/simulated-payments", Session.NONE, "{}",
				Map.of("X-BeONE-Demo-User", "VIRTUAL_PORTFOLIO", "Idempotency-Key", "demo-key-v"));
		assertStatus(approved, 409);
		assertThat(approved.getBody()).contains("\"code\":\"RECOMMENDATION_NOT_DETERMINED\"");
	}

	private String registerWithState(String product) {
		ResponseEntity<String> registered = this.members.sendWithDemoHeader(HttpMethod.POST, "/api/v1/me/cards",
				Session.NONE, "{\"displayName\":\"예시 " + product + "\",\"cardProductId\":\"" + product + "\"}", MY_CARDS);
		assertStatus(registered, 201);
		String card = userCardId(registered.getBody());
		assertStatus(this.members.sendWithDemoHeader(HttpMethod.PUT,
				"/api/v1/me/cards/" + card + "/monthly-states/2026-11", Session.NONE, C01_STATE, MY_CARDS), 200);
		return card;
	}

	private String createOrder() {
		ResponseEntity<String> created = this.members.sendWithDemoHeader(HttpMethod.POST, "/api/v1/me/orders",
				Session.NONE, C01_ORDER, MY_CARDS);
		assertStatus(created, 201);
		return (String) data(created).get("orderId");
	}

	private Map<String, Object> approve(String order, String key) {
		ResponseEntity<String> approved = this.members.sendWithHeaders(HttpMethod.POST,
				"/api/v1/me/orders/" + order + "/simulated-payments", Session.NONE, "{}",
				Map.of("X-BeONE-Demo-User", MY_CARDS, "Idempotency-Key", key));
		assertStatus(approved, 201);
		return data(approved);
	}

	private ResponseEntity<String> get(String path) {
		return this.members.getWithDemoHeader(path, Session.NONE, MY_CARDS);
	}

	private static void assertStatus(ResponseEntity<String> response, int status) {
		assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(status);
	}

	private static Map<String, Object> data(ResponseEntity<String> response) {
		return map(JsonMapper.builder().build().readValue(response.getBody(), Map.class).get("data"));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> map(Object value) {
		return (Map<String, Object>) value;
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> items(ResponseEntity<String> response) {
		return (List<Map<String, Object>>) data(response).get("items");
	}

}
