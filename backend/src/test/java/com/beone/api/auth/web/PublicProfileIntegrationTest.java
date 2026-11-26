package com.beone.api.auth.web;

import static com.beone.api.auth.web.MemberApiTestClient.PASSWORD;
import static com.beone.api.auth.web.MemberApiTestClient.UNAUTHENTICATED;
import static com.beone.api.auth.web.MemberApiTestClient.assertBody;
import static com.beone.api.auth.web.MemberApiTestClient.userCardId;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.beone.api.auth.application.AuthMailSender;
import com.beone.api.auth.application.FakeAuthMailSender;
import com.beone.api.auth.application.SmtpAuthMailSender;
import com.beone.api.auth.application.SmtpStubServer;
import com.beone.api.auth.web.MemberApiTestClient.Session;
import com.beone.api.support.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * 공개 프로필 {@code prod}를 실제 HTTP·MySQL로 띄워 회원 흐름이 동작하고 시연 부품·강제 실패가 막히는지 확인한다(결정 38-6, G5 L3).
 *
 * <p>{@code prod}는 프로필 그룹으로 {@code member}를 함께 켜지만 테스트의 {@code @ActiveProfiles}는 그룹을 펼치지 않으므로 두 프로필을
 * 직접 켠다. 메일은 실제 prod 구성 그대로 SMTP 발송기(결정 39-6, #207)가 보내고, 받는 쪽은 루프백 스텁 SMTP({@link SmtpStubServer})다.
 * 인증 토큰은 받은 메일의 링크에서 꺼낸다. 스텁이 STARTTLS를 지원하지 않으므로 이 시험에서만 STARTTLS를 끈다. 메일 설정이 없을 때의 기동
 * 실패는 {@code AuthMailSenderProfileTest}·{@code SmtpMailConfigurationTest}가 단위로 고정한다.
 *
 * <p>기대값 출처: 결정 38-6(공개 구성의 시연·Fake·강제 실패 차단), 결정 38-1(member의 {@code simulateFailure: true}는 400·미저장),
 * docs/api/auth.md·cards.md·orders.md·mock-payments.md. 주문·월간 상태 값은 {@code MemberPaymentIntegrationTest}와 같은 평가 자료
 * C01이고, 시계는 서울 2026-11-18 10:00에서 시작한다.
 */
@IntegrationTest
@ActiveProfiles({ "prod", "member" })
@Import(AuthSessionIntegrationTest.MutableClockConfiguration.class)
@TestPropertySource(properties = { "spring.mail.host=127.0.0.1", "spring.mail.username=synthetic-user",
		"spring.mail.password=synthetic-app-password", "spring.mail.properties[mail.smtp.starttls.enable]=false",
		"spring.mail.properties[mail.smtp.starttls.required]=false", "beone.mail.from=no-reply@beone.example.test",
		"beone.mail.link-base-url=https://beone.example.test" })
class PublicProfileIntegrationTest {

	private static final SmtpStubServer SMTP = new SmtpStubServer();

	private static final Pattern VERIFICATION_TOKEN = Pattern
		.compile("https://beone\\.example\\.test/email-verification\\?token=([A-Za-z0-9_-]{43})");

	private static final Instant START = Instant.parse("2026-11-18T01:00:00Z");

	private static final String EMAIL = "public@example.com";

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

	@DynamicPropertySource
	static void smtpPort(DynamicPropertyRegistry registry) {
		registry.add("spring.mail.port", SMTP::port);
	}

	@AfterAll
	static void stopSmtp() {
		SMTP.close();
	}

	@Autowired
	ApplicationContext context;

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
		SMTP.clear();
	}

	@Test
	void prodRunsWithEveryMigrationAndWithoutTheFakeMailSender() {
		assertThat(this.context.getEnvironment().getActiveProfiles()).contains("prod", "member")
			.doesNotContain("local", "demo");
		assertThat(this.context.getBeanNamesForType(FakeAuthMailSender.class)).isEmpty();
		assertThat(this.context.getBean(AuthMailSender.class)).isInstanceOf(SmtpAuthMailSender.class);
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = FALSE",
				Integer.class))
			.isZero();
		assertThat(this.jdbcTemplate.queryForList(
				"SELECT version FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank",
				String.class))
			.containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15",
					"16");
	}

	/** 가입 → 이메일 인증(테스트 발송기의 토큰) → 로그인 → /me 200. 공개 회원의 장부는 '내 카드'다. */
	@Test
	void signUpVerifyLoginThenMeIsAvailable() {
		Session member = signUpAndLogin();

		ResponseEntity<String> me = send(HttpMethod.GET, "/api/v1/me", member, null);

		assertBody(me, 200, """
				{"data":{"demoUser":null,"displayName":"내 카드","portfolioType":"REAL","readOnly":false,
				  "cardCount":0,"codefConnected":false}}
				""");
		MemberApiTestClient.assertNoStore(me);
	}

	/** 시연 헤더로는 시연 장부에 닿지 않는다. 헤더만 보내면 401이고, 회원 세션에 붙여도 무시된다. */
	@Test
	void demoLedgerIsUnreachable() {
		Integer demoCards = this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_cards uc JOIN users u"
				+ " ON u.user_id = uc.user_id WHERE u.member_id IS NULL AND uc.released_at IS NULL", Integer.class);
		assertThat(demoCards).isPositive();

		for (String path : List.of("/api/v1/me", "/api/v1/me/cards", "/api/v1/me/orders")) {
			assertBody(this.members.getWithDemoHeader(path, Session.NONE, "VIRTUAL_PORTFOLIO"), 401, UNAUTHENTICATED);
		}
		assertThat(send(HttpMethod.GET, "/api/v1/demo-users", Session.NONE, null).getStatusCode().value())
			.isEqualTo(404);

		Session member = signUpAndLogin();
		assertThat(this.members.getWithDemoHeader("/api/v1/me", member, "VIRTUAL_PORTFOLIO").getBody())
			.contains("\"demoUser\":null")
			.contains("\"cardCount\":0");
		assertBody(this.members.getWithDemoHeader("/api/v1/me/cards", member, "VIRTUAL_PORTFOLIO"), 200,
				"{\"data\":{\"items\":[]}}");
	}

	/**
	 * 결정 38-1·38-6: 공개 구성에서 {@code simulateFailure: true}는 400이고 아무것도 저장하지 않는다. 필드를 생략하면 201 승인이다. 본인 주문
	 * 목록에서 주문을 읽을 수 있다.
	 */
	@Test
	void forcedFailureIsRejectedAndOrdinaryApprovalAndOrderListWork() {
		Session member = signUpAndLogin();
		String memberId = this.jdbcTemplate.queryForObject("SELECT member_id FROM members WHERE email = ?", String.class,
				EMAIL);
		ResponseEntity<String> card = send(HttpMethod.POST, "/api/v1/me/cards", member,
				"{\"displayName\":\"카드\",\"cardProductId\":\"09271\"}");
		assertThat(card.getStatusCode().value()).isEqualTo(201);
		assertThat(send(HttpMethod.PUT, "/api/v1/me/cards/" + userCardId(card.getBody()) + "/monthly-states/2026-11",
				member, C01_STATE)
			.getStatusCode()
			.value()).isEqualTo(200);
		ResponseEntity<String> created = send(HttpMethod.POST, "/api/v1/me/orders", member, C01_ORDER);
		assertThat(created.getStatusCode().value()).isEqualTo(201);
		String orderId = orderId(created.getBody());

		assertBody(approve(member, orderId, "key-f", "{\"simulateFailure\":true}"), 400, INVALID_REQUEST);
		for (String table : List.of("simulated_payments", "ledger_transactions", "idempotency_keys")) {
			assertThat(countOf(table, memberId)).as(table).isZero();
		}

		ResponseEntity<String> approved = approve(member, orderId, "key-o", null);
		assertThat(approved.getStatusCode().value()).isEqualTo(201);
		assertThat(approved.getBody()).contains("\"status\":\"APPROVED\"");
		assertThat(countOf("simulated_payments", memberId)).isOne();

		ResponseEntity<String> orders = send(HttpMethod.GET, "/api/v1/me/orders", member, null);
		assertThat(orders.getStatusCode().value()).isEqualTo(200);
		assertThat(orders.getBody()).contains("\"orderId\":\"" + orderId + "\"");
		MemberApiTestClient.assertNoStore(orders);
	}

	private Session signUpAndLogin() {
		Session anonymous = this.members.anonymous();
		assertThat(send(HttpMethod.POST, "/api/v1/auth/sign-up", anonymous,
				"{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}")
			.getStatusCode()
			.value()).isEqualTo(202);
		SmtpStubServer.Received mail = SMTP.received().getFirst();
		assertThat(SMTP.received()).hasSize(1);
		assertThat(mail.recipients()).containsExactly(EMAIL);
		Matcher token = VERIFICATION_TOKEN.matcher(mail.text());
		assertThat(token.find()).isTrue();
		assertThat(send(HttpMethod.POST, "/api/v1/auth/email-verifications", anonymous,
				"{\"token\":\"" + token.group(1) + "\"}")
			.getStatusCode()
			.value()).isEqualTo(204);
		return this.members.login(anonymous, EMAIL);
	}

	private ResponseEntity<String> approve(Session session, String orderId, String key, String body) {
		return this.members.sendWithHeaders(HttpMethod.POST, "/api/v1/me/orders/" + orderId + "/simulated-payments",
				session, body, Map.of("Idempotency-Key", key));
	}

	private ResponseEntity<String> send(HttpMethod method, String path, Session session, String body) {
		return this.members.send(method, path, session, body);
	}

	private int countOf(String table, String memberId) {
		return this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table
				+ " t JOIN users u ON u.user_id = t.user_id WHERE u.member_id = ?", Integer.class, memberId);
	}

	private static String orderId(String body) {
		int start = body.indexOf("\"orderId\":\"") + "\"orderId\":\"".length();
		return body.substring(start, body.indexOf('"', start));
	}

}
