package com.beone.api.auth.web;

import static com.beone.api.auth.web.MemberApiTestClient.PASSWORD;
import static com.beone.api.auth.web.MemberApiTestClient.userCardId;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
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
 * 시연 모드 서버 준비 스크립트({@code deploy/demo/seed-demo.sh})의 순서와 값을 실제 HTTP·MySQL로 고정한다.
 *
 * <p>스크립트와 같은 파일({@code deploy/demo/seed/cards.tsv}, {@code state-<상품>.json})을 읽어, 시연 회원 가입 → DB 인증 완료 표시 →
 * 로그인, 시연 사용자 MY_CARDS에 예시 카드 4종 등록·월간 상태 입력을 한 뒤 다음을 확인한다.
 * <ul>
 * <li>일반 국내 100,000원 직접 결제 주문(평가 자료 C01과 같은 주문)의 추천이 조건부가 아닌 확정 추천이고, 4장 모두 비교 대상(제외 카드 없음)이며
 * 현재 주문 혜택이 모두 확정 값이다. 09271만 기본 적립 1,000원(실적 조건 없는 서비스)이고 나머지 3장은 이 업종에 해당 서비스가 없어 확정 0원이다.</li>
 * <li>자동 추천으로 모의 승인하면 09271로 승인되고 혜택은 1,000원 확정이다. 카드마다 대표 카드로 고정해 승인해도 그 카드로 승인되고 혜택은 확정 값이다
 * (09271 1,000원, 나머지 0원).</li>
 * </ul>
 * 나머지 3장의 서비스는 모두 전월 실적 조건이 있고, 업무 API가 실적 정책 근거를 {@code UNDETERMINED}로 넘기므로(recommendations.md) 그 서비스가
 * 적용되는 업종(예: 스타벅스)의 주문에서는 "확인 필요"가 된다. 이 시험은 그 한계 안에서 시연 화면이 확정 값만 보이는 주문을 고정한다.
 * 1,000원은 {@code SimulatedPaymentIntegrationTest}의 C01 기대값과 같다. 시계는 서울 2026-11-18 10:00이다.
 */
@IntegrationTest
@ActiveProfiles("demo")
@Import(AuthSessionIntegrationTest.MutableClockConfiguration.class)
class DemoSeedIntegrationTest {

	private static final Instant START = Instant.parse("2026-11-18T01:00:00Z");

	private static final String EMAIL = "demo-presenter@example.com";

	private static final String MY_CARDS = "MY_CARDS";

	private static final Path SEED = Path.of("..", "deploy", "demo", "seed");

	private static final String ORDINARY_ORDER = """
			{"amountWon":100000,"orderDate":"2026-11-18","merchantName":"동네 마트",
			 "merchantClass":"ORDINARY_DOMESTIC","paymentMethod":"DIRECT"}""";

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

	@Test
	void seededDemoServerGivesADeterminedRecommendationOverAllFourSampleCards() throws IOException {
		// 1. 시연 회원: 가입 → 가짜 메일이라 DB에서 인증 완료 표시 → 로그인
		assertStatus(this.members.send(HttpMethod.POST, "/api/v1/auth/sign-up", this.members.anonymous(),
				"{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"), 202);
		assertThat(this.jdbcTemplate.update(
				"UPDATE members SET email_verified_at = NOW() WHERE email = ? AND email_verified_at IS NULL", EMAIL))
			.isOne();
		Session member = this.members.login(this.members.anonymous(), EMAIL);
		assertStatus(this.members.send(HttpMethod.GET, "/api/v1/auth/session", member, null), 200);

		// 2. MY_CARDS에 예시 카드 4종과 월간 상태(스크립트와 같은 파일)
		Map<String, String> cards = new LinkedHashMap<>();
		for (String line : Files.readAllLines(SEED.resolve("cards.tsv"), StandardCharsets.UTF_8)) {
			String[] fields = line.split("\t");
			ResponseEntity<String> registered = this.members.sendWithDemoHeader(HttpMethod.POST, "/api/v1/me/cards",
					Session.NONE, "{\"displayName\":\"" + fields[1] + "\",\"cardProductId\":\"" + fields[0] + "\"}",
					MY_CARDS);
			assertStatus(registered, 201);
			String card = userCardId(registered.getBody());
			cards.put(fields[0], card);
			String state = Files.readString(SEED.resolve("state-" + fields[0] + ".json"), StandardCharsets.UTF_8)
				.replace("{{PREV_FROM}}", "2026-10-01")
				.replace("{{PREV_TO}}", "2026-10-31")
				.replace("{{MONTH_FROM}}", "2026-11-01")
				.replace("{{MONTH_TO}}", "2026-11-30");
			assertStatus(this.members.sendWithDemoHeader(HttpMethod.PUT,
					"/api/v1/me/cards/" + card + "/monthly-states/2026-11", Session.NONE, state, MY_CARDS), 200);
		}
		assertThat(cards).containsOnlyKeys("09271", "09174", "01664", "01914");

		// 3. 추천: 확정 추천, 4장 모두 비교 대상, 현재 주문 혜택은 모두 확정 값(09271 1,000원, 나머지 0원)
		ResponseEntity<String> recommended = this.members.sendWithDemoHeader(HttpMethod.POST,
				"/api/v1/me/orders/" + createOrder() + "/recommendations", Session.NONE, null, MY_CARDS);
		assertStatus(recommended, 201);
		Map<String, Object> recommendation = data(recommended);
		assertThat(list(recommendation.get("excludedCards"))).as(recommended.getBody()).isEmpty();
		assertThat(recommendation).as(recommended.getBody())
			.containsEntry("outcome", "SELECTED")
			.containsEntry("conditional", false);
		assertThat(map(recommendation.get("selected"))).containsEntry("userCardId", cards.get("09271"));
		List<Map<String, Object>> compared = list(recommendation.get("comparedCards"));
		assertThat(compared).extracting(card -> card.get("cardProductId"))
			.containsExactlyInAnyOrderElementsOf(cards.keySet());
		for (Map<String, Object> card : compared) {
			assertThat(map(card.get("currentOrderBenefit"))).as(recommended.getBody())
				.isEqualTo(Map.of("amountWon", "09271".equals(card.get("cardProductId")) ? 1000 : 0, "status", "CONFIRMED"));
		}

		// 4. 자동 추천 승인: 09271, 1,000원 확정
		ResponseEntity<String> automatic = this.members.sendWithHeaders(HttpMethod.POST,
				"/api/v1/me/orders/" + createOrder() + "/simulated-payments", Session.NONE, "{}",
				Map.of("X-BeONE-Demo-User", MY_CARDS, "Idempotency-Key", "demo-seed-auto"));
		assertStatus(automatic, 201);
		assertThat(data(automatic)).as(automatic.getBody())
			.containsEntry("status", "APPROVED")
			.containsEntry("userCardId", cards.get("09271"))
			.containsEntry("currentOrderBenefit", Map.of("amountWon", 1000, "status", "CONFIRMED"));

		// 5. 카드별 모의 승인: 대표 카드로 고정하면 그 카드로 승인되고 혜택은 확정 값
		int key = 0;
		for (Map.Entry<String, String> card : cards.entrySet()) {
			assertStatus(this.members.sendWithDemoHeader(HttpMethod.PUT, "/api/v1/me/payment-preference", Session.NONE,
					"{\"selectionMode\":\"FIXED_REPRESENTATIVE\",\"representativeUserCardId\":\"" + card.getValue() + "\"}",
					MY_CARDS), 200);
			ResponseEntity<String> approved = this.members.sendWithHeaders(HttpMethod.POST,
					"/api/v1/me/orders/" + createOrder() + "/simulated-payments", Session.NONE, "{}",
					Map.of("X-BeONE-Demo-User", MY_CARDS, "Idempotency-Key", "demo-seed-" + (++key)));
			assertStatus(approved, 201);
			Map<String, Object> approval = data(approved);
			assertThat(approval).as(approved.getBody())
				.containsEntry("status", "APPROVED")
				.containsEntry("userCardId", card.getValue())
				.containsEntry("cardProductId", card.getKey());
			assertThat(map(approval.get("currentOrderBenefit"))).as(approved.getBody())
				.isEqualTo(Map.of("amountWon", "09271".equals(card.getKey()) ? 1000 : 0, "status", "CONFIRMED"));
		}
	}

	private String createOrder() {
		ResponseEntity<String> created = this.members.sendWithDemoHeader(HttpMethod.POST, "/api/v1/me/orders",
				Session.NONE, ORDINARY_ORDER, MY_CARDS);
		assertStatus(created, 201);
		return (String) data(created).get("orderId");
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
	private static List<Map<String, Object>> list(Object value) {
		return (List<Map<String, Object>>) value;
	}

}
