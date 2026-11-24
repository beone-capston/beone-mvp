package com.beone.api.auth.web;

import static com.beone.api.auth.web.MemberApiTestClient.CSRF_INVALID;
import static com.beone.api.auth.web.MemberApiTestClient.PASSWORD;
import static com.beone.api.auth.web.MemberApiTestClient.UNAUTHENTICATED;
import static com.beone.api.auth.web.MemberApiTestClient.USER_CARD_NOT_FOUND;
import static com.beone.api.auth.web.MemberApiTestClient.assertBody;
import static com.beone.api.auth.web.MemberApiTestClient.assertNoStore;
import static com.beone.api.auth.web.MemberApiTestClient.userCardId;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import com.beone.api.auth.application.FakeAuthMailSender;
import com.beone.api.auth.web.MemberApiTestClient.Session;
import com.beone.api.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 로컬 회원 프로필 {@code member}의 첫 수직 기능(G2)을 실제 HTTP·MySQL로 확인한다. 회원 세션 쿠키 → 회원 → '내 카드' 장부 순서로 현재
 * 사용자를 정하고, {@code GET /api/v1/me}와 보유 카드 API를 회원별로 나눈다.
 *
 * <p>기대값은 결정 37(사용자 직접 선택)이다.
 * <ul>
 * <li>D1a: 회원 1명은 '내 카드'(REAL) 장부 1개를 가지며 이메일 인증 때 만든다</li>
 * <li>D3: 업무 요청도 미사용 만료(7일)를 늘리고 쿠키를 다시 심는다. 절대 만료(30일)는 그대로다</li>
 * <li>D4: member 프로필은 시연 헤더를 무시한다</li>
 * <li>D5: 401 → CSRF 403 → 본문 400 → 404 순서. 남의 자원은 404이고 {@code FORBIDDEN}은 쓰지 않는다</li>
 * <li>D6: 쓰기 요청은 {@code X-CSRF-TOKEN}이 필요하고, 실패하면 403이며 저장하지 않는다</li>
 * <li>D7: {@code /me}는 demoUser=null, REAL, 쓰기 가능, '내 카드'</li>
 * <li>G1 D5 권장 4항: {@code /api/v1/me}·{@code /api/v1/me/**}의 응답(401·403·쿠키를 다시 심는 200·미등록 404)에는
 * {@code Cache-Control: no-store}가 붙는다. 인증 API와 같은 규칙이다(docs/api/auth.md)</li>
 * </ul>
 * member 프로필에 등록하지 않은 업무 API(CODEF·시연 사용자 목록)는 404다. 월간 상태·결제 설정은 G3a, 주문·예정 소비·추천은 G3b,
 * 원장 조회는 #165, 모의 결제 3개는 G3c(#161, 결정 38)에서 등록했고 {@link MemberMonthlyStatePreferenceIntegrationTest},
 * {@link MemberOrderRecommendationIntegrationTest}, {@link MemberLedgerIntegrationTest},
 * {@link MemberPaymentIntegrationTest}가 확인한다. 시계는 주입한 가변 시계로 서울 2026-11-18 10:00에서 시작한다.
 */
@IntegrationTest
@ActiveProfiles("member")
@Import(AuthSessionIntegrationTest.MutableClockConfiguration.class)
class MemberProfileIntegrationTest {

	private static final Instant START = Instant.parse("2026-11-18T01:00:00Z");

	private static final String MEMBER_A = "usr-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

	private static final String MEMBER_B = "usr-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

	private static final String CARD = "{\"displayName\":\"내 첫 카드\",\"cardProductId\":\"09174\"}";

	private static final String METHOD_NOT_ALLOWED = "{\"code\":\"METHOD_NOT_ALLOWED\",\"message\":\"지원하지 않는 요청 방식입니다.\",\"errors\":[]}";

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	AuthSessionIntegrationTest.MutableClock clock;

	@Autowired
	FakeAuthMailSender mailSender;

	private MemberApiTestClient members;

	@BeforeEach
	void setUp() {
		this.members = new MemberApiTestClient(this.port, this.jdbcTemplate);
		clearTables();
		this.clock.set(START);
	}

	@AfterEach
	void clearTables() {
		this.members.clearMemberData();
		this.mailSender.clear();
	}

	/** D1a·D7: 가입 → 이메일 인증 → 로그인 뒤 /me는 회원의 '내 카드' 장부다. */
	@Test
	void signUpVerifyLoginThenMeShowsMemberLedger() throws Exception {
		Session anonymous = anonymous();
		assertThat(send(HttpMethod.POST, "/api/v1/auth/sign-up", anonymous,
				"{\"email\":\"new@example.com\",\"password\":\"" + PASSWORD + "\"}")
			.getStatusCode()
			.value()).isEqualTo(202);
		String token = this.mailSender.sentMails().get(0).token();
		assertThat(send(HttpMethod.POST, "/api/v1/auth/email-verifications", anonymous,
				"{\"token\":\"" + token + "\"}")
			.getStatusCode()
			.value()).isEqualTo(204);

		Session member = login(anonymous, "new@example.com");

		assertBody(send(HttpMethod.GET, "/api/v1/me", member, null), 200, """
				{"data":{"demoUser":null,"displayName":"내 카드","portfolioType":"REAL","readOnly":false,
				  "cardCount":0,"codefConnected":false}}
				""");
	}

	/** D1a: 회원마다 장부가 나뉘고, 남의 카드는 조회·변경·해제 모두 404다. */
	@Test
	void membersSeeOnlyTheirOwnCardsAndOthersAreNotFound() throws Exception {
		Session a = loginNew(MEMBER_A, "a@example.com");
		Session b = loginNew(MEMBER_B, "b@example.com");

		ResponseEntity<String> registered = send(HttpMethod.POST, "/api/v1/me/cards", a, CARD);
		assertThat(registered.getStatusCode().value()).isEqualTo(201);
		String cardOfA = userCardId(registered.getBody());

		assertThat(send(HttpMethod.GET, "/api/v1/me/cards", a, null).getBody()).contains(cardOfA);
		assertBody(send(HttpMethod.GET, "/api/v1/me/cards", b, null), 200, "{\"data\":{\"items\":[]}}");
		assertThat(send(HttpMethod.GET, "/api/v1/me", a, null).getBody()).contains("\"cardCount\":1");
		assertThat(send(HttpMethod.GET, "/api/v1/me", b, null).getBody()).contains("\"cardCount\":0");

		assertNotFound(send(HttpMethod.PATCH, "/api/v1/me/cards/" + cardOfA, b, "{\"displayName\":\"남의 카드\"}"));
		assertNotFound(send(HttpMethod.PATCH, "/api/v1/me/cards/" + cardOfA + "/mapping", b,
				"{\"status\":\"NOT_SUPPORTED\"}"));
		assertNotFound(send(HttpMethod.DELETE, "/api/v1/me/cards/" + cardOfA, b, null));
		// 남의 카드와 없는 카드는 코드·본문이 같아 존재 여부가 드러나지 않는다
		String missing = "/api/v1/me/cards/uc-999999999";
		assertThat(send(HttpMethod.PATCH, missing, b, "{\"displayName\":\"남의 카드\"}").getBody())
			.isEqualTo(send(HttpMethod.PATCH, "/api/v1/me/cards/" + cardOfA, b, "{\"displayName\":\"남의 카드\"}")
				.getBody());
		assertThat(send(HttpMethod.DELETE, missing, b, null).getBody())
			.isEqualTo(send(HttpMethod.DELETE, "/api/v1/me/cards/" + cardOfA, b, null).getBody());
		assertThat(activeCardCount(MEMBER_A)).isOne();
		assertThat(this.jdbcTemplate.queryForObject("SELECT display_name FROM user_cards WHERE user_card_id = ?",
				String.class, Long.parseLong(cardOfA.substring("uc-".length()))))
			.isEqualTo("내 첫 카드");
	}

	/** D4: 시연 헤더는 무시되고, 시연 포트폴리오의 카드에는 닿지 않는다. */
	@Test
	void demoHeaderIsIgnoredAndDemoPortfolioIsUnreachable() throws Exception {
		Session a = loginNew(MEMBER_A, "a@example.com");
		Long portfolioCard = this.jdbcTemplate.queryForObject(
				"SELECT MIN(uc.user_card_id) FROM user_cards uc JOIN users u ON u.user_id = uc.user_id"
						+ " WHERE u.demo_user = 'VIRTUAL_PORTFOLIO' AND uc.released_at IS NULL",
				Long.class);
		assertThat(portfolioCard).isNotNull();

		ResponseEntity<String> me = this.members.getWithDemoHeader("/api/v1/me", a, "VIRTUAL_PORTFOLIO");
		assertThat(me.getBody()).contains("\"demoUser\":null").contains("\"readOnly\":false");
		assertBody(send(HttpMethod.GET, "/api/v1/me/cards", a, null), 200, "{\"data\":{\"items\":[]}}");
		assertNotFound(send(HttpMethod.DELETE, "/api/v1/me/cards/uc-" + portfolioCard, a, null));
		assertNotFound(send(HttpMethod.PATCH, "/api/v1/me/cards/uc-" + portfolioCard, a, "{\"displayName\":\"x\"}"));

		// 쿠키 없이 시연 헤더만 보내면 401이다
		ResponseEntity<String> headerOnly = this.members.getWithDemoHeader("/api/v1/me", Session.NONE, "MY_CARDS");
		assertBody(headerOnly, 401, UNAUTHENTICATED);
	}

	/** D5: 쿠키 없음·익명 세션·만료·로그아웃 뒤에는 401이다. 401에도 no-store가 붙는다. */
	@Test
	void unauthenticatedExpiredAndLoggedOutAreUnauthorized() throws Exception {
		ResponseEntity<String> noCookie = send(HttpMethod.GET, "/api/v1/me", Session.NONE, null);
		assertBody(noCookie, 401, UNAUTHENTICATED);
		assertNoStore(noCookie);
		assertBody(send(HttpMethod.GET, "/api/v1/me/cards", Session.NONE, null), 401, UNAUTHENTICATED);
		Session anonymous = anonymous();
		assertBody(send(HttpMethod.GET, "/api/v1/me", anonymous, null), 401, UNAUTHENTICATED);
		// 401이 CSRF 403·본문 400보다 먼저다
		ResponseEntity<String> anonymousWrite = send(HttpMethod.POST, "/api/v1/me/cards", anonymous, "{}");
		assertBody(anonymousWrite, 401, UNAUTHENTICATED);
		assertNoStore(anonymousWrite);

		Session expiring = loginNew(MEMBER_A, "a@example.com");
		this.clock.advance(Duration.ofDays(7));
		assertBody(send(HttpMethod.GET, "/api/v1/me", expiring, null), 401, UNAUTHENTICATED);

		Session loggedOut = login(anonymous(), "a@example.com");
		assertThat(send(HttpMethod.GET, "/api/v1/me", loggedOut, null).getStatusCode().value()).isEqualTo(200);
		assertThat(send(HttpMethod.POST, "/api/v1/auth/logout", loggedOut, null).getStatusCode().value())
			.isEqualTo(204);
		ResponseEntity<String> afterLogout = send(HttpMethod.GET, "/api/v1/me/cards", loggedOut, null);
		assertBody(afterLogout, 401, UNAUTHENTICATED);
		assertNoStore(afterLogout);
	}

	/** D3: 업무 요청이 미사용 만료를 7일 늘리고 쿠키를 다시 심는다(그 응답은 no-store). 절대 만료 30일은 넘지 않는다. */
	@Test
	void businessRequestsExtendIdleExpiryButNotAbsoluteExpiry() throws Exception {
		Session a = loginNew(MEMBER_A, "a@example.com");

		this.clock.advance(Duration.ofDays(6));
		ResponseEntity<String> refreshed = send(HttpMethod.GET, "/api/v1/me/cards", a, null);
		assertThat(refreshed.getStatusCode().value()).isEqualTo(200);
		assertThat(refreshed.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
			.isEqualTo("__Host-BEONE_SESSION=" + a.id() + "; Path=/; Max-Age=604800; Secure; HttpOnly; SameSite=Lax");
		assertNoStore(refreshed);

		// 처음 로그인 뒤 7일이 지났지만 6일째 사용으로 늘어나 아직 유효하다
		this.clock.advance(Duration.ofDays(6));
		assertThat(send(HttpMethod.GET, "/api/v1/me", a, null).getStatusCode().value()).isEqualTo(200);

		// 18일째·24일째에도 쓰지만 로그인 30일째에는 끝난다(24일째 연장은 30일째에서 멈춘다)
		this.clock.advance(Duration.ofDays(6));
		assertThat(send(HttpMethod.GET, "/api/v1/me", a, null).getStatusCode().value()).isEqualTo(200);
		this.clock.advance(Duration.ofDays(6));
		ResponseEntity<String> capped = send(HttpMethod.GET, "/api/v1/me", a, null);
		assertThat(capped.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("Max-Age=518400;");
		assertNoStore(capped);
		this.clock.advance(Duration.ofDays(6));
		assertBody(send(HttpMethod.GET, "/api/v1/me", a, null), 401, UNAUTHENTICATED);
	}

	/** D6·D5: 쓰기는 CSRF가 필요하고 실패하면 저장하지 않는다. CSRF 403이 본문 400보다 먼저, 본문 400이 404보다 먼저다. */
	@Test
	void writesNeedCsrfBeforeBodyValidationAndNothingIsStoredOnFailure() throws Exception {
		Session a = loginNew(MEMBER_A, "a@example.com");
		Session noToken = new Session(a.id(), null);
		Session wrongToken = new Session(a.id(), "x".repeat(43));

		ResponseEntity<String> forbidden = send(HttpMethod.POST, "/api/v1/me/cards", noToken, CARD);
		assertBody(forbidden, 403, CSRF_INVALID);
		assertNoStore(forbidden);
		assertBody(send(HttpMethod.POST, "/api/v1/me/cards", wrongToken, CARD), 403, CSRF_INVALID);
		assertBody(send(HttpMethod.POST, "/api/v1/me/cards", noToken, "{}"), 403, CSRF_INVALID);
		assertThat(activeCardCount(MEMBER_A)).isZero();

		String cardId = userCardId(send(HttpMethod.POST, "/api/v1/me/cards", a, CARD).getBody());
		assertBody(send(HttpMethod.PATCH, "/api/v1/me/cards/" + cardId, noToken, "{\"displayName\":\"바뀜\"}"), 403,
				CSRF_INVALID);
		assertBody(send(HttpMethod.DELETE, "/api/v1/me/cards/" + cardId, wrongToken, null), 403, CSRF_INVALID);
		assertThat(activeCardCount(MEMBER_A)).isOne();
		assertThat(send(HttpMethod.GET, "/api/v1/me/cards", a, null).getBody()).contains("내 첫 카드")
			.doesNotContain("바뀜");

		// 조회는 CSRF 없이 된다
		assertThat(send(HttpMethod.GET, "/api/v1/me/cards", noToken, null).getStatusCode().value()).isEqualTo(200);
		// 본문 400이 없는 카드 404보다 먼저다
		assertThat(send(HttpMethod.PATCH, "/api/v1/me/cards/uc-999999999", a, "{}").getStatusCode().value())
			.isEqualTo(400);
		assertNotFound(send(HttpMethod.PATCH, "/api/v1/me/cards/uc-999999999", a, "{\"displayName\":\"x\"}"));
	}

	/** 6번째 로그인은 가장 오래 쓰지 않은 세션을 끝낸다(D-24). 끝난 세션의 업무 요청은 401이다. */
	@Test
	void sixthConcurrentLoginEndsLeastRecentlyUsedSession() throws Exception {
		createMember(MEMBER_A, "a@example.com");
		Session first = login(anonymous(), "a@example.com");
		Session[] others = new Session[5];
		for (int i = 0; i < 5; i++) {
			this.clock.advance(Duration.ofMinutes(1));
			others[i] = login(anonymous(), "a@example.com");
		}

		assertBody(send(HttpMethod.GET, "/api/v1/me", first, null), 401, UNAUTHENTICATED);
		for (Session session : others) {
			assertThat(send(HttpMethod.GET, "/api/v1/me", session, null).getStatusCode().value()).isEqualTo(200);
		}
	}

	/**
	 * member 프로필은 인증 9개·/me·보유 카드·월간 상태·결제 설정·주문·예정 소비·추천·원장 조회·모의 결제·상품 조회·health만 연다.
	 * 나머지 업무 API는 로그인해도 404다. CODEF 4개(D9 미결)와 시연 사용자 목록(결정 37 D2·D4)은 404로 남는다. 주문 경로는 POST만 있으므로 {@code GET /api/v1/me/orders}는 인증 전에 405 {@code METHOD_NOT_ALLOWED}다(docs/api.md
	 * 공통 실패 응답).
	 */
	@Test
	void onlyAllowedRoutesAreRegistered() throws Exception {
		Session a = loginNew(MEMBER_A, "a@example.com");

		ResponseEntity<String> products = send(HttpMethod.GET, "/api/v1/card-products", a, null);
		assertThat(products.getStatusCode().value()).isEqualTo(200);
		// no-store는 /api/v1/me 아래에만 붙는다. 공개 상품 조회는 바뀌지 않는다
		assertThat(products.getHeaders().getCacheControl()).isNull();
		assertThat(send(HttpMethod.GET, "/actuator/health", a, null).getStatusCode().value()).isEqualTo(200);

		String[][] closed = { { "GET", "/api/v1/demo-users" }, { "GET", "/api/v1/me/codef/connection" },
				{ "POST", "/api/v1/me/codef/connection" }, { "DELETE", "/api/v1/me/codef/connection" },
				{ "POST", "/api/v1/me/codef/syncs" } };
		for (String[] route : closed) {
			ResponseEntity<String> response = send(HttpMethod.valueOf(route[0]), route[1], a, "{}");
			assertThat(response.getStatusCode().value()).as(route[0] + " " + route[1]).isEqualTo(404);
			if (route[1].startsWith("/api/v1/me")) {
				// 필터는 핸들러 조회 전에 돌므로 등록하지 않은 경로의 404에도 붙는다
				assertNoStore(response);
			}
			// 로그인하지 않아도 401이 아니라 404다(경로 자체가 없다)
			assertThat(send(HttpMethod.valueOf(route[0]), route[1], Session.NONE, "{}").getStatusCode()
				.value()).as("anonymous " + route[0] + " " + route[1]).isEqualTo(404);
		}

		// 목록 API가 없는 주문 경로의 GET은 핸들러가 없어 인터셉터 전에 405다. 로그인 여부와 관계없다
		for (Session session : new Session[] { a, Session.NONE }) {
			ResponseEntity<String> listOrders = send(HttpMethod.GET, "/api/v1/me/orders", session, null);
			assertBody(listOrders, 405, METHOD_NOT_ALLOWED);
			assertThat(listOrders.getHeaders().getAllow()).containsExactly(HttpMethod.POST);
			assertNoStore(listOrders);
		}
	}

	private void createMember(String memberId, String email) {
		this.members.createMember(memberId, email);
	}

	private Session loginNew(String memberId, String email) {
		return this.members.loginNew(memberId, email);
	}

	private Session login(Session anonymous, String email) {
		return this.members.login(anonymous, email);
	}

	private Session anonymous() {
		return this.members.anonymous();
	}

	private ResponseEntity<String> send(HttpMethod method, String path, Session session, String body) {
		return this.members.send(method, path, session, body);
	}

	private int activeCardCount(String memberId) {
		return this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_cards uc JOIN users u"
				+ " ON u.user_id = uc.user_id WHERE u.member_id = ? AND uc.released_at IS NULL", Integer.class,
				memberId);
	}

	private static void assertNotFound(ResponseEntity<String> response) throws Exception {
		// docs/api/cards.md: 없거나 다른 사용자의 카드는 404 USER_CARD_NOT_FOUND이고 FORBIDDEN은 쓰지 않는다
		assertBody(response, 404, USER_CARD_NOT_FOUND);
		assertNoStore(response);
	}

}
