package com.beone.api.auth.web;

import static com.beone.api.auth.web.MemberApiTestClient.CSRF_INVALID;
import static com.beone.api.auth.web.MemberApiTestClient.PASSWORD;
import static com.beone.api.auth.web.MemberApiTestClient.UNAUTHENTICATED;
import static com.beone.api.auth.web.MemberApiTestClient.assertBody;
import static com.beone.api.auth.web.MemberApiTestClient.assertNoStore;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import com.beone.api.auth.application.FakeAuthMailSender;
import com.beone.api.auth.web.MemberApiTestClient.Session;
import com.beone.api.codef.application.CodefGateway;
import com.beone.api.codef.application.CodefRegistration;
import com.beone.api.codef.application.ConnectedIdCiphers;
import com.beone.api.codef.application.ConnectedIdKeySource;
import com.beone.api.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 회원 탈퇴 {@code DELETE /api/v1/me}(결정 D10, 관리자 T5 지시)를 실제 HTTP·MySQL로 확인한다. 기대값은 지시에서 구현 전에 정했다.
 * <ul>
 * <li>비밀번호 재확인 + CSRF. 401 → 403 → 본문 400 → 429 → 비밀번호 불일치 400({@code password} 필드 오류) 순서</li>
 * <li>성공 204: 그 회원의 보유카드·CODEF 연결·장부·세션·인증 토큰·로그인 실패 기록을 지우고, 같은 세션 쿠키는 바로 401</li>
 * <li>CODEF 해제는 커밋 뒤 요청하고, 실패해도 탈퇴는 성공한다. CODEF는 기록용 스텁만 쓴다(실 호출 없음)</li>
 * <li>같은 이메일로 다시 가입할 수 있다. 메일 요청 제한(60초)은 남는다</li>
 * </ul>
 * 이메일·connectedId는 합성 값이다.
 */
@IntegrationTest
@ActiveProfiles("member")
@Import({ AuthSessionIntegrationTest.MutableClockConfiguration.class,
		MemberWithdrawalIntegrationTest.StubCodefConfiguration.class })
class MemberWithdrawalIntegrationTest {

	private static final Instant START = Instant.parse("2026-11-18T01:00:00Z");

	private static final String MEMBER_A = "usr-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

	private static final String MEMBER_B = "usr-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

	private static final String EMAIL_A = "a@example.com";

	private static final String ME = "/api/v1/me";

	private static final String CARD = "{\"displayName\":\"내 첫 카드\",\"cardProductId\":\"09174\"}";

	private static final String CONNECTED_ID = "synthetic-withdrawal-connected-id";

	private static final byte[] KEY = new byte[32];

	private static final String CORRECT = "{\"password\":\"" + PASSWORD + "\"}";

	private static final String WRONG = "{\"password\":\"wrong-pass-1!\"}";

	private static final String PASSWORD_MISMATCH = "{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 값을 확인해 주세요.\","
			+ "\"errors\":[{\"field\":\"password\",\"message\":\"비밀번호가 일치하지 않습니다.\"}]}";

	private static final String RATE_LIMITED = "{\"code\":\"RATE_LIMITED\",\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.\",\"errors\":[]}";

	/** 기록용 CODEF 스텁. 해제 요청을 받은 connectedId를 남기고, 설정하면 실패한다. */
	static final List<String> RELEASED = new CopyOnWriteArrayList<>();

	static volatile boolean releaseFails;

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
		for (String table : new String[] { "monthly_states", "codef_performance_references",
				"codef_sync_incomplete_marks", "codef_syncs", "codef_connections" }) {
			this.jdbcTemplate.update("DELETE t FROM " + table + " t JOIN users u ON u.user_id = t.user_id"
					+ " WHERE u.member_id IS NOT NULL");
		}
		this.members.clearMemberData();
		this.mailSender.clear();
		RELEASED.clear();
		releaseFails = false;
	}

	/** 성공: 204, 세션 쿠키 삭제, 그 회원 데이터 전부 삭제, 다른 회원은 그대로, CODEF 해제는 커밋 뒤 요청. */
	@Test
	void withdrawalDeletesTheMemberAndAllTheirData() {
		Session a = this.members.loginNew(MEMBER_A, EMAIL_A);
		Session otherDevice = this.members.login(this.members.anonymous(), EMAIL_A);
		Session b = this.members.loginNew(MEMBER_B, "b@example.com");
		assertThat(this.members.send(HttpMethod.POST, "/api/v1/me/cards", a, CARD).getStatusCode().value())
			.isEqualTo(201);
		assertThat(this.members.send(HttpMethod.POST, "/api/v1/me/cards", b, CARD).getStatusCode().value())
			.isEqualTo(201);
		connectCodef(MEMBER_A);
		// 실패 기록 1회가 남아 있어도 탈퇴와 함께 지워진다
		assertThat(this.members.deleteWithBody(ME, a, WRONG).getStatusCode().value()).isEqualTo(400);

		ResponseEntity<String> withdrawn = this.members.deleteWithBody(ME, a, CORRECT);

		assertThat(withdrawn.getStatusCode().value()).isEqualTo(204);
		assertNoStore(withdrawn);
		List<String> cookies = withdrawn.getHeaders().get(HttpHeaders.SET_COOKIE);
		assertThat(cookies).singleElement().satisfies(cookie -> assertThat(cookie).contains("Max-Age=0"));
		// 같은 세션 쿠키와 다른 기기 세션 모두 바로 401
		assertBody(this.members.send(HttpMethod.GET, ME, a, null), 401, UNAUTHENTICATED);
		assertBody(this.members.send(HttpMethod.GET, ME, otherDevice, null), 401, UNAUTHENTICATED);
		assertThat(count("SELECT COUNT(*) FROM members WHERE member_id = ?", MEMBER_A)).isZero();
		assertThat(count("SELECT COUNT(*) FROM users WHERE member_id = ?", MEMBER_A)).isZero();
		assertThat(count("SELECT COUNT(*) FROM auth_sessions WHERE member_id = ?", MEMBER_A)).isZero();
		assertThat(count("SELECT COUNT(*) FROM auth_tokens WHERE member_id = ?", MEMBER_A)).isZero();
		assertThat(count("SELECT COUNT(*) FROM auth_login_failures", null)).isZero();
		assertThat(count("SELECT COUNT(*) FROM codef_connections", null)).isZero();
		assertThat(RELEASED).containsExactly(CONNECTED_ID);
		// 다른 회원은 그대로다
		assertThat(this.members.send(HttpMethod.GET, ME, b, null).getBody()).contains("\"cardCount\":1");
		assertThat(count("SELECT COUNT(*) FROM user_cards c JOIN users u ON u.user_id = c.user_id"
				+ " WHERE u.member_id = ?", MEMBER_B)).isOne();
	}

	/** 탈퇴 뒤 같은 이메일로 다시 가입·인증·로그인할 수 있고, 새 장부는 비어 있다. 메일 요청 제한 60초는 남는다. */
	@Test
	void sameEmailCanSignUpAgainAfterWithdrawal() {
		Session anonymous = this.members.anonymous();
		String signUp = "{\"email\":\"" + EMAIL_A + "\",\"password\":\"" + PASSWORD + "\"}";
		assertThat(this.members.send(HttpMethod.POST, "/api/v1/auth/sign-up", anonymous, signUp)
			.getStatusCode()
			.value()).isEqualTo(202);
		verify(anonymous, this.mailSender.sentMails().get(0).token());
		Session a = this.members.login(anonymous, EMAIL_A);
		assertThat(this.members.send(HttpMethod.POST, "/api/v1/me/cards", a, CARD).getStatusCode().value())
			.isEqualTo(201);

		assertThat(this.members.deleteWithBody(ME, a, CORRECT).getStatusCode().value()).isEqualTo(204);

		Session again = this.members.anonymous();
		// 가입 메일을 보낸 지 60초가 지나지 않았으면 메일 요청 제한(남긴 표)으로 429다
		assertBody(this.members.send(HttpMethod.POST, "/api/v1/auth/sign-up", again, signUp), 429, RATE_LIMITED);
		this.clock.advance(Duration.ofSeconds(61));
		assertThat(this.members.send(HttpMethod.POST, "/api/v1/auth/sign-up", again, signUp)
			.getStatusCode()
			.value()).isEqualTo(202);
		verify(again, this.mailSender.sentMails().get(this.mailSender.sentMails().size() - 1).token());
		Session reborn = this.members.login(again, EMAIL_A);
		assertBody(this.members.send(HttpMethod.GET, ME, reborn, null), 200, """
				{"data":{"demoUser":null,"displayName":"내 카드","portfolioType":"REAL","readOnly":false,
				  "cardCount":0,"codefConnected":false}}
				""");
	}

	/** 401 → 403 → 본문 400 순서. 어느 실패도 지우지 않는다. */
	@Test
	void sessionCsrfAndBodyAreCheckedBeforeAnything() {
		Session a = this.members.loginNew(MEMBER_A, EMAIL_A);

		assertBody(this.members.deleteWithBody(ME, Session.NONE, CORRECT), 401, UNAUTHENTICATED);
		assertBody(this.members.deleteWithBody(ME, this.members.anonymous(), CORRECT), 401, UNAUTHENTICATED);
		assertBody(this.members.deleteWithBody(ME, a.withoutCsrf(), CORRECT), 403, CSRF_INVALID);
		for (String body : new String[] { "{}", "{\"password\":\"\"}", "{\"password\":\"" + PASSWORD + "\",\"x\":1}" }) {
			ResponseEntity<String> invalid = this.members.deleteWithBody(ME, a, body);
			assertThat(invalid.getStatusCode().value()).as(body).isEqualTo(400);
			assertThat(invalid.getBody()).as(body).contains("\"code\":\"INVALID_REQUEST\"");
		}
		// 본문 오류는 실패 제한에 세지 않는다
		assertThat(count("SELECT COUNT(*) FROM auth_login_failures", null)).isZero();
		assertThat(count("SELECT COUNT(*) FROM members WHERE member_id = ?", MEMBER_A)).isOne();
		assertThat(this.members.send(HttpMethod.GET, ME, a, null).getStatusCode().value()).isEqualTo(200);
	}

	/**
	 * 비밀번호 불일치는 세션 만료로 오인하지 않게 401이 아니라 400 {@code password} 필드 오류이고, 로그인 실패 제한(15분 5회)에 합산된다.
	 * 잠기면 맞는 비밀번호도 429이고 로그인도 429다.
	 */
	@Test
	void wrongPasswordIsAFieldErrorAndSharesTheLoginLockout() {
		Session a = this.members.loginNew(MEMBER_A, EMAIL_A);

		for (int i = 0; i < 5; i++) {
			assertBody(this.members.deleteWithBody(ME, a, WRONG), 400, PASSWORD_MISMATCH);
		}
		ResponseEntity<String> locked = this.members.deleteWithBody(ME, a, CORRECT);
		assertBody(locked, 429, RATE_LIMITED);
		assertThat(locked.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("900");
		assertBody(this.members.send(HttpMethod.POST, "/api/v1/auth/login", this.members.anonymous(),
				"{\"email\":\"" + EMAIL_A + "\",\"password\":\"" + PASSWORD + "\"}"), 429, RATE_LIMITED);
		// 세션은 유지되고 데이터도 그대로다
		assertThat(this.members.send(HttpMethod.GET, ME, a, null).getStatusCode().value()).isEqualTo(200);
		assertThat(count("SELECT COUNT(*) FROM members WHERE member_id = ?", MEMBER_A)).isOne();

		this.clock.advance(Duration.ofMinutes(15));
		assertThat(this.members.deleteWithBody(ME, a, CORRECT).getStatusCode().value()).isEqualTo(204);
	}

	/** CODEF 해제 요청이 실패해도 로컬 삭제는 끝난다. */
	@Test
	void codefReleaseFailureDoesNotBlockWithdrawal() {
		Session a = this.members.loginNew(MEMBER_A, EMAIL_A);
		connectCodef(MEMBER_A);
		releaseFails = true;

		assertThat(this.members.deleteWithBody(ME, a, CORRECT).getStatusCode().value()).isEqualTo(204);

		assertThat(RELEASED).containsExactly(CONNECTED_ID);
		assertThat(count("SELECT COUNT(*) FROM codef_connections", null)).isZero();
		assertThat(count("SELECT COUNT(*) FROM members WHERE member_id = ?", MEMBER_A)).isZero();
	}

	/**
	 * V17: 월간 상태가 CODEF 실적 참고값을 가리켜도(참고 스냅샷, ON DELETE SET NULL) 삭제 순서가 맞아 탈퇴된다. 참고값·동기화·월간 상태가
	 * 모두 지워진다.
	 */
	@Test
	void monthlyStateReferencingACodefPerformanceIsErasedToo() {
		Session a = this.members.loginNew(MEMBER_A, EMAIL_A);
		ResponseEntity<String> card = this.members.send(HttpMethod.POST, "/api/v1/me/cards", a, CARD);
		assertThat(card.getStatusCode().value()).isEqualTo(201);
		long userCardId = Long.parseLong(MemberApiTestClient.userCardId(card.getBody()).substring("uc-".length()));
		Long userId = this.jdbcTemplate.queryForObject("SELECT user_id FROM users WHERE member_id = ?", Long.class,
				MEMBER_A);
		this.jdbcTemplate.update("INSERT INTO codef_syncs (user_id, period_from, period_to, synced_at)"
				+ " VALUES (?, '2026-10-01', '2026-11-17', '2026-11-17 09:00:00')", userId);
		Long syncId = this.jdbcTemplate.queryForObject("SELECT MAX(sync_id) FROM codef_syncs WHERE user_id = ?",
				Long.class, userId);
		this.jdbcTemplate.update("INSERT INTO codef_performance_references (user_id, user_card_id, sync_id, card_name,"
				+ " period_from, period_to, current_use_won, fetched_at) VALUES (?, ?, ?, '청춘대로 톡톡카드',"
				+ " '2026-10-01', '2026-10-31', 312000, '2026-11-17 09:00:00')", userId, userCardId, syncId);
		Long referenceId = this.jdbcTemplate.queryForObject(
				"SELECT MAX(codef_performance_reference_id) FROM codef_performance_references WHERE user_id = ?",
				Long.class, userId);
		this.jdbcTemplate.update("INSERT INTO monthly_states (user_id, user_card_id, state_month, snapshot_at,"
				+ " previous_month_won, previous_month_from, previous_month_to, no_service_selection,"
				+ " previous_month_codef_reference_id, previous_month_codef_sync_id, previous_month_codef_won,"
				+ " previous_month_codef_from, previous_month_codef_to, previous_month_codef_fetched_at)"
				+ " VALUES (?, ?, '2026-11', '2026-11-18 10:00:00', 310000, '2026-10-01', '2026-10-31', FALSE,"
				+ " ?, ?, 312000, '2026-10-01', '2026-10-31', '2026-11-17 09:00:00')", userId, userCardId,
				referenceId, syncId);

		// 동기화별 불완전 행 건수(V18, #289)와 승인 합계 추정값 참고 기록도 함께 지워진다
		this.jdbcTemplate.update("UPDATE codef_syncs SET approvals_judged = TRUE WHERE sync_id = ?", syncId);
		this.jdbcTemplate.update("INSERT INTO codef_sync_incomplete_marks (sync_id, user_id, user_card_id, used_month,"
				+ " reason, row_count) VALUES (?, ?, ?, '2026-10', 'UNDETERMINED_ROW', 2), (?, ?, NULL, NULL,"
				+ " 'DUPLICATE_KEY_AMBIGUOUS', 1)", syncId, userId, userCardId, syncId, userId);
		this.jdbcTemplate.update("UPDATE monthly_states SET previous_month_inferred_won = 310000,"
				+ " previous_month_inferred_sync_id = ?, previous_month_inferred_computed_at = '2026-11-18 10:00:00'"
				+ " WHERE user_id = ?", syncId, userId);

		assertThat(this.members.deleteWithBody(ME, a, CORRECT).getStatusCode().value()).isEqualTo(204);

		for (String table : new String[] { "monthly_states", "codef_performance_references",
				"codef_sync_incomplete_marks", "codef_syncs", "user_cards" }) {
			assertThat(count("SELECT COUNT(*) FROM " + table + " WHERE user_id = " + userId, null)).as(table).isZero();
		}
		assertThat(count("SELECT COUNT(*) FROM members WHERE member_id = ?", MEMBER_A)).isZero();
	}

	/** 연결이 없으면 해제를 요청하지 않는다. */
	@Test
	void noCodefConnectionMeansNoRelease() {
		Session a = this.members.loginNew(MEMBER_A, EMAIL_A);

		assertThat(this.members.deleteWithBody(ME, a, CORRECT).getStatusCode().value()).isEqualTo(204);

		assertThat(RELEASED).isEmpty();
	}

	private void verify(Session anonymous, String token) {
		assertThat(this.members.send(HttpMethod.POST, "/api/v1/auth/email-verifications", anonymous,
				"{\"token\":\"" + token + "\"}")
			.getStatusCode()
			.value()).isEqualTo(204);
	}

	private void connectCodef(String memberId) {
		Long userId = this.jdbcTemplate.queryForObject("SELECT user_id FROM users WHERE member_id = ?", Long.class,
				memberId);
		this.jdbcTemplate.update(
				"INSERT INTO codef_connections (user_id, organization, connected_id_ciphertext, status, connected_at)"
						+ " VALUES (?, '0301', ?, 'CONNECTED', '2026-11-17 09:00:00')",
				userId, ConnectedIdCiphers.encrypt(KEY, userId, CONNECTED_ID));
	}

	private int count(String sql, String arg) {
		Integer value = (arg == null) ? this.jdbcTemplate.queryForObject(sql, Integer.class)
				: this.jdbcTemplate.queryForObject(sql, Integer.class, arg);
		return (value == null) ? 0 : value;
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class StubCodefConfiguration {

		@Bean
		CodefGateway recordingCodefGateway() {
			return new CodefGateway() {
				@Override
				public CodefRegistration register(String organization, String loginId, char[] password) {
					throw new UnsupportedOperationException("not used");
				}

				@Override
				public void release(String connectedId) {
					RELEASED.add(connectedId);
					if (releaseFails) {
						throw new IllegalStateException("synthetic release failure");
					}
				}
			};
		}

		@Bean
		@Primary
		ConnectedIdKeySource syntheticConnectedIdKey() {
			return () -> Optional.of(KEY.clone());
		}

	}

}
