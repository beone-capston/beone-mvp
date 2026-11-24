package com.beone.api.auth.web;

import static com.beone.api.auth.web.MemberApiTestClient.CSRF_INVALID;
import static com.beone.api.auth.web.MemberApiTestClient.UNAUTHENTICATED;
import static com.beone.api.auth.web.MemberApiTestClient.assertBody;
import static com.beone.api.auth.web.MemberApiTestClient.assertNoStore;
import static com.beone.api.auth.web.MemberApiTestClient.userCardId;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.beone.api.auth.web.MemberApiTestClient.Session;
import com.beone.api.order.application.NewOrder;
import com.beone.api.order.application.OrderService;
import com.beone.api.order.application.PaymentMethod;
import com.beone.api.recommendation.application.RecommendationService;
import com.beone.api.support.IntegrationTest;
import com.beone.api.user.application.CurrentUser;
import com.beone.api.user.application.DemoUser;
import com.beone.api.user.application.DemoUserService;
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
 * G3b: {@code member} 프로필에 등록한 주문 API 2개, 예정 소비 API 2개, 추천 API 2개를 실제 HTTP·MySQL로 확인한다. 세션 401·쓰기
 * CSRF 403·{@code Cache-Control: no-store}는 #158의 인터셉터·필터가 경로 기준으로 처리하므로, 그것이 새로 연 경로에도 실제로
 * 적용되는지 단언한다.
 *
 * <p>기대값 출처
 * <ul>
 * <li>응답 형식·오류 코드: docs/api/orders.md, docs/api/recommendations.md, docs/api/cards.md(추천 제외·매핑 변경)</li>
 * <li>401 → CSRF 403 → 본문 400 → 404, 남의 자원 404(결정 37 D5·D6), 시연 헤더 무시(D4), no-store(결정 37 구현 범위)</li>
 * <li>추천 제외 변경·매핑 변경은 {@code MAPPING_CHANGED}(결정 32 Q2), 결제 전 설정 변경은 무효화 없음(결정 32 Q3)</li>
 * <li>{@code GET /api/v1/me/orders}는 목록 API가 없어 405 {@code METHOD_NOT_ALLOWED}(docs/api.md 공통 실패 응답)</li>
 * </ul>
 * 모의 승인은 아직 member에 등록하지 않았으므로(G3c) {@code ORDER_ALREADY_PAID}·{@code ORDER_PAID}는 여기서 확인하지 않는다. 새 테스트
 * 컨텍스트를 만들지 않도록 {@link MemberProfileIntegrationTest}와 같은 프로필·가변 시계 조합을 쓴다. 시계는 서울 2026-11-18 10:00에서
 * 시작한다.
 *
 * <p>예정 소비 날짜({@code THIS_MONTH})는 클래스 로드 시점의 서울 날짜로 정하고, 주문 쪽 {@code OrderClock}은 요청 시점의 시스템 시계를
 * 쓴다. 둘 사이에 서울 기준 달이 바뀌면 {@code plannedDate} 400이 나므로 월 경계 자정 근처 실행은 피함(그때 실패하면 다시 실행한다).
 */
@IntegrationTest
@ActiveProfiles("member")
@Import(AuthSessionIntegrationTest.MutableClockConfiguration.class)
class MemberOrderRecommendationIntegrationTest {

	private static final Instant START = Instant.parse("2026-11-18T01:00:00Z");

	private static final String MEMBER_A = "usr-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

	private static final String MEMBER_B = "usr-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

	private static final String CARDS = "/api/v1/me/cards";

	private static final String ORDERS = "/api/v1/me/orders";

	private static final String PLANNED = "/api/v1/me/planned-spends";

	private static final String PREFERENCE = "/api/v1/me/payment-preference";

	private static final String MISSING_ORDER = "ord-999999999";

	private static final String MISSING_RECOMMENDATION = "rec-999999999";

	private static final String ORDER = """
			{"amountWon":100000,"orderDate":"2026-11-18","merchantName":"동네 마트",
			 "merchantClass":"ORDINARY_DOMESTIC","paymentMethod":"DIRECT"}""";

	/**
	 * 주문·예정 소비의 생성 시각과 "이번 달"은 order 패키지 자체 시계({@code OrderClock}, 시스템 시계)를 따른다. 가변 시계를 쓰지 않으므로
	 * 예정 소비 날짜는 실행일 기준 서울의 이번 달·다음 달 1일로 만든다.
	 */
	private static final LocalDate THIS_MONTH = LocalDate.now(ZoneId.of("Asia/Seoul")).withDayOfMonth(1);

	private static final String PLANNED_ITEMS = """
			{"items":[{"plannedDate":"%s","amountWon":30000,"merchantClass":"STARBUCKS_STORE",
			 "paymentMethod":"DIRECT","merchantName":"스타벅스"}]}""".formatted(THIS_MONTH);

	// 전월 실적만 보내면 매핑 확인 없이 저장한다(docs/api/monthly-states.md 409 설명)
	private static final String PERFORMANCE_STATE = """
			{"previousMonthPerformanceWon":{"value":523000,"period":{"from":"2026-10-01","to":"2026-10-31"}}}""";

	private static final String EMPTY_PLANNED = "{\"data\":{\"items\":[],\"usedInRecommendation\":false}}";

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	AuthSessionIntegrationTest.MutableClock clock;

	@Autowired
	DemoUserService demoUserService;

	@Autowired
	OrderService orderService;

	@Autowired
	RecommendationService recommendationService;

	private MemberApiTestClient members;

	/** 시연 장부에 이 테스트가 직접 만든 주문 ID(숫자). 시연 시드는 건드리지 않도록 이것만 지운다. */
	private final List<Long> demoOrderIds = new ArrayList<>();

	@BeforeEach
	void setUp() {
		this.members = new MemberApiTestClient(this.port, this.jdbcTemplate);
		clearTables();
		this.clock.set(START);
	}

	@AfterEach
	void clearTables() {
		for (Long orderId : this.demoOrderIds) {
			this.jdbcTemplate.update("DELETE FROM recommendations WHERE order_id = ?", orderId);
			this.jdbcTemplate.update("DELETE FROM orders WHERE order_id = ?", orderId);
		}
		this.demoOrderIds.clear();
		this.members.clearMemberData();
	}

	/** 주문은 그 회원의 장부에 만들어지고, 상세 조회는 마지막 추천 ID를 담는다. */
	@Test
	void orderIsCreatedAndReadInMemberLedgerOnly() {
		Session a = this.members.loginNew(MEMBER_A, "a@example.com");
		int demoOrders = countDemo("orders");

		ResponseEntity<String> created = send(HttpMethod.POST, ORDERS, a, ORDER);
		String orderId = field(created, "orderId");
		String createdAt = field(created, "createdAt");
		assertThat(createdAt).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\+09:00");
		String order = """
				"orderId":"%s","amountWon":100000,"orderDate":"2026-11-18","orderTime":null,"merchantName":"동네 마트",
				"merchantClass":"ORDINARY_DOMESTIC","paymentMethod":"DIRECT","status":"CREATED","createdAt":"%s"
				""".formatted(orderId, createdAt);
		assertBody(created, 201, "{\"data\":{" + order + "}}");
		assertNoStore(created);

		ResponseEntity<String> detail = send(HttpMethod.GET, ORDERS + "/" + orderId, a, null);
		assertBody(detail, 200,
				"{\"data\":{" + order + ",\"simulatedPaymentId\":null,\"latestRecommendationId\":null}}");
		assertNoStore(detail);

		String recommendationId = field(send(HttpMethod.POST, recommendations(orderId), a, null), "recommendationId");
		assertBody(send(HttpMethod.GET, ORDERS + "/" + orderId, a, null), 200, "{\"data\":{" + order
				+ ",\"simulatedPaymentId\":null,\"latestRecommendationId\":\"" + recommendationId + "\"}}");

		assertThat(countOf("orders", MEMBER_A)).isOne();
		assertThat(countOf("recommendations", MEMBER_A)).isOne();
		assertThat(countDemo("orders")).isEqualTo(demoOrders);
	}

	/** 회원 B는 A의 주문을 읽지도, 그 주문으로 추천을 계산하지도, A의 추천을 읽지도 못한다. 남의 것·없는 것·형식 오류의 404 본문은 같다. */
	@Test
	void ordersAndRecommendationsOfAnotherMemberAreNotFoundWithTheSameBody() {
		Session a = this.members.loginNew(MEMBER_A, "a@example.com");
		Session b = this.members.loginNew(MEMBER_B, "b@example.com");
		String orderOfA = createOrder(a);
		String recommendationOfA = field(send(HttpMethod.POST, recommendations(orderOfA), a, null),
				"recommendationId");
		String orderBefore = send(HttpMethod.GET, ORDERS + "/" + orderOfA, a, null).getBody();
		String recommendationBefore = send(HttpMethod.GET, recommendation(recommendationOfA), a, null).getBody();

		ResponseEntity<String> foreignOrder = send(HttpMethod.GET, ORDERS + "/" + orderOfA, b, null);
		for (String other : List.of(MISSING_ORDER, "abc")) {
			ResponseEntity<String> missing = send(HttpMethod.GET, ORDERS + "/" + other, b, null);
			assertOrderNotFound(missing);
			assertThat(foreignOrder.getBody()).isEqualTo(missing.getBody());
		}
		assertOrderNotFound(foreignOrder);

		ResponseEntity<String> foreignCalculation = send(HttpMethod.POST, recommendations(orderOfA), b, null);
		ResponseEntity<String> missingCalculation = send(HttpMethod.POST, recommendations(MISSING_ORDER), b, null);
		assertOrderNotFound(foreignCalculation);
		assertOrderNotFound(missingCalculation);
		assertThat(foreignCalculation.getBody()).isEqualTo(missingCalculation.getBody());

		ResponseEntity<String> foreignRecommendation = send(HttpMethod.GET, recommendation(recommendationOfA), b,
				null);
		for (String other : List.of(MISSING_RECOMMENDATION, "abc")) {
			ResponseEntity<String> missing = send(HttpMethod.GET, recommendation(other), b, null);
			assertRecommendationNotFound(missing);
			assertThat(foreignRecommendation.getBody()).isEqualTo(missing.getBody());
		}
		assertRecommendationNotFound(foreignRecommendation);

		assertThat(send(HttpMethod.GET, ORDERS + "/" + orderOfA, a, null).getBody()).isEqualTo(orderBefore);
		assertThat(send(HttpMethod.GET, recommendation(recommendationOfA), a, null).getBody())
			.isEqualTo(recommendationBefore);
		assertThat(countOf("recommendations", MEMBER_A)).isOne();
		assertThat(countOf("recommendations", MEMBER_B)).isZero();
	}

	/** 예정 소비는 회원마다 나뉜다. B의 교체가 A의 행을 지우지 않는다. */
	@Test
	void plannedSpendsAreSeparatedPerMember() {
		Session a = this.members.loginNew(MEMBER_A, "a@example.com");
		Session b = this.members.loginNew(MEMBER_B, "b@example.com");
		ResponseEntity<String> empty = send(HttpMethod.GET, PLANNED, a, null);
		assertBody(empty, 200, EMPTY_PLANNED);
		assertNoStore(empty);

		ResponseEntity<String> saved = send(HttpMethod.PUT, PLANNED, a, PLANNED_ITEMS);
		String expected = """
				{"data":{"items":[{"plannedSpendId":"%s","plannedDate":"%s","amountWon":30000,
				 "merchantClass":"STARBUCKS_STORE","paymentMethod":"DIRECT","merchantName":"스타벅스","plannedTime":null}],
				 "usedInRecommendation":false}}""".formatted(plannedSpendId(saved), THIS_MONTH);
		assertBody(saved, 200, expected);
		assertNoStore(saved);
		assertBody(send(HttpMethod.GET, PLANNED, a, null), 200, expected);
		assertBody(send(HttpMethod.GET, PLANNED, b, null), 200, EMPTY_PLANNED);

		// B가 빈 배열로 모두 지워도 A의 행은 남는다
		assertBody(send(HttpMethod.PUT, PLANNED, b, "{\"items\":[]}"), 200, EMPTY_PLANNED);
		assertBody(send(HttpMethod.GET, PLANNED, a, null), 200, expected);
		assertThat(countOf("planned_spends", MEMBER_A)).isOne();
		assertThat(countOf("planned_spends", MEMBER_B)).isZero();
		assertThat(countDemo("planned_spends")).isZero();
	}

	/** 시연 장부의 주문·추천 ID를 넣어도 404이고, 시연 헤더를 보내도 회원 장부로 처리한다(결정 37 D2·D4). */
	@Test
	void demoLedgerIsUnreachableAndDemoHeaderIsIgnored() {
		Session a = this.members.loginNew(MEMBER_A, "a@example.com");
		CurrentUser demo = this.demoUserService.currentUserOf(DemoUser.MY_CARDS);
		String demoOrder = this.orderService
			.create(demo, new NewOrder(10000, LocalDate.of(2026, 11, 18), null, "시연", "ORDINARY_DOMESTIC",
					PaymentMethod.DIRECT))
			.orderId();
		this.demoOrderIds.add(Long.parseLong(demoOrder.substring("ord-".length())));
		String demoRecommendation = this.recommendationService.calculate(demo, demoOrder).recommendationId();

		assertOrderNotFound(send(HttpMethod.GET, ORDERS + "/" + demoOrder, a, null));
		assertOrderNotFound(send(HttpMethod.POST, recommendations(demoOrder), a, null));
		assertRecommendationNotFound(send(HttpMethod.GET, recommendation(demoRecommendation), a, null));
		assertOrderNotFound(this.members.getWithDemoHeader(ORDERS + "/" + demoOrder, a, "MY_CARDS"));
		assertRecommendationNotFound(
				this.members.getWithDemoHeader(recommendation(demoRecommendation), a, "MY_CARDS"));
		assertBody(this.members.getWithDemoHeader(PLANNED, a, "MY_CARDS"), 200, EMPTY_PLANNED);

		// 시연 헤더를 붙인 쓰기도 회원 장부에 저장하고 시연 장부는 바뀌지 않는다
		int demoOrders = countDemo("orders");
		int demoRecommendations = countDemo("recommendations");
		String memberOrder = field(this.members.sendWithDemoHeader(HttpMethod.POST, ORDERS, a, ORDER, "MY_CARDS"),
				"orderId");
		assertThat(this.members.sendWithDemoHeader(HttpMethod.POST, recommendations(memberOrder), a, null, "MY_CARDS")
			.getStatusCode()
			.value()).isEqualTo(201);
		assertThat(countOf("orders", MEMBER_A)).isOne();
		assertThat(countOf("recommendations", MEMBER_A)).isOne();
		assertThat(countDemo("orders")).isEqualTo(demoOrders);
		assertThat(countDemo("recommendations")).isEqualTo(demoRecommendations);
	}

	/** 쿠키가 없거나 익명 CSRF 세션이면 6개 경로 모두 401이고, 401이 CSRF 403·본문 400보다 먼저다. */
	@Test
	void unauthenticatedRequestsAreUnauthorizedBeforeCsrfAndBodyValidation() {
		Session anonymous = this.members.anonymous();
		Object[][] routes = { { HttpMethod.POST, ORDERS, "{}" }, { HttpMethod.GET, ORDERS + "/ord-1", null },
				{ HttpMethod.GET, PLANNED, null }, { HttpMethod.PUT, PLANNED, "{}" },
				{ HttpMethod.POST, recommendations("ord-1"), null },
				{ HttpMethod.GET, recommendation("rec-1"), null } };
		for (Object[] route : routes) {
			for (Session session : new Session[] { Session.NONE, anonymous.withoutCsrf(), anonymous }) {
				ResponseEntity<String> response = send((HttpMethod) route[0], (String) route[1], session,
						(String) route[2]);
				assertBody(response, 401, UNAUTHENTICATED);
				assertNoStore(response);
			}
		}
		// 시연 헤더만으로는 인증되지 않는다
		assertBody(this.members.getWithDemoHeader(PLANNED, Session.NONE, "MY_CARDS"), 401, UNAUTHENTICATED);
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders o JOIN users u"
				+ " ON u.user_id = o.user_id WHERE u.member_id IS NOT NULL", Integer.class)).isZero();
	}

	/** 쓰기 3개(주문 생성·예정 소비 교체·추천 계산)는 CSRF가 틀리면 403이고 저장하지 않는다. 403은 본문 400·404보다 먼저다. */
	@Test
	void writesWithoutValidCsrfAreForbiddenAndStoreNothing() {
		Session a = this.members.loginNew(MEMBER_A, "a@example.com");
		String orderId = createOrder(a);
		assertThat(send(HttpMethod.PUT, PLANNED, a, PLANNED_ITEMS).getStatusCode().value()).isEqualTo(200);
		String plannedBefore = send(HttpMethod.GET, PLANNED, a, null).getBody();
		Session noToken = a.withoutCsrf();
		Session wrongToken = new Session(a.id(), "x".repeat(43));

		for (Session session : new Session[] { noToken, wrongToken }) {
			assertForbidden(send(HttpMethod.POST, ORDERS, session, ORDER));
			assertForbidden(send(HttpMethod.POST, ORDERS, session, "{}"));
			assertForbidden(send(HttpMethod.PUT, PLANNED, session, "{\"items\":[]}"));
			assertForbidden(send(HttpMethod.PUT, PLANNED, session, "{}"));
			assertForbidden(send(HttpMethod.POST, recommendations(orderId), session, null));
			assertForbidden(send(HttpMethod.POST, recommendations(MISSING_ORDER), session, null));
		}

		assertThat(countOf("orders", MEMBER_A)).isOne();
		assertThat(countOf("recommendations", MEMBER_A)).isZero();
		// 조회는 CSRF 없이 되고, 기존 예정 소비는 지워지지 않았다
		assertThat(send(HttpMethod.GET, PLANNED, noToken, null).getBody()).isEqualTo(plannedBefore);
		assertThat(countOf("planned_spends", MEMBER_A)).isOne();
		assertThat(send(HttpMethod.GET, ORDERS + "/" + orderId, noToken, null).getBody())
			.contains("\"latestRecommendationId\":null");
	}

	/** 인증·CSRF를 통과하면 본문 400이 저장보다 먼저이고, 아무것도 저장하지 않는다(결정 37 D5). */
	@Test
	void invalidBodiesAreRejectedWithoutStoring() {
		Session a = this.members.loginNew(MEMBER_A, "a@example.com");

		for (ResponseEntity<String> response : List.of(send(HttpMethod.POST, ORDERS, a, "{\"amountWon\":0}"),
				send(HttpMethod.PUT, PLANNED, a, "{}"), send(HttpMethod.PUT, PLANNED, a, """
						{"items":[{"plannedDate":"%s","amountWon":1,"merchantClass":"STARBUCKS_STORE",
						 "paymentMethod":"DIRECT"}]}""".formatted(THIS_MONTH.plusMonths(1))))) {
			assertThat(response.getStatusCode().value()).isEqualTo(400);
			assertThat(response.getBody()).contains("\"code\":\"INVALID_REQUEST\"");
			assertNoStore(response);
		}
		assertThat(countOf("orders", MEMBER_A)).isZero();
		assertThat(countOf("planned_spends", MEMBER_A)).isZero();
	}

	/**
	 * 결정 32 Q2·Q3를 member API로 확인한다. 추천 제외 변경과 매핑 변경은 그 회원의 추천을 {@code MAPPING_CHANGED}로 무효화하고, 결제 전 설정
	 * 변경은 무효화하지 않는다. 다른 회원의 추천은 그대로다.
	 */
	@Test
	void exclusionAndMappingChangesInvalidateOwnRecommendationsButPreferenceDoesNot() {
		Session a = this.members.loginNew(MEMBER_A, "a@example.com");
		Session b = this.members.loginNew(MEMBER_B, "b@example.com");
		String cardOfA = registerWithState(a, "09271");
		String secondOfA = registerWithState(a, "09174");
		String cardOfB = registerWithState(b, "09271");
		String recommendationOfB = recommend(b);

		// Q3: 결제 전 설정 변경은 기존 추천을 무효화하지 않는다
		String first = recommend(a);
		this.clock.advance(Duration.ofMinutes(1));
		assertThat(send(HttpMethod.PUT, PREFERENCE, a,
				"{\"selectionMode\":\"FIXED_REPRESENTATIVE\",\"representativeUserCardId\":\"" + secondOfA + "\"}")
			.getStatusCode()
			.value()).isEqualTo(200);
		assertValid(a, first);
		assertThat(send(HttpMethod.PUT, PREFERENCE, a,
				"{\"selectionMode\":\"AUTO_RECOMMEND\",\"representativeUserCardId\":null}")
			.getStatusCode()
			.value()).isEqualTo(200);
		assertValid(a, first);

		// Q2: 추천 제외 변경은 MAPPING_CHANGED
		this.clock.advance(Duration.ofMinutes(1));
		ResponseEntity<String> excluded = send(HttpMethod.PATCH, CARDS + "/" + cardOfA, a,
				"{\"excludedFromRecommendation\":true}");
		assertThat(excluded.getStatusCode().value()).isEqualTo(200);
		assertThat(invalidatedIds(excluded)).containsExactly(first);
		assertInvalidated(a, first, "MAPPING_CHANGED");

		// Q2: 추천 제외를 되돌리는 것도 실제 변경이므로 그 사이 계산한 추천을 MAPPING_CHANGED로 무효화한다
		String second = recommend(a);
		this.clock.advance(Duration.ofMinutes(1));
		ResponseEntity<String> included = send(HttpMethod.PATCH, CARDS + "/" + cardOfA, a,
				"{\"excludedFromRecommendation\":false}");
		assertThat(included.getStatusCode().value()).isEqualTo(200);
		assertThat(invalidatedIds(included)).containsExactly(second);
		assertInvalidated(a, second, "MAPPING_CHANGED");

		// 매핑 해제도 MAPPING_CHANGED. 무효화는 계산 때 카드 근거와 현재 상태의 비교라서, 추천 제외를 되돌린 시점에 first는 다시
		// 유효해졌고 이 해제로 함께 무효가 된다. 목록에는 이번 변경으로 쓸 수 없게 된 추천만 담기므로 B의 추천은 없다
		String third = recommend(a);
		this.clock.advance(Duration.ofMinutes(1));
		ResponseEntity<String> unmapped = send(HttpMethod.PATCH, CARDS + "/" + secondOfA + "/mapping", a,
				"{\"status\":\"UNMAPPED\"}");
		assertThat(unmapped.getStatusCode().value()).isEqualTo(200);
		assertThat(invalidatedIds(unmapped)).contains(third).doesNotContain(recommendationOfB);
		assertInvalidated(a, third, "MAPPING_CHANGED");

		// 다른 회원의 같은 상품 카드 추천은 그대로다
		assertValid(b, recommendationOfB);
		assertThat(cardOfB).isNotEqualTo(cardOfA);
	}

	private String registerWithState(Session session, String cardProductId) {
		ResponseEntity<String> created = send(HttpMethod.POST, CARDS, session,
				"{\"displayName\":\"카드\",\"cardProductId\":\"" + cardProductId + "\"}");
		assertThat(created.getStatusCode().value()).isEqualTo(201);
		String card = userCardId(created.getBody());
		assertThat(send(HttpMethod.PUT, CARDS + "/" + card + "/monthly-states/2026-11", session, PERFORMANCE_STATE)
			.getStatusCode()
			.value()).isEqualTo(200);
		return card;
	}

	private String createOrder(Session session) {
		ResponseEntity<String> created = send(HttpMethod.POST, ORDERS, session, ORDER);
		assertThat(created.getStatusCode().value()).isEqualTo(201);
		return field(created, "orderId");
	}

	private String recommend(Session session) {
		ResponseEntity<String> calculated = send(HttpMethod.POST, recommendations(createOrder(session)), session,
				null);
		assertThat(calculated.getStatusCode().value()).isEqualTo(201);
		assertNoStore(calculated);
		return field(calculated, "recommendationId");
	}

	private void assertValid(Session session, String recommendationId) {
		Map<String, Object> data = data(send(HttpMethod.GET, recommendation(recommendationId), session, null));
		assertThat(data.get("invalidated")).isEqualTo(false);
		assertThat(data.get("invalidatedReason")).isNull();
	}

	private void assertInvalidated(Session session, String recommendationId, String reason) {
		Map<String, Object> data = data(send(HttpMethod.GET, recommendation(recommendationId), session, null));
		assertThat(data.get("invalidated")).isEqualTo(true);
		assertThat(data.get("invalidatedReason")).isEqualTo(reason);
	}

	private int countOf(String table, String memberId) {
		return this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table
				+ " t JOIN users u ON u.user_id = t.user_id WHERE u.member_id = ?", Integer.class, memberId);
	}

	private int countDemo(String table) {
		return this.jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM " + table + " t JOIN users u ON u.user_id = t.user_id WHERE u.member_id IS NULL",
				Integer.class);
	}

	private ResponseEntity<String> send(HttpMethod method, String path, Session session, String body) {
		return this.members.send(method, path, session, body);
	}

	private static String recommendations(String orderId) {
		return ORDERS + "/" + orderId + "/recommendations";
	}

	private static String recommendation(String recommendationId) {
		return "/api/v1/me/recommendations/" + recommendationId;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> data(ResponseEntity<String> response) {
		assertThat(response.getStatusCode().value()).isEqualTo(200);
		return (Map<String, Object>) JsonMapper.builder().build().readValue(response.getBody(), Map.class).get("data");
	}

	private static String field(ResponseEntity<String> response, String name) {
		Object value = ((Map<?, ?>) JsonMapper.builder().build().readValue(response.getBody(), Map.class).get("data"))
			.get(name);
		assertThat(value).as(name).isInstanceOf(String.class);
		return (String) value;
	}

	@SuppressWarnings("unchecked")
	private static String plannedSpendId(ResponseEntity<String> response) {
		Map<String, Object> data = (Map<String, Object>) JsonMapper.builder()
			.build()
			.readValue(response.getBody(), Map.class)
			.get("data");
		return (String) ((List<Map<String, Object>>) data.get("items")).get(0).get("plannedSpendId");
	}

	@SuppressWarnings("unchecked")
	private static List<String> invalidatedIds(ResponseEntity<String> response) {
		Map<String, Object> data = (Map<String, Object>) JsonMapper.builder()
			.build()
			.readValue(response.getBody(), Map.class)
			.get("data");
		return (List<String>) data.get("invalidatedRecommendationIds");
	}

	private static void assertOrderNotFound(ResponseEntity<String> response) {
		assertThat(response.getStatusCode().value()).isEqualTo(404);
		assertThat(response.getBody()).contains("\"code\":\"ORDER_NOT_FOUND\"");
		assertNoStore(response);
	}

	private static void assertRecommendationNotFound(ResponseEntity<String> response) {
		assertThat(response.getStatusCode().value()).isEqualTo(404);
		assertThat(response.getBody()).contains("\"code\":\"RECOMMENDATION_NOT_FOUND\"");
		assertNoStore(response);
	}

	private static void assertForbidden(ResponseEntity<String> response) {
		assertBody(response, 403, CSRF_INVALID);
		assertNoStore(response);
	}

}
