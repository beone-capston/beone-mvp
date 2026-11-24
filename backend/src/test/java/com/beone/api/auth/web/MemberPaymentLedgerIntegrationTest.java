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
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.beone.api.auth.web.MemberApiTestClient.Session;
import com.beone.api.card.application.NewUserCard;
import com.beone.api.card.application.UserCardService;
import com.beone.api.monthlystate.application.DatePeriod;
import com.beone.api.monthlystate.application.MonthlyStateInput;
import com.beone.api.monthlystate.application.MonthlyStateService;
import com.beone.api.order.application.NewOrder;
import com.beone.api.order.application.OrderService;
import com.beone.api.order.application.PaymentMethod;
import com.beone.api.payment.application.ApprovalCommand;
import com.beone.api.payment.application.SimulatedPaymentService;
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
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;
import tools.jackson.databind.json.JsonMapper;

/**
 * G3c: {@code member} 프로필에 등록한 모의 결제 API 3개(승인·상세·취소)와 원장 조회 API 1개를 실제 HTTP·MySQL로 확인한다. 세션 401·쓰기
 * CSRF 403·{@code Cache-Control: no-store}는 #158의 인터셉터·필터가 경로 기준으로 처리하므로, 그것이 새로 연 경로에도 실제로
 * 적용되는지 단언한다.
 *
 * <p>기대값 출처
 * <ul>
 * <li>응답 형식·오류 코드: docs/api/mock-payments.md, docs/api/ledger.md, docs/api/recommendations.md({@code ORDER_PAID})</li>
 * <li>멱등: docs/api.md 중복 방지(사용자별 키, 지문 = 사용자·메서드·경로·본문, 24시간 보존, 같은 키·다른 지문 409)</li>
 * <li>{@code simulateFailure}는 {@code local}·{@code demo}에서만 받고 그 밖에는 400 {@code INVALID_REQUEST}(mock-payments.md 요청
 * 본문·실패 목록, D-23-4). member의 400은 사용자 확인(N-G3-2)을 기다리는 문언 기준 구현이다</li>
 * <li>무효가 된 추천으로도 승인하고 차이를 담는다(D-23-3). 비교 대상은 모드와 무관한 {@code selected}다(recommendations.md
 * {@code selected}, 결정 32 Q4). 그래서 대표 카드 고정에서 대표 카드만 바꾸면 차이로 잡히지 않는다(공개 한계, N-G3-4)</li>
 * <li>401 → CSRF 403 → 본문 400 → 404, 남의 자원 404(결정 37 D5·D6), 시연 헤더 무시(D4), no-store(결정 37 구현 범위)</li>
 * </ul>
 * 다음 기대값은 계약 문언이 아니라 현재 코드 동작을 고정한 것이다(G3 명세 패키지 5-3절). 같은 키 재생의 응답 상태 201, 24시간이 지난
 * 같은 키 부분 취소의 재실행.
 *
 * <p>주문·추천 값은 평가 자료 C01(09271, 일반 국내 100,000원 직접 결제, 전월 실적 0원 → 기본 적립 1,000원)을 2026-11로 옮겼다. 새 테스트
 * 컨텍스트를 만들지 않도록 {@link MemberProfileIntegrationTest}와 같은 프로필·가변 시계 조합을 쓴다. 시계는 서울 2026-11-18 10:00에서
 * 시작한다.
 */
@IntegrationTest
@ActiveProfiles("member")
@Import(AuthSessionIntegrationTest.MutableClockConfiguration.class)
class MemberPaymentLedgerIntegrationTest {

	private static final Instant START = Instant.parse("2026-11-18T01:00:00Z");

	private static final String MEMBER_A = "usr-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

	private static final String MEMBER_B = "usr-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

	private static final String EMAIL_A = "a@example.com";

	private static final String CARDS = "/api/v1/me/cards";

	private static final String ORDERS = "/api/v1/me/orders";

	private static final String PREFERENCE = "/api/v1/me/payment-preference";

	private static final String LEDGER = "/api/v1/me/ledger-transactions?month=2026-11";

	private static final String MISSING_ORDER = "ord-999999999";

	private static final String MISSING_PAYMENT = "pay-999999999";

	private static final String INVALID_REQUEST = "{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 값을 확인해 주세요.\",\"errors\":[]}";

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

	@Autowired
	DemoUserService demoUserService;

	@Autowired
	UserCardService userCardService;

	@Autowired
	MonthlyStateService monthlyStateService;

	@Autowired
	OrderService orderService;

	@Autowired
	SimulatedPaymentService simulatedPaymentService;

	private MemberApiTestClient members;

	/** 시연 장부에 이 테스트가 직접 만든 보유 카드·주문 ID(숫자). 시연 시드는 건드리지 않도록 이것만 지운다. */
	private Long demoCardId;

	private Long demoOrderId;

	@BeforeEach
	void setUp() {
		this.members = new MemberApiTestClient(this.port, this.jdbcTemplate);
		clearTables();
		this.clock.set(START);
	}

	@AfterEach
	void clearTables() {
		if (this.demoOrderId != null) {
			// 모의 결제·원장 행은 주문·카드 삭제의 외래 키 연쇄 삭제로 함께 지워진다
			this.jdbcTemplate.update("DELETE FROM idempotency_keys WHERE idempotency_key = 'demo-g3c'");
			this.jdbcTemplate.update("DELETE FROM recommendations WHERE order_id = ?", this.demoOrderId);
			this.jdbcTemplate.update("DELETE FROM orders WHERE order_id = ?", this.demoOrderId);
			this.demoOrderId = null;
		}
		if (this.demoCardId != null) {
			this.jdbcTemplate.update("DELETE FROM monthly_states WHERE user_card_id = ?", this.demoCardId);
			this.jdbcTemplate.update("DELETE FROM user_cards WHERE user_card_id = ?", this.demoCardId);
			this.demoCardId = null;
		}
		this.members.clearMemberData();
	}

	/**
	 * 승인은 회원 장부에 한 번만 기록된다. 같은 키·같은 본문은 같은 결과(201), 같은 키·다른 본문은 409 {@code IDEMPOTENCY_KEY_REUSED},
	 * 다른 키는 409 {@code ORDER_ALREADY_PAID}다. 승인 뒤 추천 재계산은 409이고 본 추천은 {@code ORDER_PAID}로 무효 표시된다.
	 */
	@Test
	void approvalIsRecordedOnceAndReplayedWithTheSameKey() {
		Session a = this.members.loginNew(MEMBER_A, EMAIL_A);
		String card = registerWithC01State(a);
		String order = createOrder(a);
		String seen = recommend(a, order);
		this.clock.advance(Duration.ofMinutes(1));
		String body = seenBody(seen);

		ResponseEntity<String> approved = approve(a, order, "key-1", body);

		assertThat(approved.getStatusCode().value()).isEqualTo(201);
		assertNoStore(approved);
		Map<String, Object> data = data(approved);
		String payment = (String) data.get("simulatedPaymentId");
		String ledger = (String) data.get("ledgerTransactionId");
		assertThat(payment).matches("pay-[1-9][0-9]*");
		assertThat(ledger).matches("lt-[1-9][0-9]*");
		assertThat(data).containsEntry("orderId", order)
			.containsEntry("status", "APPROVED")
			.containsEntry("approvedAt", "2026-11-18T10:01:00+09:00")
			.containsEntry("amountWon", 100000)
			.containsEntry("selectionMode", "AUTO_RECOMMEND")
			.containsEntry("userCardId", card)
			.containsEntry("cardProductId", "09271")
			.containsEntry("currentOrderBenefit", Map.of("amountWon", 1000, "status", "CONFIRMED"))
			.containsEntry("cancellableAmountWon", 100000);
		assertThat(map(data.get("recalculation"))).containsEntry("seenRecommendationId", seen)
			.containsEntry("changed", false)
			.containsEntry("differences", List.of());

		// 원장: 회원 장부에 자체 모의 승인 행 하나
		ResponseEntity<String> ledgerResponse = send(HttpMethod.GET, LEDGER, a, null);
		assertNoStore(ledgerResponse);
		List<Map<String, Object>> items = items(ledgerResponse);
		assertThat(items).singleElement()
			.satisfies(item -> assertThat(item).containsEntry("ledgerTransactionId", ledger)
				.containsEntry("userCardId", card)
				.containsEntry("source", "SIMULATION")
				.containsEntry("entryType", "APPROVAL")
				.containsEntry("amountWon", 100000)
				.containsEntry("transactionDate", "2026-11-18")
				.containsEntry("simulatedPaymentId", payment));

		// 같은 키·같은 본문 → 같은 결과. 결제·원장·키는 늘지 않는다
		ResponseEntity<String> replay = approve(a, order, "key-1", body);
		assertThat(replay.getStatusCode().value()).isEqualTo(201);
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch(approved.getBody(), replay.getBody());
		assertThat(countOf("simulated_payments", MEMBER_A)).isOne();
		assertThat(countOf("ledger_transactions", MEMBER_A)).isOne();
		assertThat(countOf("idempotency_keys", MEMBER_A)).isOne();

		// 같은 키·다른 본문 → 409, 다른 키 → 이미 결제된 주문 409
		assertError(approve(a, order, "key-1", "{}"), 409, "IDEMPOTENCY_KEY_REUSED");
		assertError(approve(a, order, "key-2", body), 409, "ORDER_ALREADY_PAID");
		assertError(send(HttpMethod.POST, ORDERS + "/" + order + "/recommendations", a, null), 409,
				"ORDER_ALREADY_PAID");
		Map<String, Object> seenNow = data(send(HttpMethod.GET, "/api/v1/me/recommendations/" + seen, a, null));
		assertThat(seenNow).containsEntry("invalidated", true).containsEntry("invalidatedReason", "ORDER_PAID");
		assertThat(data(send(HttpMethod.GET, ORDERS + "/" + order, a, null))).containsEntry("status", "PAID")
			.containsEntry("simulatedPaymentId", payment);

		ResponseEntity<String> detail = send(HttpMethod.GET, "/api/v1/me/simulated-payments/" + payment, a, null);
		assertNoStore(detail);
		assertThat(data(detail)).containsEntry("status", "APPROVED")
			.containsEntry("cancellableAmountWon", 100000)
			.containsEntry("cancellations", List.of());
		assertThat(countOf("simulated_payments", MEMBER_A)).isOne();
	}

	/**
	 * 회원 A·B가 같은 키를 써도 서로 독립이다. B는 A의 주문·결제·추천·카드에 닿지 못하고, 남의 것·없는 것·형식 오류의 404 본문은 같다.
	 */
	@Test
	void membersAreSeparatedAndKeysAreScopedPerMember() {
		Session a = this.members.loginNew(MEMBER_A, EMAIL_A);
		Session b = this.members.loginNew(MEMBER_B, "b@example.com");
		String cardOfA = registerWithC01State(a);
		String cardOfB = registerWithC01State(b);
		String orderOfA = createOrder(a);
		String seenOfA = recommend(a, orderOfA);
		String orderOfB = createOrder(b);

		ResponseEntity<String> approvedA = approve(a, orderOfA, "shared-key", null);
		ResponseEntity<String> approvedB = approve(b, orderOfB, "shared-key", null);
		assertThat(approvedA.getStatusCode().value()).isEqualTo(201);
		assertThat(approvedB.getStatusCode().value()).isEqualTo(201);
		String paymentOfA = (String) data(approvedA).get("simulatedPaymentId");
		assertThat(data(approvedB).get("simulatedPaymentId")).isNotEqualTo(paymentOfA);
		assertThat(data(approvedB)).containsEntry("userCardId", cardOfB);

		// 원장은 회원마다 나뉜다
		assertThat(items(send(HttpMethod.GET, LEDGER, a, null))).singleElement()
			.satisfies(item -> assertThat(item).containsEntry("userCardId", cardOfA));
		assertThat(items(send(HttpMethod.GET, LEDGER, b, null))).singleElement()
			.satisfies(item -> assertThat(item).containsEntry("userCardId", cardOfB));
		String paymentBefore = send(HttpMethod.GET, payment(paymentOfA), a, null).getBody();

		// 결제 상세·취소: 남의 것 = 없는 것 = 형식 오류
		ResponseEntity<String> foreignDetail = send(HttpMethod.GET, payment(paymentOfA), b, null);
		ResponseEntity<String> foreignCancel = cancel(b, paymentOfA, "k-b", "{\"amountWon\":1}");
		for (String other : List.of(MISSING_PAYMENT, "abc")) {
			ResponseEntity<String> missingDetail = send(HttpMethod.GET, payment(other), b, null);
			ResponseEntity<String> missingCancel = cancel(b, other, "k-b", "{\"amountWon\":1}");
			assertNotFound(missingDetail, "SIMULATED_PAYMENT_NOT_FOUND");
			assertNotFound(missingCancel, "SIMULATED_PAYMENT_NOT_FOUND");
			assertThat(foreignDetail.getBody()).isEqualTo(missingDetail.getBody());
			assertThat(foreignCancel.getBody()).isEqualTo(missingCancel.getBody());
		}
		assertNotFound(foreignDetail, "SIMULATED_PAYMENT_NOT_FOUND");
		assertNotFound(foreignCancel, "SIMULATED_PAYMENT_NOT_FOUND");

		// 승인: 남의 주문 = 없는 주문, 남의 seenRecommendationId = 없는 추천
		ResponseEntity<String> foreignOrder = approve(b, orderOfA, "k-b", null);
		ResponseEntity<String> missingOrder = approve(b, MISSING_ORDER, "k-b", null);
		assertNotFound(foreignOrder, "ORDER_NOT_FOUND");
		assertNotFound(missingOrder, "ORDER_NOT_FOUND");
		assertThat(foreignOrder.getBody()).isEqualTo(missingOrder.getBody());
		String secondOrderOfB = createOrder(b);
		ResponseEntity<String> foreignSeen = approve(b, secondOrderOfB, "k-b", seenBody(seenOfA));
		ResponseEntity<String> missingSeen = approve(b, secondOrderOfB, "k-b", seenBody("rec-999999999"));
		assertNotFound(foreignSeen, "RECOMMENDATION_NOT_FOUND");
		assertNotFound(missingSeen, "RECOMMENDATION_NOT_FOUND");
		assertThat(foreignSeen.getBody()).isEqualTo(missingSeen.getBody());

		// 원장 카드 필터: 남의 카드 = 없는 카드
		ResponseEntity<String> foreignCard = send(HttpMethod.GET, LEDGER + "&userCardId=" + cardOfA, b, null);
		ResponseEntity<String> missingCard = send(HttpMethod.GET, LEDGER + "&userCardId=uc-999999999", b, null);
		assertBody(foreignCard, 404, USER_CARD_NOT_FOUND);
		assertBody(missingCard, 404, USER_CARD_NOT_FOUND);
		assertNoStore(foreignCard);

		// A의 결제는 그대로이고 B의 실패 요청은 아무것도 저장하지 않았다
		assertThat(send(HttpMethod.GET, payment(paymentOfA), a, null).getBody()).isEqualTo(paymentBefore);
		assertThat(countOf("simulated_payments", MEMBER_B)).isOne();
		assertThat(countOf("payment_cancellations", MEMBER_B)).isZero();
		assertThat(countOf("idempotency_keys", MEMBER_A)).isOne();
		assertThat(countOf("idempotency_keys", MEMBER_B)).isOne();
		assertThat(data(send(HttpMethod.GET, ORDERS + "/" + secondOrderOfB, b, null))).containsEntry("status",
				"CREATED");
	}

	/**
	 * 취소의 멱등: 같은 키·같은 금액은 재생, 같은 키·다른 금액은 409다. 키는 24시간 보존하고, 지나면 같은 키를 새 요청으로 받는다. 승인은 주문이
	 * 이미 PAID라 409로 이중 결제가 없고, 부분 취소는 남은 금액이 있으면 다시 실행된다(현재 동작 고정).
	 */
	@Test
	void idempotencyKeysExpireAfterTwentyFourHours() {
		Session a = this.members.loginNew(MEMBER_A, EMAIL_A);
		registerWithC01State(a);
		String order = createOrder(a);
		ResponseEntity<String> approved = approve(a, order, "key-a", null);
		String payment = (String) data(approved).get("simulatedPaymentId");
		this.clock.advance(Duration.ofMinutes(1));

		ResponseEntity<String> partial = cancel(a, payment, "key-c", "{\"amountWon\":40000}");
		assertThat(partial.getStatusCode().value()).isEqualTo(201);
		assertNoStore(partial);
		assertThat(data(partial)).containsEntry("simulatedPaymentId", payment)
			.containsEntry("amountWon", 40000)
			.containsEntry("cancelledAt", "2026-11-18T10:01:00+09:00")
			.containsEntry("paymentStatus", "PARTIALLY_CANCELLED")
			.containsEntry("cancellableAmountWon", 60000);
		assertError(cancel(a, payment, "key-c", "{\"amountWon\":30000}"), 409, "IDEMPOTENCY_KEY_REUSED");

		// 24시간 안: 같은 키·같은 요청은 재생
		this.clock.advance(Duration.ofHours(23).plusMinutes(58));
		JsonAssert.comparator(JsonCompareMode.STRICT)
			.assertIsMatch(partial.getBody(), cancel(a, payment, "key-c", "{\"amountWon\":40000}").getBody());
		JsonAssert.comparator(JsonCompareMode.STRICT)
			.assertIsMatch(approved.getBody(), approve(a, order, "key-a", null).getBody());
		assertThat(countOf("payment_cancellations", MEMBER_A)).isOne();

		// 24시간 뒤: 승인은 주문이 PAID라 409, 부분 취소는 새 요청으로 다시 실행된다
		this.clock.advance(Duration.ofMinutes(2));
		assertError(approve(a, order, "key-a", null), 409, "ORDER_ALREADY_PAID");
		ResponseEntity<String> again = cancel(a, payment, "key-c", "{\"amountWon\":40000}");
		assertThat(again.getStatusCode().value()).isEqualTo(201);
		assertThat(data(again)).containsEntry("paymentStatus", "PARTIALLY_CANCELLED")
			.containsEntry("cancellableAmountWon", 20000);
		assertThat(data(again).get("cancellationId")).isNotEqualTo(data(partial).get("cancellationId"));

		// 남은 금액 전액 취소 → 결제 CANCELLED, 주문 CANCELLED, 더 취소하면 409
		ResponseEntity<String> rest = cancel(a, payment, "key-r", "{\"amountWon\":20000}");
		assertThat(data(rest)).containsEntry("paymentStatus", "CANCELLED").containsEntry("cancellableAmountWon", 0);
		assertThat(data(send(HttpMethod.GET, ORDERS + "/" + order, a, null))).containsEntry("status", "CANCELLED");
		assertError(cancel(a, payment, "key-x", "{\"amountWon\":1}"), 409, "PAYMENT_ALREADY_CANCELLED");

		// 원장: 승인 1행 + 취소 3행(음수, 원 승인 연결)
		assertThat(items(send(HttpMethod.GET, LEDGER, a, null)))
			.extracting(item -> item.get("entryType") + ":" + item.get("amountWon"))
			.containsExactly("APPROVAL:100000", "CANCELLATION:-40000", "CANCELLATION:-40000", "CANCELLATION:-20000");
	}

	/**
	 * member에서 {@code simulateFailure: true}는 400 {@code INVALID_REQUEST}이고 결제·멱등 키를 저장하지 않는다. 400은 주문 404보다
	 * 먼저다. {@code false}는 정상 처리하고, 거절된 키는 소모되지 않는다(mock-payments.md, N-G3-2 확인 전 문언 기준).
	 */
	@Test
	void simulateFailureIsRejectedInMemberProfile() {
		Session a = this.members.loginNew(MEMBER_A, EMAIL_A);
		registerWithC01State(a);
		String order = createOrder(a);

		for (String target : List.of(order, MISSING_ORDER)) {
			ResponseEntity<String> rejected = approve(a, target, "key-f", "{\"simulateFailure\":true}");
			assertBody(rejected, 400, INVALID_REQUEST);
			assertNoStore(rejected);
		}
		assertThat(countOf("simulated_payments", MEMBER_A)).isZero();
		assertThat(countOf("idempotency_keys", MEMBER_A)).isZero();
		assertThat(data(send(HttpMethod.GET, ORDERS + "/" + order, a, null))).containsEntry("status", "CREATED")
			.containsEntry("simulatedPaymentId", null);

		ResponseEntity<String> approved = approve(a, order, "key-f", "{\"simulateFailure\":false}");
		assertThat(approved.getStatusCode().value()).isEqualTo(201);
		assertThat(data(approved)).containsEntry("status", "APPROVED");
		assertThat(countOf("simulated_payments", MEMBER_A)).isOne();
	}

	/**
	 * 오래된(무효가 된) 추천으로도 승인하고 서버가 다시 계산한다(D-23-3). 결정 32 Q4: 대표 카드 고정에서 대표 카드만 바꾸면 본 추천은 무효가
	 * 아니고, 승인 카드는 새 대표 카드이지만 비교 기준 {@code selected}가 같아 차이로 보고되지 않는다(한계 고정, N-G3-4).
	 */
	@Test
	void staleRecommendationIsApprovedAndRepresentativeChangeIsNotReported() {
		Session a = this.members.loginNew(MEMBER_A, EMAIL_A);
		String first = registerWithC01State(a);

		// 월간 상태 재입력으로 무효가 된 추천도 막지 않는다. 값이 같아 차이는 없다
		String order = createOrder(a);
		String stale = recommend(a, order);
		this.clock.advance(Duration.ofMinutes(1));
		assertThat(send(HttpMethod.PUT, monthlyState(first), a, C01_STATE).getStatusCode().value()).isEqualTo(200);
		assertThat(data(send(HttpMethod.GET, "/api/v1/me/recommendations/" + stale, a, null)))
			.containsEntry("invalidated", true)
			.containsEntry("invalidatedReason", "MONTHLY_STATE_CHANGED");
		ResponseEntity<String> approvedStale = approve(a, order, "key-s", seenBody(stale));
		assertThat(approvedStale.getStatusCode().value()).isEqualTo(201);
		Map<String, Object> staleRecalculation = map(data(approvedStale).get("recalculation"));
		assertThat(staleRecalculation).containsEntry("seenRecommendationId", stale)
			.containsEntry("changed", false)
			.containsEntry("differences", List.of());
		assertThat(staleRecalculation.get("recommendationId")).isNotEqualTo(stale);

		// Q4: 대표 카드를 first로 고정하고 추천을 본 뒤 대표 카드를 second로 바꾼다
		String second = register(a, "09174");
		putPreference(a, first);
		String fixedOrder = createOrder(a);
		String seen = recommend(a, fixedOrder);
		this.clock.advance(Duration.ofMinutes(1));
		putPreference(a, second);
		assertThat(data(send(HttpMethod.GET, "/api/v1/me/recommendations/" + seen, a, null)))
			.containsEntry("invalidated", false)
			.containsEntry("invalidatedReason", null);

		ResponseEntity<String> approved = approve(a, fixedOrder, "key-q4", seenBody(seen));
		assertThat(approved.getStatusCode().value()).isEqualTo(201);
		assertThat(data(approved)).containsEntry("selectionMode", "FIXED_REPRESENTATIVE")
			.containsEntry("userCardId", second);
		assertThat(map(data(approved).get("recalculation"))).containsEntry("seenRecommendationId", seen)
			.containsEntry("changed", false)
			.containsEntry("differences", List.of());
	}

	/**
	 * 추천과 승인 사이에 세션이 만료되면 승인은 401이고 저장하지 않는다. 다시 로그인하면 이전 CSRF 토큰은 403이고, 새 토큰으로 같은 키·같은
	 * 요청을 보내면 처리된다. 승인 뒤 로그아웃·재로그인에서 같은 키·같은 요청은 같은 결과를 돌려준다(G3 명세 패키지 4-2절).
	 */
	@Test
	void sessionExpiryBetweenRecommendationAndApprovalStoresNothing() {
		Session first = this.members.loginNew(MEMBER_A, EMAIL_A);
		registerWithC01State(first);
		String order = createOrder(first);
		String seen = recommend(first, order);
		String body = seenBody(seen);

		// 미사용 7일 → 세션 만료. 승인 401, 결제·원장·키 없음. 주문·추천은 남는다
		this.clock.advance(Duration.ofDays(7));
		ResponseEntity<String> expired = approve(first, order, "key-m", body);
		assertBody(expired, 401, UNAUTHENTICATED);
		assertNoStore(expired);
		assertBody(cancel(first, MISSING_PAYMENT, "key-m", "{\"amountWon\":1}"), 401, UNAUTHENTICATED);
		assertThat(countOf("simulated_payments", MEMBER_A)).isZero();
		assertThat(countOf("ledger_transactions", MEMBER_A)).isZero();
		assertThat(countOf("idempotency_keys", MEMBER_A)).isZero();
		assertThat(countOf("recommendations", MEMBER_A)).isOne();

		// 재로그인: 이전 토큰은 403이고 저장하지 않는다
		Session second = this.members.login(this.members.anonymous(), EMAIL_A);
		assertBody(approve(new Session(second.id(), first.csrf()), order, "key-m", body), 403, CSRF_INVALID);
		assertThat(countOf("simulated_payments", MEMBER_A)).isZero();

		// 새 토큰으로 같은 키·같은 요청 → 처리. 같은 회원이므로 본 추천은 404가 아니다
		ResponseEntity<String> approved = approve(second, order, "key-m", body);
		assertThat(approved.getStatusCode().value()).isEqualTo(201);
		assertThat(map(data(approved).get("recalculation"))).containsEntry("seenRecommendationId", seen);

		// 승인 뒤 로그아웃 → 재로그인 → 같은 키·같은 요청은 같은 결과(이중 결제 없음)
		assertThat(send(HttpMethod.POST, "/api/v1/auth/logout", second, null).getStatusCode().value())
			.isEqualTo(204);
		assertBody(approve(second, order, "key-m", body), 401, UNAUTHENTICATED);
		Session third = this.members.login(this.members.anonymous(), EMAIL_A);
		ResponseEntity<String> replay = approve(third, order, "key-m", body);
		assertThat(replay.getStatusCode().value()).isEqualTo(201);
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch(approved.getBody(), replay.getBody());
		assertThat(countOf("simulated_payments", MEMBER_A)).isOne();
		assertThat(countOf("ledger_transactions", MEMBER_A)).isOne();
	}

	/** 쿠키가 없거나 익명 CSRF 세션이면 4개 경로 모두 401이고, 401이 CSRF 403·키 400·본문 400보다 먼저다. */
	@Test
	void unauthenticatedRequestsAreUnauthorizedBeforeCsrfAndValidation() {
		Session anonymous = this.members.anonymous();
		Object[][] routes = { { HttpMethod.POST, ORDERS + "/ord-1/simulated-payments", "{\"simulateFailure\":true}" },
				{ HttpMethod.GET, payment("pay-1"), null },
				{ HttpMethod.POST, payment("pay-1") + "/cancellations", "{}" }, { HttpMethod.GET, LEDGER, null },
				{ HttpMethod.GET, "/api/v1/me/ledger-transactions", null } };
		for (Object[] route : routes) {
			for (Session session : new Session[] { Session.NONE, anonymous.withoutCsrf(), anonymous }) {
				ResponseEntity<String> response = send((HttpMethod) route[0], (String) route[1], session,
						(String) route[2]);
				assertBody(response, 401, UNAUTHENTICATED);
				assertNoStore(response);
			}
		}
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM idempotency_keys k JOIN users u"
				+ " ON u.user_id = k.user_id WHERE u.member_id IS NOT NULL", Integer.class)).isZero();
	}

	/**
	 * 승인·취소는 CSRF가 틀리면 403이고 결제·취소·원장·멱등 키를 저장하지 않는다. 403은 키 400·본문 400·404보다 먼저이고, 거절된 키는 소모되지
	 * 않아 올바른 토큰으로 같은 키를 쓸 수 있다.
	 */
	@Test
	void writesWithoutValidCsrfAreForbiddenAndStoreNothing() {
		Session a = this.members.loginNew(MEMBER_A, EMAIL_A);
		registerWithC01State(a);
		String order = createOrder(a);
		String otherOrder = createOrder(a);
		String payment = (String) data(approve(a, otherOrder, "key-p", null)).get("simulatedPaymentId");
		Session noToken = a.withoutCsrf();
		Session wrongToken = new Session(a.id(), "x".repeat(43));

		for (Session session : new Session[] { noToken, wrongToken }) {
			assertForbidden(approve(session, order, "key-w", null));
			assertForbidden(approve(session, order, null, "{\"simulateFailure\":true}"));
			assertForbidden(approve(session, MISSING_ORDER, "key-w", null));
			assertForbidden(cancel(session, payment, "key-c", "{\"amountWon\":1000}"));
			assertForbidden(cancel(session, payment, null, "{}"));
			assertForbidden(cancel(session, MISSING_PAYMENT, "key-c", "{\"amountWon\":1000}"));
		}
		assertThat(countOf("simulated_payments", MEMBER_A)).isOne();
		assertThat(countOf("payment_cancellations", MEMBER_A)).isZero();
		assertThat(countOf("ledger_transactions", MEMBER_A)).isOne();
		assertThat(countOf("idempotency_keys", MEMBER_A)).isOne();
		assertThat(data(send(HttpMethod.GET, ORDERS + "/" + order, noToken, null))).containsEntry("status",
				"CREATED");

		// 같은 키를 올바른 토큰으로 보내면 처리된다
		assertThat(approve(a, order, "key-w", null).getStatusCode().value()).isEqualTo(201);
		assertThat(cancel(a, payment, "key-c", "{\"amountWon\":1000}").getStatusCode().value()).isEqualTo(201);
	}

	/**
	 * 시연 장부의 주문·결제는 회원에게 404이고 시연 원장은 회원 원장에 나오지 않는다. 시연 헤더를 붙여도 회원 장부로 처리한다(결정 37 D2·D4).
	 * CODEF 원장은 연결하지 않았으므로 member 원장에는 자체 모의 거래만 있다(R8).
	 */
	@Test
	void demoLedgerIsUnreachableAndDemoHeaderIsIgnored() {
		Session a = this.members.loginNew(MEMBER_A, EMAIL_A);
		String card = registerWithC01State(a);
		CurrentUser demo = this.demoUserService.currentUserOf(DemoUser.MY_CARDS);
		String demoCard = this.userCardService.register(demo, new NewUserCard("시연", "09271", false)).userCardId();
		this.demoCardId = Long.parseLong(demoCard.substring("uc-".length()));
		this.monthlyStateService.replace(demo, demoCard, YearMonth.of(2026, 11), new MonthlyStateInput(
				new MonthlyStateInput.Amount(0, new DatePeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))),
				null, null, null, null, LocalDate.of(2026, 1, 1)));
		String demoOrder = this.orderService
			.create(demo, new NewOrder(100000, LocalDate.of(2026, 11, 18), null, "시연", "ORDINARY_DOMESTIC",
					PaymentMethod.DIRECT))
			.orderId();
		this.demoOrderId = Long.parseLong(demoOrder.substring("ord-".length()));
		String demoPayment = this.simulatedPaymentService
			.approve(demo, demoOrder, new ApprovalCommand(null, false), "demo-g3c")
			.simulatedPaymentId();
		int demoLedger = countDemo("ledger_transactions");
		assertThat(demoLedger).isPositive();

		assertNotFound(approve(a, demoOrder, "key-d", null), "ORDER_NOT_FOUND");
		assertNotFound(send(HttpMethod.GET, payment(demoPayment), a, null), "SIMULATED_PAYMENT_NOT_FOUND");
		assertNotFound(cancel(a, demoPayment, "key-d", "{\"amountWon\":1}"), "SIMULATED_PAYMENT_NOT_FOUND");
		assertBody(send(HttpMethod.GET, LEDGER + "&userCardId=" + demoCard, a, null), 404, USER_CARD_NOT_FOUND);
		assertNotFound(withDemoHeader(HttpMethod.GET, payment(demoPayment), a, null, null),
				"SIMULATED_PAYMENT_NOT_FOUND");

		// 회원 원장은 비어 있다. 시연 헤더를 붙여도 같다
		assertThat(items(send(HttpMethod.GET, LEDGER, a, null))).isEmpty();
		assertThat(items(withDemoHeader(HttpMethod.GET, LEDGER, a, null, null))).isEmpty();

		// 시연 헤더를 붙인 승인도 회원 장부에 기록하고 시연 장부는 바뀌지 않는다
		String order = createOrder(a);
		ResponseEntity<String> approved = withDemoHeader(HttpMethod.POST, ORDERS + "/" + order + "/simulated-payments",
				a, null, "key-h");
		assertThat(approved.getStatusCode().value()).isEqualTo(201);
		assertThat(data(approved)).containsEntry("userCardId", card);
		assertThat(items(send(HttpMethod.GET, LEDGER, a, null))).singleElement()
			.satisfies(item -> assertThat(item).containsEntry("source", "SIMULATION").containsEntry("userCardId", card));
		assertThat(countDemo("ledger_transactions")).isEqualTo(demoLedger);
		assertThat(countOf("ledger_transactions", MEMBER_A)).isOne();
		// CODEF 원장은 연결하지 않아 회원 장부에는 자체 모의 거래 밖의 원장 행이 없다(R8)
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger_transactions t JOIN users u"
				+ " ON u.user_id = t.user_id WHERE u.member_id IS NOT NULL AND t.source <> 'SIMULATION'", Integer.class))
			.isZero();
	}

	private String register(Session session, String cardProductId) {
		ResponseEntity<String> created = send(HttpMethod.POST, CARDS, session,
				"{\"displayName\":\"카드\",\"cardProductId\":\"" + cardProductId + "\"}");
		assertThat(created.getStatusCode().value()).isEqualTo(201);
		return userCardId(created.getBody());
	}

	private String registerWithC01State(Session session) {
		String card = register(session, "09271");
		assertThat(send(HttpMethod.PUT, monthlyState(card), session, C01_STATE).getStatusCode().value())
			.isEqualTo(200);
		return card;
	}

	private void putPreference(Session session, String representativeUserCardId) {
		assertThat(send(HttpMethod.PUT, PREFERENCE, session,
				"{\"selectionMode\":\"FIXED_REPRESENTATIVE\",\"representativeUserCardId\":\"" + representativeUserCardId
						+ "\"}")
			.getStatusCode()
			.value()).isEqualTo(200);
	}

	private String createOrder(Session session) {
		ResponseEntity<String> created = send(HttpMethod.POST, ORDERS, session, C01_ORDER);
		assertThat(created.getStatusCode().value()).isEqualTo(201);
		return (String) data(created).get("orderId");
	}

	private String recommend(Session session, String orderId) {
		ResponseEntity<String> calculated = send(HttpMethod.POST, ORDERS + "/" + orderId + "/recommendations",
				session, null);
		assertThat(calculated.getStatusCode().value()).isEqualTo(201);
		return (String) data(calculated).get("recommendationId");
	}

	private ResponseEntity<String> approve(Session session, String orderId, String key, String body) {
		return this.members.sendWithHeaders(HttpMethod.POST, ORDERS + "/" + orderId + "/simulated-payments", session,
				body, headers(key, null));
	}

	private ResponseEntity<String> cancel(Session session, String paymentId, String key, String body) {
		return this.members.sendWithHeaders(HttpMethod.POST, payment(paymentId) + "/cancellations", session, body,
				headers(key, null));
	}

	private ResponseEntity<String> withDemoHeader(HttpMethod method, String path, Session session, String body,
			String key) {
		return this.members.sendWithHeaders(method, path, session, body, headers(key, "MY_CARDS"));
	}

	private static Map<String, String> headers(String key, String demoUser) {
		Map<String, String> headers = new HashMap<>();
		headers.put("Idempotency-Key", key);
		headers.put("X-BeONE-Demo-User", demoUser);
		return headers;
	}

	private int countOf(String table, String memberId) {
		if (table.equals("payment_cancellations")) {
			return this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_cancellations c"
					+ " JOIN simulated_payments p ON p.simulated_payment_id = c.simulated_payment_id"
					+ " JOIN users u ON u.user_id = p.user_id WHERE u.member_id = ?", Integer.class, memberId);
		}
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

	private static String monthlyState(String userCardId) {
		return CARDS + "/" + userCardId + "/monthly-states/2026-11";
	}

	private static String payment(String paymentId) {
		return "/api/v1/me/simulated-payments/" + paymentId;
	}

	private static String seenBody(String recommendationId) {
		return "{\"seenRecommendationId\":\"" + recommendationId + "\"}";
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> map(Object value) {
		return (Map<String, Object>) value;
	}

	private static Map<String, Object> data(ResponseEntity<String> response) {
		return map(JsonMapper.builder().build().readValue(response.getBody(), Map.class).get("data"));
	}

	private static List<Map<String, Object>> items(ResponseEntity<String> response) {
		assertThat(response.getStatusCode().value()).isEqualTo(200);
		return items(data(response));
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> items(Map<String, Object> data) {
		return (List<Map<String, Object>>) data.get("items");
	}

	private static void assertError(ResponseEntity<String> response, int status, String code) {
		assertThat(response.getStatusCode().value()).isEqualTo(status);
		assertThat(response.getBody()).contains("\"code\":\"" + code + "\"");
		assertNoStore(response);
	}

	private static void assertNotFound(ResponseEntity<String> response, String code) {
		assertError(response, 404, code);
	}

	private static void assertForbidden(ResponseEntity<String> response) {
		assertBody(response, 403, CSRF_INVALID);
		assertNoStore(response);
	}

}
