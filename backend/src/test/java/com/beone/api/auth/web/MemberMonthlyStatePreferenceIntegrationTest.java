package com.beone.api.auth.web;

import static com.beone.api.auth.web.MemberApiTestClient.CSRF_INVALID;
import static com.beone.api.auth.web.MemberApiTestClient.UNAUTHENTICATED;
import static com.beone.api.auth.web.MemberApiTestClient.USER_CARD_NOT_FOUND;
import static com.beone.api.auth.web.MemberApiTestClient.assertBody;
import static com.beone.api.auth.web.MemberApiTestClient.assertNoStore;
import static com.beone.api.auth.web.MemberApiTestClient.userCardId;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.beone.api.auth.web.MemberApiTestClient.Session;
import com.beone.api.order.application.NewOrder;
import com.beone.api.order.application.OrderService;
import com.beone.api.order.application.PaymentMethod;
import com.beone.api.recommendation.application.InvalidationReason;
import com.beone.api.recommendation.application.RecommendationService;
import com.beone.api.recommendation.application.RecommendationView;
import com.beone.api.support.IntegrationTest;
import com.beone.api.user.application.CurrentUser;
import com.beone.api.user.application.MemberUserService;
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

/**
 * G3a: {@code member} 프로필에 등록한 월간 상태 API 3개(출처 충돌 선택 C1 포함)와 결제 전 설정 API 2개를 실제 HTTP·MySQL로 확인한다.
 * 세션 401·쓰기 CSRF 403·{@code Cache-Control: no-store}는 #158의 인터셉터·필터가 경로 기준으로 처리하므로, 그것이 새로 연 경로에도
 * 실제로 적용되는지 단언한다.
 *
 * <p>기대값 출처
 * <ul>
 * <li>응답 형식·오류 코드: docs/api/monthly-states.md, docs/api/preferences.md</li>
 * <li>401 → CSRF 403 → 본문 400 → 404, 남의 자원 404(결정 37 D5·D6), 시연 헤더 무시(D4), no-store(결정 37 구현 범위)</li>
 * <li>C1은 충돌 경로가 생기기 전까지 항상 409 {@code SOURCE_CONFLICT_NOT_FOUND}이고 저장하지 않는다(결정 32 Q1, 결정 36)</li>
 * <li>결제 전 설정 변경은 기존 추천을 무효화하지 않는다(결정 32 Q3). 월간 상태 재입력은 그 카드가 들어간 추천을
 * {@code MONTHLY_STATE_CHANGED}로 무효화한다(docs/api/recommendations.md)</li>
 * </ul>
 * 추천 API는 아직 member에 등록하지 않았으므로(G3b) 추천은 서비스로 직접 만들고 조회한다. 새 테스트 컨텍스트를 만들지 않도록
 * {@link MemberProfileIntegrationTest}와 같은 프로필·가변 시계 조합을 쓴다. 시계는 서울 2026-11-18 10:00에서 시작한다.
 */
@IntegrationTest
@ActiveProfiles("member")
@Import(AuthSessionIntegrationTest.MutableClockConfiguration.class)
class MemberMonthlyStatePreferenceIntegrationTest {

	private static final Instant START = Instant.parse("2026-11-18T01:00:00Z");

	private static final String NOW = "2026-11-18T10:00:00+09:00";

	private static final String MEMBER_A = "usr-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

	private static final String MEMBER_B = "usr-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

	private static final String CARDS = "/api/v1/me/cards";

	private static final String PREFERENCE = "/api/v1/me/payment-preference";

	private static final String MISSING_CARD = "uc-999999999";

	private static final String UNKNOWN_STATE = """
			{"value":null,"source":"UNKNOWN","period":null,"updatedAt":null,"usable":false,"reason":"NOT_ENTERED"}""";

	// 전월 실적만 보내면 매핑 확인 없이 저장한다(docs/api/monthly-states.md 409 설명)
	private static final String PERFORMANCE_STATE = """
			{"previousMonthPerformanceWon":{"value":523000,"period":{"from":"2026-10-01","to":"2026-10-31"}}}""";

	private static final String SOURCE_SELECTION = """
			{"item":"previousMonthPerformanceWon","period":{"from":"2026-10-01","to":"2026-10-31"},
			 "selectedSource":"MANUAL","conflictVersion":"cf-v3"}""";

	private static final String NO_CONFLICT = "{\"code\":\"SOURCE_CONFLICT_NOT_FOUND\",\"message\":\"출처 충돌이 없는 항목입니다.\",\"errors\":[]}";

	private static final String NOT_SET = "{\"data\":{\"selectionMode\":\"AUTO_RECOMMEND\",\"representativeUserCardId\":null,\"updatedAt\":null}}";

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	AuthSessionIntegrationTest.MutableClock clock;

	@Autowired
	MemberUserService memberUserService;

	@Autowired
	OrderService orderService;

	@Autowired
	RecommendationService recommendationService;

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
	}

	/** 입력한 적 없는 달은 404가 아니라 모든 값이 UNKNOWN이고, 수동 입력은 그 회원의 장부에만 저장된다. */
	@Test
	void monthlyStateIsReadAndReplacedInMemberLedgerOnly() {
		Session a = this.members.loginNew(MEMBER_A, "a@example.com");
		String card = register(a, "09271");
		String path = monthlyPath(card);

		ResponseEntity<String> empty = send(HttpMethod.GET, path, a, null);
		assertBody(empty, 200, """
				{"data":{"userCardId":"%s","month":"2026-11","snapshotAt":null,
				 "previousMonthPerformanceWon":{"value":null,"source":"UNKNOWN","period":null,"updatedAt":null,
				   "usable":false,"reason":"NOT_ENTERED","referencedCodef":null},
				 "limitBucketUsages":[],"otherBucketsUsedWon":%s,"selectedServices":[],
				 "serviceSelectionStatus":"UNKNOWN","noServiceSelection":null,"registeredOn":%s,
				 "conflicts":[],"afterSnapshot":null}}
				""".formatted(card, UNKNOWN_STATE, UNKNOWN_STATE));
		assertNoStore(empty);

		ResponseEntity<String> saved = send(HttpMethod.PUT, path, a, """
				{"previousMonthPerformanceWon":{"value":-12000,"period":{"from":"2026-10-01","to":"2026-10-31"}},
				 "limitBucketUsages":[{"limitBucketId":"FUEL","value":4000,"period":{"from":"2026-11-01","to":"2026-11-30"}}]}
				""");
		String manual = """
				"userCardId":"%1$s","month":"2026-11","snapshotAt":"%2$s",
				"previousMonthPerformanceWon":{"value":-12000,"source":"MANUAL",
				  "period":{"from":"2026-10-01","to":"2026-10-31"},"updatedAt":"%2$s","usable":true,"reason":null,
				  "referencedCodef":null},
				"limitBucketUsages":[
				  {"limitBucketId":"FUEL","value":4000,"source":"MANUAL","period":{"from":"2026-11-01","to":"2026-11-30"},
				   "updatedAt":"%2$s","usable":true,"reason":null}],
				"otherBucketsUsedWon":%3$s,"selectedServices":[],"serviceSelectionStatus":"UNKNOWN",
				"noServiceSelection":null,"registeredOn":%3$s,"conflicts":[]
				""".formatted(card, NOW, UNKNOWN_STATE);
		assertBody(saved, 200, "{\"data\":{" + manual + "}}");
		assertNoStore(saved);
		assertBody(send(HttpMethod.GET, path, a, null), 200, """
				{"data":{%s,"afterSnapshot":{"approvalCount":0,"approvedAmountWon":0,"cancellationCount":0}}}
				""".formatted(manual));

		assertThat(countOf("monthly_states", MEMBER_A)).isOne();
		assertThat(countDemo("monthly_states")).isZero();
	}

	/** 회원 B는 A의 카드 월간 상태를 읽지도 바꾸지도 못한다. 남의 카드와 없는 카드의 404 본문은 같다. */
	@Test
	void monthlyStateOfAnotherMemberIsNotFoundWithTheSameBodyAsMissingCard() {
		Session a = this.members.loginNew(MEMBER_A, "a@example.com");
		Session b = this.members.loginNew(MEMBER_B, "b@example.com");
		String cardOfA = register(a, "09271");
		assertThat(send(HttpMethod.PUT, monthlyPath(cardOfA), a, PERFORMANCE_STATE).getStatusCode().value())
			.isEqualTo(200);
		String before = send(HttpMethod.GET, monthlyPath(cardOfA), a, null).getBody();

		ResponseEntity<String> foreignGet = send(HttpMethod.GET, monthlyPath(cardOfA), b, null);
		ResponseEntity<String> missingGet = send(HttpMethod.GET, monthlyPath(MISSING_CARD), b, null);
		ResponseEntity<String> foreignPut = send(HttpMethod.PUT, monthlyPath(cardOfA), b,
				"{\"previousMonthPerformanceWon\":{\"value\":1,\"period\":{\"from\":\"2026-10-01\",\"to\":\"2026-10-31\"}}}");
		ResponseEntity<String> missingPut = send(HttpMethod.PUT, monthlyPath(MISSING_CARD), b,
				"{\"previousMonthPerformanceWon\":{\"value\":1,\"period\":{\"from\":\"2026-10-01\",\"to\":\"2026-10-31\"}}}");
		for (ResponseEntity<String> response : List.of(foreignGet, missingGet, foreignPut, missingPut)) {
			assertNotFound(response);
		}
		assertThat(foreignGet.getBody()).isEqualTo(missingGet.getBody());
		assertThat(foreignPut.getBody()).isEqualTo(missingPut.getBody());

		assertThat(send(HttpMethod.GET, monthlyPath(cardOfA), a, null).getBody()).isEqualTo(before);
		assertThat(countOf("monthly_states", MEMBER_B)).isZero();
	}

	/** 시연 장부의 카드 ID를 넣어도 404이고, 시연 헤더를 보내도 회원 장부로 처리한다(결정 37 D2·D4). */
	@Test
	void demoPortfolioCardsAreUnreachableAndDemoHeaderIsIgnored() {
		Session a = this.members.loginNew(MEMBER_A, "a@example.com");
		String seed = seedCardOfPortfolio();

		assertNotFound(send(HttpMethod.GET, monthlyPath(seed), a, null));
		assertNotFound(send(HttpMethod.PUT, monthlyPath(seed), a, PERFORMANCE_STATE));
		assertNotFound(send(HttpMethod.PUT, monthlyPath(seed) + "/source-selections", a, SOURCE_SELECTION));
		assertNotFound(send(HttpMethod.PUT, PREFERENCE, a,
				"{\"selectionMode\":\"FIXED_REPRESENTATIVE\",\"representativeUserCardId\":\"" + seed + "\"}"));
		assertNotFound(this.members.getWithDemoHeader(monthlyPath(seed), a, "VIRTUAL_PORTFOLIO"));

		ResponseEntity<String> preference = this.members.getWithDemoHeader(PREFERENCE, a, "VIRTUAL_PORTFOLIO");
		assertBody(preference, 200, NOT_SET);
		assertThat(countOf("monthly_states", MEMBER_A)).isZero();
		assertThat(countOf("payment_preferences", MEMBER_A)).isZero();
		assertThat(countDemo("monthly_states")).isZero();
		assertThat(countDemo("payment_preferences")).isZero();
	}

	/** 쿠키가 없거나 익명 CSRF 세션이면 5개 경로 모두 401이고, 401이 CSRF 403·본문 400보다 먼저다. */
	@Test
	void unauthenticatedRequestsAreUnauthorizedBeforeCsrfAndBodyValidation() {
		Session anonymous = this.members.anonymous();
		String path = monthlyPath("uc-1");
		Object[][] routes = { { HttpMethod.GET, path, null }, { HttpMethod.PUT, path, "{\"registeredOn\":1}" },
				{ HttpMethod.PUT, path + "/source-selections", "{}" }, { HttpMethod.GET, PREFERENCE, null },
				{ HttpMethod.PUT, PREFERENCE, "{}" } };
		for (Object[] route : routes) {
			for (Session session : new Session[] { Session.NONE, anonymous.withoutCsrf(), anonymous }) {
				ResponseEntity<String> response = send((HttpMethod) route[0], (String) route[1], session,
						(String) route[2]);
				assertBody(response, 401, UNAUTHENTICATED);
				assertNoStore(response);
			}
		}
		// 시연 헤더만으로는 인증되지 않는다
		assertBody(this.members.getWithDemoHeader(PREFERENCE, Session.NONE, "MY_CARDS"), 401, UNAUTHENTICATED);
	}

	/** 쓰기 3개는 CSRF가 틀리면 403이고 저장하지 않는다. 403은 본문 400과 C1의 409보다 먼저다. */
	@Test
	void writesWithoutValidCsrfAreForbiddenAndStoreNothing() {
		Session a = this.members.loginNew(MEMBER_A, "a@example.com");
		String card = register(a, "09174");
		Session noToken = a.withoutCsrf();
		Session wrongToken = new Session(a.id(), "x".repeat(43));
		String preference = "{\"selectionMode\":\"FIXED_REPRESENTATIVE\",\"representativeUserCardId\":\"" + card + "\"}";

		for (Session session : new Session[] { noToken, wrongToken }) {
			assertForbidden(send(HttpMethod.PUT, monthlyPath(card), session, PERFORMANCE_STATE));
			assertForbidden(send(HttpMethod.PUT, monthlyPath(card), session, "{\"registeredOn\":1}"));
			assertForbidden(send(HttpMethod.PUT, monthlyPath(card) + "/source-selections", session, SOURCE_SELECTION));
			assertForbidden(send(HttpMethod.PUT, PREFERENCE, session, preference));
			assertForbidden(send(HttpMethod.PUT, PREFERENCE, session, "{}"));
		}

		assertThat(countOf("monthly_states", MEMBER_A)).isZero();
		assertThat(countOf("payment_preferences", MEMBER_A)).isZero();
		// 조회는 CSRF 없이 된다
		assertThat(send(HttpMethod.GET, monthlyPath(card), noToken, null).getBody()).contains("\"snapshotAt\":null");
		assertBody(send(HttpMethod.GET, PREFERENCE, noToken, null), 200, NOT_SET);
	}

	/** 인증·CSRF를 통과하면 본문·경로 400이 없는 카드 404보다 먼저다(결정 37 D5). */
	@Test
	void validationErrorsComeBeforeNotFound() {
		Session a = this.members.loginNew(MEMBER_A, "a@example.com");

		for (ResponseEntity<String> response : List.of(
				send(HttpMethod.GET, CARDS + "/" + MISSING_CARD + "/monthly-states/2026-13", a, null),
				send(HttpMethod.PUT, monthlyPath(MISSING_CARD), a,
						"{\"previousMonthPerformanceWon\":{\"value\":\"1\",\"period\":{\"from\":\"2026-10-01\",\"to\":\"2026-10-31\"}}}"),
				send(HttpMethod.PUT, monthlyPath(MISSING_CARD) + "/source-selections", a, "{}"),
				send(HttpMethod.PUT, PREFERENCE, a, "{\"selectionMode\":\"AUTO_RECOMMEND\"}"))) {
			assertThat(response.getStatusCode().value()).isEqualTo(400);
			assertThat(response.getBody()).contains("\"code\":\"INVALID_REQUEST\"");
			assertNoStore(response);
		}
		assertNotFound(send(HttpMethod.PUT, monthlyPath(MISSING_CARD), a, PERFORMANCE_STATE));
		assertNotFound(send(HttpMethod.PUT, monthlyPath(MISSING_CARD) + "/source-selections", a, SOURCE_SELECTION));
		assertNotFound(send(HttpMethod.PUT, PREFERENCE, a,
				"{\"selectionMode\":\"AUTO_RECOMMEND\",\"representativeUserCardId\":\"" + MISSING_CARD + "\"}"));
	}

	/** C1: member에서도 충돌이 없어 항상 409 SOURCE_CONFLICT_NOT_FOUND이고 아무것도 저장하지 않는다(결정 32 Q1, 결정 36). */
	@Test
	void sourceSelectionStaysConflictNotFoundAndStoresNothing() {
		Session a = this.members.loginNew(MEMBER_A, "a@example.com");
		Session b = this.members.loginNew(MEMBER_B, "b@example.com");
		String card = register(a, "09271");
		String selections = monthlyPath(card) + "/source-selections";

		// 입력이 없는 달도, 수동 값이 있는 달도 같다
		ResponseEntity<String> withoutState = send(HttpMethod.PUT, selections, a, SOURCE_SELECTION);
		assertBody(withoutState, 409, NO_CONFLICT);
		assertNoStore(withoutState);
		assertThat(countOf("monthly_states", MEMBER_A)).isZero();

		assertThat(send(HttpMethod.PUT, monthlyPath(card), a, PERFORMANCE_STATE).getStatusCode().value())
			.isEqualTo(200);
		String before = send(HttpMethod.GET, monthlyPath(card), a, null).getBody();
		this.clock.advance(Duration.ofMinutes(1));
		assertBody(send(HttpMethod.PUT, selections, a, SOURCE_SELECTION), 409, NO_CONFLICT);
		assertThat(send(HttpMethod.GET, monthlyPath(card), a, null).getBody()).isEqualTo(before);
		assertThat(countOf("monthly_states", MEMBER_A)).isOne();

		// 남의 카드는 409가 아니라 404이고 없는 카드와 본문이 같다
		ResponseEntity<String> foreign = send(HttpMethod.PUT, selections, b, SOURCE_SELECTION);
		ResponseEntity<String> missing = send(HttpMethod.PUT, monthlyPath(MISSING_CARD) + "/source-selections", b,
				SOURCE_SELECTION);
		assertNotFound(foreign);
		assertNotFound(missing);
		assertThat(foreign.getBody()).isEqualTo(missing.getBody());
	}

	/** 새 회원은 설정 행 없이 미설정 표시이고, 회원마다 설정이 나뉜다. 남의 카드를 대표 카드로 고르면 404다. */
	@Test
	void paymentPreferenceIsSeparatedPerMember() {
		Session a = this.members.loginNew(MEMBER_A, "a@example.com");
		Session b = this.members.loginNew(MEMBER_B, "b@example.com");
		ResponseEntity<String> initial = send(HttpMethod.GET, PREFERENCE, a, null);
		assertBody(initial, 200, NOT_SET);
		assertNoStore(initial);
		assertThat(countOf("payment_preferences", MEMBER_A)).isZero();

		String cardOfA = register(a, "09174");
		String fixed = "{\"data\":{\"selectionMode\":\"FIXED_REPRESENTATIVE\",\"representativeUserCardId\":\"%s\",\"updatedAt\":\"%s\"}}"
			.formatted(cardOfA, NOW);
		ResponseEntity<String> saved = send(HttpMethod.PUT, PREFERENCE, a,
				"{\"selectionMode\":\"FIXED_REPRESENTATIVE\",\"representativeUserCardId\":\"" + cardOfA + "\"}");
		assertBody(saved, 200, fixed);
		assertNoStore(saved);
		assertBody(send(HttpMethod.GET, PREFERENCE, a, null), 200, fixed);
		assertBody(send(HttpMethod.GET, PREFERENCE, b, null), 200, NOT_SET);

		ResponseEntity<String> foreign = send(HttpMethod.PUT, PREFERENCE, b,
				"{\"selectionMode\":\"AUTO_RECOMMEND\",\"representativeUserCardId\":\"" + cardOfA + "\"}");
		ResponseEntity<String> missing = send(HttpMethod.PUT, PREFERENCE, b,
				"{\"selectionMode\":\"AUTO_RECOMMEND\",\"representativeUserCardId\":\"" + MISSING_CARD + "\"}");
		assertNotFound(foreign);
		assertNotFound(missing);
		assertThat(foreign.getBody()).isEqualTo(missing.getBody());

		assertBody(send(HttpMethod.GET, PREFERENCE, a, null), 200, fixed);
		assertBody(send(HttpMethod.GET, PREFERENCE, b, null), 200, NOT_SET);
		assertThat(countOf("payment_preferences", MEMBER_A)).isOne();
		assertThat(countOf("payment_preferences", MEMBER_B)).isZero();
	}

	/**
	 * 결정 32 Q3: 결제 전 설정 변경은 기존 추천을 무효화하지 않는다. 월간 상태 재입력은 그 회원의 추천만 {@code MONTHLY_STATE_CHANGED}로
	 * 무효화하고 다른 회원의 추천은 그대로다.
	 */
	@Test
	void preferenceChangeKeepsRecommendationsWhileMonthlyStateInvalidatesOnlyOwn() {
		Session a = this.members.loginNew(MEMBER_A, "a@example.com");
		Session b = this.members.loginNew(MEMBER_B, "b@example.com");
		String cardOfA = register(a, "09271");
		String cardOfB = register(b, "09271");
		assertThat(send(HttpMethod.PUT, monthlyPath(cardOfA), a, PERFORMANCE_STATE).getStatusCode().value())
			.isEqualTo(200);
		assertThat(send(HttpMethod.PUT, monthlyPath(cardOfB), b, PERFORMANCE_STATE).getStatusCode().value())
			.isEqualTo(200);
		CurrentUser userA = currentUser(MEMBER_A);
		CurrentUser userB = currentUser(MEMBER_B);
		String recommendationOfA = recommend(userA);
		String recommendationOfB = recommend(userB);

		this.clock.advance(Duration.ofMinutes(1));
		assertThat(send(HttpMethod.PUT, PREFERENCE, a,
				"{\"selectionMode\":\"FIXED_REPRESENTATIVE\",\"representativeUserCardId\":\"" + cardOfA + "\"}")
			.getStatusCode()
			.value()).isEqualTo(200);
		assertValid(userA, recommendationOfA);

		this.clock.advance(Duration.ofMinutes(1));
		assertThat(send(HttpMethod.PUT, monthlyPath(cardOfA), a, PERFORMANCE_STATE).getStatusCode().value())
			.isEqualTo(200);
		RecommendationView invalidated = this.recommendationService.get(userA, recommendationOfA);
		assertThat(invalidated.invalidated()).isTrue();
		assertThat(invalidated.invalidatedReason()).isEqualTo(InvalidationReason.MONTHLY_STATE_CHANGED);
		assertValid(userB, recommendationOfB);
	}

	private String register(Session session, String cardProductId) {
		ResponseEntity<String> created = send(HttpMethod.POST, CARDS, session,
				"{\"displayName\":\"카드\",\"cardProductId\":\"" + cardProductId + "\"}");
		assertThat(created.getStatusCode().value()).isEqualTo(201);
		return userCardId(created.getBody());
	}

	private CurrentUser currentUser(String memberId) {
		return this.memberUserService.currentUserOf(memberId).orElseThrow();
	}

	private String recommend(CurrentUser user) {
		String orderId = this.orderService
			.create(user, new NewOrder(100000, LocalDate.of(2026, 11, 18), null, "동네 마트", "ORDINARY_DOMESTIC",
					PaymentMethod.DIRECT))
			.orderId();
		return this.recommendationService.calculate(user, orderId).recommendationId();
	}

	private void assertValid(CurrentUser user, String recommendationId) {
		RecommendationView view = this.recommendationService.get(user, recommendationId);
		assertThat(view.invalidated()).isFalse();
		assertThat(view.invalidatedReason()).isNull();
	}

	private String seedCardOfPortfolio() {
		Long id = this.jdbcTemplate.queryForObject("SELECT MIN(c.user_card_id) FROM user_cards c "
				+ "JOIN users u ON u.user_id = c.user_id WHERE u.demo_user = 'VIRTUAL_PORTFOLIO'", Long.class);
		return "uc-" + id;
	}

	/** 회원 장부 범위로 행 수를 센다. 다른 테스트가 남긴 행에 영향을 받지 않는다. */
	private int countOf(String table, String memberId) {
		return this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table
				+ " t JOIN users u ON u.user_id = t.user_id WHERE u.member_id = ?", Integer.class, memberId);
	}

	/** 시연 장부(member_id 없음)의 행 수를 센다. */
	private int countDemo(String table) {
		return this.jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM " + table + " t JOIN users u ON u.user_id = t.user_id WHERE u.member_id IS NULL",
				Integer.class);
	}

	private ResponseEntity<String> send(HttpMethod method, String path, Session session, String body) {
		return this.members.send(method, path, session, body);
	}

	private static String monthlyPath(String userCardId) {
		return CARDS + "/" + userCardId + "/monthly-states/2026-11";
	}

	private static void assertNotFound(ResponseEntity<String> response) {
		assertBody(response, 404, USER_CARD_NOT_FOUND);
		assertNoStore(response);
	}

	private static void assertForbidden(ResponseEntity<String> response) {
		assertBody(response, 403, CSRF_INVALID);
		assertNoStore(response);
	}

}
