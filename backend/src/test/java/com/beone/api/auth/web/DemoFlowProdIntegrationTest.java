package com.beone.api.auth.web;

import static com.beone.api.auth.web.MemberApiTestClient.PASSWORD;
import static com.beone.api.auth.web.MemberApiTestClient.assertNoStore;
import static com.beone.api.auth.web.MemberApiTestClient.userCardId;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.beone.api.auth.application.SmtpStubServer;
import com.beone.api.auth.web.MemberApiTestClient.Session;
import com.beone.api.support.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.json.JsonMapper;

/**
 * 시연 핵심 흐름(사용자 결정 2026-11-27: 운영에서 "로그인 → 내 카드 → 추천 → 모의결제")을 공개 구성 {@code prod,member}로 한 번에 이어
 * 돈다. 가입·이메일 인증(SMTP 스텁) → 로그인 → {@code GET /me} → 상품 조회 → 보유 카드 등록·조회 → 월간 상태 → 결제 전 설정 → 주문 →
 * 추천 계산·조회 → 모의 승인 → 결제·원장·주문 조회. 쓰기 요청은 화면처럼 요청 전에 {@code GET /api/v1/auth/csrf}로 받은 토큰을 쓴다.
 *
 * <p>기대값은 기존 계약과 평가 자료 C01(09271, 일반 국내 100,000원 직접 결제, 전월 실적 0원 → 기본 적립 1,000원,
 * {@code MemberPaymentIntegrationTest}와 같은 값)이다. 승인 본문은 화면과 같은 {@code {}}다. 시계는 서울 2026-11-18 10:00이다.
 */
@IntegrationTest
@ActiveProfiles({ "prod", "member" })
@Import(AuthSessionIntegrationTest.MutableClockConfiguration.class)
@TestPropertySource(properties = { "spring.mail.host=127.0.0.1", "spring.mail.username=synthetic-user",
		"spring.mail.password=synthetic-app-password", "spring.mail.properties[mail.smtp.starttls.enable]=false",
		"spring.mail.properties[mail.smtp.starttls.required]=false", "beone.mail.from=no-reply@beone.example.test",
		"beone.mail.link-base-url=https://beone.example.test" })
class DemoFlowProdIntegrationTest {

	private static final SmtpStubServer SMTP = new SmtpStubServer();

	private static final Pattern VERIFICATION_TOKEN = Pattern
		.compile("https://beone\\.example\\.test/email-verification\\?token=([A-Za-z0-9_-]{43})");

	private static final Instant START = Instant.parse("2026-11-18T01:00:00Z");

	private static final String EMAIL = "demo-flow@example.com";

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

	@DynamicPropertySource
	static void smtpPort(DynamicPropertyRegistry registry) {
		registry.add("spring.mail.port", SMTP::port);
	}

	@AfterAll
	static void stopSmtp() {
		SMTP.close();
	}

	private MemberApiTestClient members;

	@BeforeEach
	void setUp() {
		this.members = new MemberApiTestClient(this.port, this.jdbcTemplate);
		clearTables();
		this.clock.set(START);
	}

	@AfterEach
	void clearTables() {
		for (String table : List.of("ledger_transactions", "simulated_payments")) {
			this.jdbcTemplate.update("DELETE t FROM " + table + " t JOIN users u ON u.user_id = t.user_id"
					+ " WHERE u.member_id IS NOT NULL");
		}
		this.members.clearMemberData();
		SMTP.clear();
	}

	@Test
	void signUpLoginCardRecommendationAndSimulatedPaymentRunEndToEnd() {
		// 1. 가입·이메일 인증(메일 링크의 토큰)·로그인
		Session anonymous = this.members.anonymous();
		assertStatus(send(HttpMethod.POST, "/api/v1/auth/sign-up", anonymous,
				"{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"), 202);
		Matcher token = VERIFICATION_TOKEN.matcher(SMTP.received().getFirst().text());
		assertThat(token.find()).isTrue();
		assertStatus(send(HttpMethod.POST, "/api/v1/auth/email-verifications", anonymous,
				"{\"token\":\"" + token.group(1) + "\"}"), 204);
		Session member = this.members.login(anonymous, EMAIL);
		assertThat(data(send(HttpMethod.GET, "/api/v1/auth/session", member, null))).containsEntry("email", EMAIL);

		// 2. 내 정보: 빈 '내 카드' 장부
		assertThat(data(send(HttpMethod.GET, "/api/v1/me", member, null))).containsEntry("portfolioType", "REAL")
			.containsEntry("cardCount", 0)
			.containsEntry("codefConnected", false);

		// 3. 상품 조회(인증 불필요)와 보유 카드 수동 등록·조회
		assertThat(send(HttpMethod.GET, "/api/v1/card-products?asOf=2026-11-18", Session.NONE, null).getBody())
			.contains("\"cardProductId\":\"09271\"");
		ResponseEntity<String> registered = send(HttpMethod.POST, "/api/v1/me/cards", fresh(member),
				"{\"displayName\":\"시연 카드\",\"cardProductId\":\"09271\"}");
		assertStatus(registered, 201);
		String card = userCardId(registered.getBody());
		ResponseEntity<String> cards = send(HttpMethod.GET, "/api/v1/me/cards", member, null);
		assertNoStore(cards);
		assertThat(items(cards)).singleElement()
			.satisfies(item -> assertThat(item).containsEntry("userCardId", card)
				.satisfies(entry -> assertThat(map(entry.get("mapping"))).containsEntry("cardProductId", "09271")));

		// 4. 월간 상태 입력·조회, 결제 전 설정 조회
		String state = "/api/v1/me/cards/" + card + "/monthly-states/2026-11";
		assertStatus(send(HttpMethod.PUT, state, fresh(member), C01_STATE), 200);
		assertStatus(send(HttpMethod.GET, state, member, null), 200);
		assertStatus(send(HttpMethod.GET, "/api/v1/me/payment-preference", member, null), 200);

		// 5. 주문 → 추천 계산·조회
		ResponseEntity<String> created = send(HttpMethod.POST, "/api/v1/me/orders", fresh(member), C01_ORDER);
		assertStatus(created, 201);
		String order = (String) data(created).get("orderId");
		ResponseEntity<String> calculated = send(HttpMethod.POST, "/api/v1/me/orders/" + order + "/recommendations",
				fresh(member), null);
		assertStatus(calculated, 201);
		String recommendation = (String) data(calculated).get("recommendationId");
		Map<String, Object> read = data(send(HttpMethod.GET, "/api/v1/me/recommendations/" + recommendation, member,
				null));
		assertThat(read).containsEntry("orderId", order);
		assertThat(map(read.get("selected"))).containsEntry("userCardId", card);
		assertThat(data(send(HttpMethod.GET, "/api/v1/me/orders/" + order, member, null)))
			.containsEntry("latestRecommendationId", recommendation);

		// 6. 모의 승인(화면과 같은 빈 본문) → 결제·원장·주문 조회
		ResponseEntity<String> approved = this.members.sendWithHeaders(HttpMethod.POST,
				"/api/v1/me/orders/" + order + "/simulated-payments", fresh(member), "{}",
				Map.of("Idempotency-Key", "demo-flow-key-1"));
		assertStatus(approved, 201);
		Map<String, Object> approval = data(approved);
		assertThat(approval).containsEntry("status", "APPROVED")
			.containsEntry("amountWon", 100000)
			.containsEntry("userCardId", card)
			.containsEntry("cardProductId", "09271")
			.containsEntry("currentOrderBenefit", Map.of("amountWon", 1000, "status", "CONFIRMED"));
		String payment = (String) approval.get("simulatedPaymentId");
		String ledger = (String) approval.get("ledgerTransactionId");
		assertStatus(send(HttpMethod.GET, "/api/v1/me/simulated-payments/" + payment, member, null), 200);
		assertThat(items(send(HttpMethod.GET, "/api/v1/me/ledger-transactions?month=2026-11", member, null)))
			.singleElement()
			.satisfies(item -> assertThat(item).containsEntry("ledgerTransactionId", ledger)
				.containsEntry("simulatedPaymentId", payment)
				.containsEntry("amountWon", 100000));
		assertThat(data(send(HttpMethod.GET, "/api/v1/me/orders/" + order, member, null)))
			.containsEntry("simulatedPaymentId", payment);
		assertThat(send(HttpMethod.GET, "/api/v1/me/orders", member, null).getBody())
			.contains("\"orderId\":\"" + order + "\"");
		assertThat(data(send(HttpMethod.GET, "/api/v1/me", member, null))).containsEntry("cardCount", 1);
	}

	/** 화면은 쓰기 요청마다 {@code GET /api/v1/auth/csrf}로 토큰을 다시 받는다. 회원 세션에서 받은 토큰이 그대로 통해야 한다. */
	private Session fresh(Session member) {
		ResponseEntity<String> csrf = this.members.getCsrf(member.id());
		assertStatus(csrf, 200);
		return new Session(member.id(), (String) data(csrf).get("token"));
	}

	private ResponseEntity<String> send(HttpMethod method, String path, Session session, String body) {
		return this.members.send(method, path, session, body);
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
