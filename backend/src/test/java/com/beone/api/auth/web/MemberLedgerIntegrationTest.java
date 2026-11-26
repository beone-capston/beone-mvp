package com.beone.api.auth.web;

import static com.beone.api.auth.web.MemberApiTestClient.UNAUTHENTICATED;
import static com.beone.api.auth.web.MemberApiTestClient.USER_CARD_NOT_FOUND;
import static com.beone.api.auth.web.MemberApiTestClient.assertBody;
import static com.beone.api.auth.web.MemberApiTestClient.assertNoStore;
import static com.beone.api.auth.web.MemberApiTestClient.userCardId;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.beone.api.auth.web.MemberApiTestClient.Session;
import com.beone.api.card.application.NewUserCard;
import com.beone.api.card.application.UserCardService;
import com.beone.api.ledger.application.AttributionStatus;
import com.beone.api.ledger.application.LedgerEntryType;
import com.beone.api.ledger.application.LedgerSource;
import com.beone.api.ledger.persistence.LedgerTransactionEntity;
import com.beone.api.ledger.persistence.LedgerTransactionRepository;
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

/**
 * #165: {@code member} 프로필에 등록한 원장 조회 API({@code GET /api/v1/me/ledger-transactions})를 실제 HTTP·MySQL로 확인한다. 세션
 * 401·{@code Cache-Control: no-store}는 #158의 인터셉터·필터가 경로 기준으로 처리하므로, 그것이 새로 연 경로에도 적용되는지 단언한다.
 *
 * <p>원장 조회만 따로 확인하려고 회원 원장 행은 결제 API가 아니라 원장 저장소에 직접 넣는 테스트 픽스처로 만든다. 출처는 모의
 * 거래({@code SIMULATION})이고 모의 결제 번호는 비워 둔다. 모의 결제 API가 쓴 회원 원장은
 * {@link MemberPaymentIntegrationTest}가 확인한다(G3c #161).
 *
 * <p>기대값 출처
 * <ul>
 * <li>응답 형식·오류 코드: docs/api/ledger.md, docs/api/cards.md({@code USER_CARD_NOT_FOUND} 문구)</li>
 * <li>401 → 400 → 404 순서, 남의 자원 404(결정 37 D5), 시연 헤더 무시(D4), no-store(결정 37 구현 범위)</li>
 * <li>CODEF는 원장에 행을 넣지 않아 {@code complete=false}·사유 빈 배열(결정 22 R8, 결정 32 Q5)</li>
 * </ul>
 * 새 테스트 컨텍스트를 만들지 않도록 {@link MemberProfileIntegrationTest}와 같은 프로필·가변 시계 조합을 쓴다.
 */
@IntegrationTest
@ActiveProfiles("member")
@Import(AuthSessionIntegrationTest.MutableClockConfiguration.class)
class MemberLedgerIntegrationTest {

	private static final Instant START = Instant.parse("2026-11-18T01:00:00Z");

	private static final String MEMBER_A = "usr-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

	private static final String MEMBER_B = "usr-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

	private static final String CARDS = "/api/v1/me/cards";

	private static final String LEDGER = "/api/v1/me/ledger-transactions?month=2026-11";

	private static final String EMPTY_LEDGER = "{\"data\":{\"month\":\"2026-11\",\"items\":[],\"coverage\":[]}}";

	private static final String PERIOD = "\"period\":{\"from\":\"2026-11-01\",\"to\":\"2026-11-30\"}";

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
	LedgerTransactionRepository ledgerTransactionRepository;

	private MemberApiTestClient members;

	/** 시연 장부에 이 테스트가 직접 등록한 보유 카드 번호. 원장 행은 카드 삭제의 외래 키 연쇄로 함께 지워진다. */
	private Long demoCardId;

	@BeforeEach
	void setUp() {
		this.members = new MemberApiTestClient(this.port, this.jdbcTemplate);
		clearTables();
		this.clock.set(START);
	}

	@AfterEach
	void clearTables() {
		if (this.demoCardId != null) {
			this.jdbcTemplate.update("DELETE FROM user_cards WHERE user_card_id = ?", this.demoCardId);
			this.demoCardId = null;
		}
		// 회원 원장 행은 회원 보유 카드를 지울 때 외래 키 연쇄 삭제로 함께 지워진다
		this.members.clearMemberData();
	}

	/**
	 * 회원 A·B의 원장은 나뉜다. 각자 자기 카드의 행과 카드·출처별 완전성만 본다. CODEF는 원장에 연결하지 않아 행이 없고 완전하다고
	 * 선언하지 않는다(R8).
	 */
	@Test
	void ledgersAreSeparatedPerMember() {
		Session a = this.members.loginNew(MEMBER_A, "a@example.com");
		Session b = this.members.loginNew(MEMBER_B, "b@example.com");
		String cardOfA = register(a, "09174");
		String cardOfB = register(b, "09271");
		long approvalOfA = insertApproval(MEMBER_A, cardOfA, 10000, LocalDate.of(2026, 11, 3));
		long cancellationOfA = insertCancellation(MEMBER_A, cardOfA, -4000, LocalDate.of(2026, 11, 4), approvalOfA);
		long approvalOfB = insertApproval(MEMBER_B, cardOfB, 7000, LocalDate.of(2026, 11, 5));
		// 다른 달의 행은 거래일 기준으로 빠진다
		insertApproval(MEMBER_A, cardOfA, 3000, LocalDate.of(2026, 10, 31));

		ResponseEntity<String> ledgerOfA = send(LEDGER, a);
		assertBody(ledgerOfA, 200, "{\"data\":{\"month\":\"2026-11\",\"items\":["
				+ item(approvalOfA, cardOfA, "APPROVAL", 10000, "2026-11-03", null) + ","
				+ item(cancellationOfA, cardOfA, "CANCELLATION", -4000, "2026-11-04", approvalOfA)
				+ "],\"coverage\":[" + coverage(cardOfA, "SIMULATION", true) + "," + coverage(cardOfA, "CODEF", false)
				+ "]}}");
		assertNoStore(ledgerOfA);

		assertBody(send(LEDGER, b), 200,
				"{\"data\":{\"month\":\"2026-11\",\"items\":["
						+ item(approvalOfB, cardOfB, "APPROVAL", 7000, "2026-11-05", null) + "],\"coverage\":["
						+ coverage(cardOfB, "SIMULATION", true) + "," + coverage(cardOfB, "CODEF", false) + "]}}");

		// 출처 CODEF로 좁히면 행이 없고 완전하다고 선언하지 않는다(R8 미연결 유지)
		assertBody(send(LEDGER + "&source=CODEF", a), 200,
				"{\"data\":{\"month\":\"2026-11\",\"items\":[],\"coverage\":[" + coverage(cardOfA, "CODEF", false)
						+ "]}}");
	}

	/** B가 A의 카드로 좁혀도 404이고, 없는 카드·형식 오류 카드와 본문이 같다. A의 원장은 바뀌지 않는다. */
	@Test
	void cardOfAnotherMemberIsNotFoundWithTheSameBody() {
		Session a = this.members.loginNew(MEMBER_A, "a@example.com");
		Session b = this.members.loginNew(MEMBER_B, "b@example.com");
		String cardOfA = register(a, "09174");
		insertApproval(MEMBER_A, cardOfA, 10000, LocalDate.of(2026, 11, 3));
		String before = send(LEDGER, a).getBody();

		ResponseEntity<String> foreign = send(LEDGER + "&userCardId=" + cardOfA, b);
		assertBody(foreign, 404, USER_CARD_NOT_FOUND);
		assertNoStore(foreign);
		for (String other : List.of("uc-999999999", "abc")) {
			ResponseEntity<String> missing = send(LEDGER + "&userCardId=" + other, b);
			assertBody(missing, 404, USER_CARD_NOT_FOUND);
			assertNoStore(missing);
			assertThat(foreign.getBody()).isEqualTo(missing.getBody());
		}
		assertBody(send(LEDGER, b), 200, EMPTY_LEDGER);
		assertThat(send(LEDGER, a).getBody()).isEqualTo(before);
	}

	/** 시연 장부의 원장 행은 회원에게 보이지 않고, 시연 헤더를 보내도 회원 장부로 조회한다(결정 37 D2·D4). */
	@Test
	void demoLedgerIsUnreachableAndDemoHeaderIsIgnored() {
		Session a = this.members.loginNew(MEMBER_A, "a@example.com");
		CurrentUser demo = this.demoUserService.currentUserOf(DemoUser.MY_CARDS);
		String demoCard = this.userCardService.register(demo, new NewUserCard("시연", "09271", false)).userCardId();
		this.demoCardId = cardNumber(demoCard);
		insert(demo.userId(), this.demoCardId, LedgerEntryType.APPROVAL, 5000, LocalDate.of(2026, 11, 6), null);
		int demoLedger = countDemoLedger();
		assertThat(demoLedger).isPositive();

		assertBody(send(LEDGER + "&userCardId=" + demoCard, a), 404, USER_CARD_NOT_FOUND);
		assertBody(this.members.getWithDemoHeader(LEDGER + "&userCardId=" + demoCard, a, "MY_CARDS"), 404,
				USER_CARD_NOT_FOUND);
		assertBody(send(LEDGER, a), 200, EMPTY_LEDGER);
		ResponseEntity<String> withHeader = this.members.getWithDemoHeader(LEDGER, a, "MY_CARDS");
		assertBody(withHeader, 200, EMPTY_LEDGER);
		assertNoStore(withHeader);
		assertThat(countDemoLedger()).isEqualTo(demoLedger);
	}

	/** 쿠키가 없거나 익명 CSRF 세션이면 401이고, 401이 {@code month} 누락 400보다 먼저다. 시연 헤더만으로는 인증되지 않는다. */
	@Test
	void unauthenticatedRequestsAreUnauthorizedBeforeValidation() {
		Session anonymous = this.members.anonymous();
		for (String path : List.of(LEDGER, "/api/v1/me/ledger-transactions")) {
			for (Session session : new Session[] { Session.NONE, anonymous.withoutCsrf(), anonymous }) {
				ResponseEntity<String> response = send(path, session);
				assertBody(response, 401, UNAUTHENTICATED);
				assertNoStore(response);
			}
		}
		assertBody(this.members.getWithDemoHeader(LEDGER, Session.NONE, "MY_CARDS"), 401, UNAUTHENTICATED);
	}

	/** 인증을 통과한 뒤 {@code month} 누락·형식 오류는 400 {@code INVALID_REQUEST}이고 no-store다. 조회라 CSRF는 필요 없다. */
	@Test
	void invalidMonthIsRejectedAfterAuthentication() {
		Session a = this.members.loginNew(MEMBER_A, "a@example.com");
		Session noToken = a.withoutCsrf();
		assertBody(send("/api/v1/me/ledger-transactions", noToken), 400,
				"{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 값을 확인해 주세요.\",\"errors\":[{\"field\":\"month\",\"message\":\"필수 값입니다.\"}]}");
		ResponseEntity<String> malformed = send("/api/v1/me/ledger-transactions?month=2026-13", noToken);
		assertBody(malformed, 400,
				"{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 값을 확인해 주세요.\",\"errors\":[{\"field\":\"month\",\"message\":\"형식이 올바르지 않습니다.\"}]}");
		assertNoStore(malformed);
		assertBody(send(LEDGER, noToken), 200, EMPTY_LEDGER);
	}

	private String register(Session session, String cardProductId) {
		ResponseEntity<String> created = this.members.send(HttpMethod.POST, CARDS, session,
				"{\"displayName\":\"카드\",\"cardProductId\":\"" + cardProductId + "\"}");
		assertThat(created.getStatusCode().value()).isEqualTo(201);
		return userCardId(created.getBody());
	}

	private long insertApproval(String memberId, String userCardId, long amountWon, LocalDate date) {
		return insert(this.members.userIdOf(memberId), cardNumber(userCardId), LedgerEntryType.APPROVAL, amountWon,
				date, null);
	}

	private long insertCancellation(String memberId, String userCardId, long amountWon, LocalDate date,
			long originalLedgerId) {
		return insert(this.members.userIdOf(memberId), cardNumber(userCardId), LedgerEntryType.CANCELLATION,
				amountWon, date, originalLedgerId);
	}

	/**
	 * 결제 API 없이 원장 저장소에 모의 거래 행을 직접 넣는다. 모의 결제 번호는 비워 둔다(원장 조회만 따로 확인). 취소 행은 실제
	 * 쓰기 경로처럼 거래 시각을 비운다(docs/api/ledger.md).
	 */
	private long insert(long userId, long userCardId, LedgerEntryType entryType, long amountWon, LocalDate date,
			Long originalLedgerId) {
		return this.ledgerTransactionRepository
			.save(new LedgerTransactionEntity(userId, userCardId, LedgerSource.SIMULATION, entryType, amountWon, date,
					(entryType == LedgerEntryType.CANCELLATION) ? null : LocalTime.of(10, 0), "ORDINARY_DOMESTIC", "DIRECT", "동네 마트", originalLedgerId, null,
					date.toString().substring(0, 7), AttributionStatus.CONFIRMED, LocalDateTime.of(2026, 11, 18, 10, 0)))
			.getLedgerId();
	}

	private static String item(long ledgerId, String userCardId, String entryType, long amountWon, String date,
			Long originalLedgerId) {
		return """
				{"ledgerTransactionId":"lt-%d","userCardId":"%s","source":"SIMULATION","entryType":"%s","amountWon":%d,
				 "transactionDate":"%s","transactionTime":%s,"merchantClass":"ORDINARY_DOMESTIC",
				 "paymentMethod":"DIRECT","transactionNature":null,"merchantName":"동네 마트","originalTransactionId":%s,
				 "simulatedPaymentId":null,"attributionMonth":{"value":"2026-11","status":"CONFIRMED"},"syncId":null,"fullyCanceled":null}"""
			.formatted(ledgerId, userCardId, entryType, amountWon, date,
					"CANCELLATION".equals(entryType) ? "null" : "\"10:00:00\"",
					(originalLedgerId == null) ? "null" : "\"lt-" + originalLedgerId + "\"");
	}

	private static String coverage(String userCardId, String source, boolean complete) {
		return "{\"userCardId\":\"" + userCardId + "\",\"source\":\"" + source + "\"," + PERIOD + ",\"complete\":"
				+ complete + ",\"incompleteReasons\":[]}";
	}

	private int countDemoLedger() {
		return this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger_transactions t JOIN users u"
				+ " ON u.user_id = t.user_id WHERE u.member_id IS NULL", Integer.class);
	}

	private ResponseEntity<String> send(String path, Session session) {
		return this.members.send(HttpMethod.GET, path, session, null);
	}

	private static long cardNumber(String userCardId) {
		return Long.parseLong(userCardId.substring("uc-".length()));
	}

}
