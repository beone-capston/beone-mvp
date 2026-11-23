package com.beone.api.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.beone.api.auth.application.PasswordHasher;
import com.beone.api.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.web.client.RestClient;

/**
 * 회원 로그인·로그아웃·현재 회원 세션 조회를 실제 HTTP·MySQL로 확인한다. Flyway {@code V13} 적용, 로그인 때 세션 ID·CSRF 토큰
 * 교체와 이전 세션 폐기(병합 조건 ④), 미사용 7일·최대 30일·동시 5개, 로그아웃은 현재 세션만, 15분 5회 실패 잠금을 본다.
 *
 * <p>기대값은 docs/api/auth.md(인증 명세 사본 반영), D-24(미사용 7일·최대 30일·동시 5개), 2026-11-18 사용자 직접 결정(같은 이메일 15분
 * 안 5번 실패 시 15분 잠금, IP 기준 없음)이다. 시계는 주입한 가변 시계로 서울 2026-11-18 10:00에서 시작해 테스트가 앞으로 돌린다
 * (sleep 없음). 회원 행은 비밀번호 해시와 함께 직접 넣는다(가입·인증 흐름은 U2 시험이 본다).
 */
@IntegrationTest
@ActiveProfiles("local")
@Import(AuthSessionIntegrationTest.MutableClockConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class MemberSessionIntegrationTest {

	private static final Instant START = Instant.parse("2026-11-18T01:00:00Z");

	private static final String PASSWORD = "abcdefgh1!x";

	private static final String EMAIL = "member@example.com";

	private static final String MEMBER_ID = "usr-0123456789abcdef0123456789abcdef";

	private static final String INVALID_CREDENTIALS = "{\"code\":\"INVALID_CREDENTIALS\",\"message\":\"이메일 또는 비밀번호를 확인해 주세요.\",\"errors\":[]}";

	private static final String EMAIL_NOT_VERIFIED = "{\"code\":\"EMAIL_NOT_VERIFIED\",\"message\":\"이메일 인증을 완료해 주세요.\",\"errors\":[]}";

	private static final String UNAUTHENTICATED = "{\"code\":\"UNAUTHENTICATED\",\"message\":\"로그인이 필요합니다.\",\"errors\":[]}";

	private static final String RATE_LIMITED = "{\"code\":\"RATE_LIMITED\",\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.\",\"errors\":[]}";

	private static final String CSRF_INVALID = "{\"code\":\"CSRF_INVALID\",\"message\":\"요청을 확인할 수 없습니다. 화면을 새로고침한 후 다시 시도해 주세요.\",\"errors\":[]}";

	private static final String CLEARED_COOKIE = "__Host-BEONE_SESSION=; Path=/; Max-Age=0; Secure; HttpOnly; SameSite=Lax";

	/** 테스트 사이에 공유하는 해시(Argon2id 계산을 줄이려고 한 번만 만든다). */
	private static final String PASSWORD_HASH = new PasswordHasher().hash(PASSWORD);

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	AuthSessionIntegrationTest.MutableClock clock;

	@BeforeEach
	void setUp() {
		clearTables();
		this.clock.set(START);
	}

	@AfterEach
	void clearTables() {
		this.jdbcTemplate.update("DELETE FROM auth_login_failures");
		this.jdbcTemplate.update("DELETE FROM auth_login_throttles");
		this.jdbcTemplate.update("DELETE FROM auth_sessions");
		this.jdbcTemplate.update("DELETE FROM auth_tokens");
		this.jdbcTemplate.update("DELETE FROM members");
		this.jdbcTemplate.update("DELETE FROM auth_mail_throttles");
	}

	@Test
	void memberSessionMigrationIsApplied() {
		assertThat(this.jdbcTemplate.queryForObject("SELECT success FROM flyway_schema_history WHERE version = '13'",
				Boolean.class))
			.isTrue();
	}

	/** 병합 조건 ④: 로그인하면 세션 ID와 CSRF 토큰이 바뀌고 이전 익명 세션은 폐기된다. */
	@Test
	void loginReplacesSessionIdAndCsrfTokenAndDiscardsAnonymousSession() throws Exception {
		createMember(MEMBER_ID, EMAIL, true);
		Session anonymous = anonymous();

		ResponseEntity<String> response = login(anonymous, " Member@Example.COM ", PASSWORD);

		assertBody(response, 200, "{\"data\":{\"userId\":\"" + MEMBER_ID + "\",\"email\":\"" + EMAIL + "\"}}");
		assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
		List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
		assertThat(cookies).hasSize(1);
		String member = cookieValue(cookies.get(0));
		assertThat(cookies.get(0))
			.isEqualTo("__Host-BEONE_SESSION=" + member + "; Path=/; Max-Age=2592000; Secure; HttpOnly; SameSite=Lax");
		assertThat(member).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(anonymous.id());
		assertThat(response.getBody()).doesNotContain(member).doesNotContain(anonymous.id());

		// 서버: 익명 세션 행은 없어지고 회원 세션 행만 해시로 남는다.
		assertThat(sessionRowCount(anonymous.id())).isZero();
		Map<String, Object> row = this.jdbcTemplate.queryForMap("SELECT * FROM auth_sessions");
		assertThat(row.get("session_hash")).isEqualTo(sha256Hex(member));
		assertThat(row.get("member_id")).isEqualTo(MEMBER_ID);
		assertThat(row.get("created_at").toString()).startsWith("2026-11-18T10:00");
		assertThat(row.get("idle_expires_at").toString()).startsWith("2026-11-25T10:00");
		assertThat(row.get("absolute_expires_at").toString()).startsWith("2026-12-18T10:00");

		// 새 세션의 CSRF 토큰은 이전 토큰과 다르다.
		String memberCsrf = csrfOf(member);
		assertThat(memberCsrf).isNotEqualTo(anonymous.csrf());

		// 이전 세션 쿠키·토큰은 더 쓸 수 없다.
		assertBody(logout(new Session(anonymous.id(), anonymous.csrf())), 403, CSRF_INVALID);
		assertBody(logout(new Session(member, anonymous.csrf())), 403, CSRF_INVALID);
		ResponseEntity<String> reissued = getCsrf(anonymous.id());
		assertThat(cookieValue(reissued.getHeaders().getFirst(HttpHeaders.SET_COOKIE))).isNotEqualTo(anonymous.id());
		assertThat(sessionRowCount(anonymous.id())).isZero();

		// 회원 세션에서 다시 로그인해도 세션 ID를 바꾸고 이전 회원 세션을 지운다.
		ResponseEntity<String> again = login(new Session(member, memberCsrf), EMAIL, PASSWORD);
		assertThat(again.getStatusCode().value()).isEqualTo(200);
		String next = cookieValue(again.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
		assertThat(next).isNotEqualTo(member);
		assertThat(sessionRowCount(member)).isZero();
		assertThat(csrfOf(next)).isNotEqualTo(memberCsrf);
		assertBody(getSession(member), 401, UNAUTHENTICATED);
	}

	@Test
	void sessionReportsTimesAndSlidesSevenDayIdleExpiry() throws Exception {
		createMember(MEMBER_ID, EMAIL, true);
		String member = loginAs(EMAIL);

		ResponseEntity<String> first = getSession(member);
		assertBody(first, 200, sessionBody("2026-11-18T10:00:00+09:00", "2026-11-25T10:00:00+09:00"));
		assertThat(first.getHeaders().getCacheControl()).isEqualTo("no-store");
		assertThat(first.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();

		this.clock.advance(Duration.ofDays(7).minusSeconds(1));
		assertBody(getSession(member), 200, sessionBody("2026-11-18T10:00:00+09:00", "2026-12-02T09:59:59+09:00"));

		this.clock.advance(Duration.ofDays(7));
		assertBody(getSession(member), 401, UNAUTHENTICATED);
		assertThat(sessionRowCount(member)).isZero();
	}

	@Test
	void memberSessionEndsThirtyDaysAfterLoginEvenWhenUsed() throws Exception {
		createMember(MEMBER_ID, EMAIL, true);
		String member = loginAs(EMAIL);

		for (int day = 6; day <= 24; day += 6) {
			this.clock.advance(Duration.ofDays(6));
			assertThat(getSession(member).getStatusCode().value()).as("day " + day).isEqualTo(200);
		}
		// 24일째 사용 뒤 미사용 만료는 31일째지만 절대 만료(30일째)를 넘지 않는다.
		this.clock.advance(Duration.ofDays(6).minusSeconds(1));
		assertBody(getSession(member), 200, sessionBody("2026-11-18T10:00:00+09:00", "2026-12-18T10:00:00+09:00"));

		this.clock.advance(Duration.ofSeconds(1));
		assertBody(getSession(member), 401, UNAUTHENTICATED);
	}

	@Test
	void anonymousOrMissingSessionIsNotAMemberSession() throws Exception {
		Session anonymous = anonymous();
		Object lastUsed = lastUsedAt(anonymous.id());
		this.clock.advance(Duration.ofMinutes(5));

		assertBody(getSession(anonymous.id()), 401, UNAUTHENTICATED);
		assertBody(getSession(null), 401, UNAUTHENTICATED);
		assertBody(getSession("not-a-session"), 401, UNAUTHENTICATED);
		// 익명 세션은 세션 조회로 사용 시각이 바뀌지 않는다.
		assertThat(lastUsedAt(anonymous.id())).isEqualTo(lastUsed);
	}

	@Test
	void sixthLoginEndsTheLeastRecentlyUsedSession() throws Exception {
		createMember(MEMBER_ID, EMAIL, true);
		List<String> sessions = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			sessions.add(loginAs(EMAIL));
			this.clock.advance(Duration.ofMinutes(1));
		}
		// 첫 세션을 다시 써서, 가장 오래 쓰지 않은 세션을 두 번째로 만든다.
		assertThat(getSession(sessions.get(0)).getStatusCode().value()).isEqualTo(200);
		Session unrelatedAnonymous = anonymous();
		this.clock.advance(Duration.ofMinutes(1));

		String sixth = loginAs(EMAIL);

		assertBody(getSession(sessions.get(1)), 401, UNAUTHENTICATED);
		for (String alive : List.of(sessions.get(0), sessions.get(2), sessions.get(3), sessions.get(4), sixth)) {
			assertThat(getSession(alive).getStatusCode().value()).isEqualTo(200);
		}
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_sessions WHERE member_id = ?",
				Integer.class, MEMBER_ID))
			.isEqualTo(5);
		// 익명 세션은 회원 동시 세션 수에 넣지 않으며 그대로 남는다.
		assertThat(sessionRowCount(unrelatedAnonymous.id())).isEqualTo(1);
	}

	@Test
	void logoutEndsOnlyTheCurrentSession() throws Exception {
		createMember(MEMBER_ID, EMAIL, true);
		String phone = loginAs(EMAIL);
		String laptop = loginAs(EMAIL);
		Session phoneSession = new Session(phone, csrfOf(phone));

		ResponseEntity<String> response = logout(phoneSession);

		assertThat(response.getStatusCode().value()).isEqualTo(204);
		assertThat(response.getBody()).isNull();
		assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).containsExactly(CLEARED_COOKIE);
		assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
		assertBody(getSession(phone), 401, UNAUTHENTICATED);
		assertThat(getSession(laptop).getStatusCode().value()).isEqualTo(200);
		assertThat(sessionRowCount(phone)).isZero();

		// 무효화된 이전 토큰을 다시 쓰면 403, 새로 받은 익명 세션의 토큰이면 이미 로그아웃된 상태여도 204다.
		assertBody(logout(phoneSession), 403, CSRF_INVALID);
		assertThat(logout(anonymous()).getStatusCode().value()).isEqualTo(204);
	}

	@Test
	void missingAccountAndWrongPasswordAreIndistinguishable() throws Exception {
		createMember(MEMBER_ID, EMAIL, true);
		createMember("usr-fedcba9876543210fedcba9876543210", "pending@example.com", false);
		Session anonymous = anonymous();

		ResponseEntity<String> missing = login(anonymous, "nobody@example.com", PASSWORD);
		ResponseEntity<String> wrong = login(anonymous, EMAIL, "abcdefgh1!y");
		ResponseEntity<String> pendingWrong = login(anonymous, "pending@example.com", "abcdefgh1!y");

		assertBody(missing, 401, INVALID_CREDENTIALS);
		assertBody(wrong, 401, INVALID_CREDENTIALS);
		assertBody(pendingWrong, 401, INVALID_CREDENTIALS);
		// 미인증 오류는 비밀번호가 맞은 뒤에만 준다.
		assertBody(login(anonymous, "pending@example.com", PASSWORD), 403, EMAIL_NOT_VERIFIED);
		// 실패한 로그인은 회원 세션을 만들지 않고 익명 세션을 그대로 둔다.
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_sessions WHERE member_id IS NOT NULL",
				Integer.class))
			.isZero();
		assertThat(sessionRowCount(anonymous.id())).isEqualTo(1);
	}

	@Test
	void fiveFailuresWithinFifteenMinutesLockTheEmailForFifteenMinutes() throws Exception {
		createMember(MEMBER_ID, EMAIL, true);
		Session anonymous = anonymous();
		for (int i = 0; i < 5; i++) {
			assertBody(login(anonymous, EMAIL, "abcdefgh1!y"), 401, INVALID_CREDENTIALS);
			if (i < 4) {
				this.clock.advance(Duration.ofMinutes(1));
			}
		}

		// 다섯 번째 실패(10:04)부터 15분 동안은 맞는 비밀번호도 429다.
		ResponseEntity<String> locked = login(anonymous, EMAIL, PASSWORD);
		assertBody(locked, 429, RATE_LIMITED);
		assertThat(locked.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("900");

		this.clock.advance(Duration.ofMinutes(15).minusSeconds(1));
		ResponseEntity<String> stillLocked = login(anonymous, EMAIL, "abcdefgh1!y");
		assertBody(stillLocked, 429, RATE_LIMITED);
		assertThat(stillLocked.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");

		// 잠긴 동안의 시도는 세지 않으므로, 풀린 뒤 한 번 틀려도 다시 잠기지 않는다.
		this.clock.advance(Duration.ofSeconds(1));
		assertBody(login(anonymous, EMAIL, "abcdefgh1!y"), 401, INVALID_CREDENTIALS);
		assertThat(login(anonymous, EMAIL, PASSWORD).getStatusCode().value()).isEqualTo(200);
	}

	@Test
	void failuresOlderThanFifteenMinutesAreNotCounted() throws Exception {
		createMember(MEMBER_ID, EMAIL, true);
		Session anonymous = anonymous();
		for (int i = 0; i < 4; i++) {
			assertBody(login(anonymous, EMAIL, "abcdefgh1!y"), 401, INVALID_CREDENTIALS);
			this.clock.advance(Duration.ofMinutes(1));
		}
		// 10:15: 10:00 실패는 정확히 15분 전이라 세지 않는다(10:01·10:02·10:03·10:15 = 4번).
		this.clock.set(START.plus(Duration.ofMinutes(15)));
		assertBody(login(anonymous, EMAIL, "abcdefgh1!y"), 401, INVALID_CREDENTIALS);
		assertThat(login(anonymous(), EMAIL, PASSWORD).getStatusCode().value()).isEqualTo(200);
	}

	@Test
	void successfulLoginClearsEarlierFailures() throws Exception {
		createMember(MEMBER_ID, EMAIL, true);
		Session anonymous = anonymous();
		for (int i = 0; i < 4; i++) {
			assertBody(login(anonymous, EMAIL, "abcdefgh1!y"), 401, INVALID_CREDENTIALS);
		}
		assertThat(login(anonymous, EMAIL, PASSWORD).getStatusCode().value()).isEqualTo(200);

		Session next = anonymous();
		for (int i = 0; i < 4; i++) {
			assertBody(login(next, EMAIL, "abcdefgh1!y"), 401, INVALID_CREDENTIALS);
		}
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_login_failures", Integer.class))
			.isEqualTo(4);
		assertBody(login(next, EMAIL, "abcdefgh1!y"), 401, INVALID_CREDENTIALS);
		assertBody(login(next, EMAIL, PASSWORD), 429, RATE_LIMITED);
	}

	@Test
	void missingAccountIsLockedTheSameWayAndOnlyHashesAreStored() throws Exception {
		Session anonymous = anonymous();
		for (int i = 0; i < 5; i++) {
			assertBody(login(anonymous, " Nobody@Example.com", PASSWORD), 401, INVALID_CREDENTIALS);
		}
		ResponseEntity<String> locked = login(anonymous, "nobody@example.com", PASSWORD);
		assertBody(locked, 429, RATE_LIMITED);
		assertThat(locked.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("900");

		assertThat(this.jdbcTemplate.queryForList("SELECT email_hash FROM auth_login_throttles", String.class))
			.containsExactly(sha256Hex("nobody@example.com"));
		assertThat(allTableText()).doesNotContain("nobody@example.com").doesNotContain(PASSWORD);
	}

	@Test
	void concurrentWrongLoginsForSameEmailLockAfterExactlyFive() throws Exception {
		createMember(MEMBER_ID, EMAIL, true);
		Session anonymous = anonymous();
		int attempts = 7;
		ExecutorService executor = Executors.newFixedThreadPool(attempts);
		try {
			CountDownLatch ready = new CountDownLatch(attempts);
			List<Future<Integer>> results = new ArrayList<>();
			for (int i = 0; i < attempts; i++) {
				results.add(executor.submit((Callable<Integer>) () -> {
					ready.countDown();
					ready.await(10, TimeUnit.SECONDS);
					return login(anonymous, EMAIL, "abcdefgh1!y").getStatusCode().value();
				}));
			}
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> result : results) {
				statuses.add(result.get(60, TimeUnit.SECONDS));
			}
			// 같은 이메일의 로그인은 잠금 행으로 차례로 판정되어 정확히 5번째 실패에서 잠긴다(교착 500 없음).
			assertThat(statuses).containsOnly(401, 429);
			assertThat(statuses.stream().filter(status -> status == 401).count()).isEqualTo(5);
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void concurrentLoginsNeverExceedFiveSessions() throws Exception {
		createMember(MEMBER_ID, EMAIL, true);
		List<Session> anonymousSessions = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			anonymousSessions.add(anonymous());
		}
		ExecutorService executor = Executors.newFixedThreadPool(anonymousSessions.size());
		try {
			CountDownLatch ready = new CountDownLatch(anonymousSessions.size());
			List<Future<Integer>> results = new ArrayList<>();
			for (Session session : anonymousSessions) {
				results.add(executor.submit((Callable<Integer>) () -> {
					ready.countDown();
					ready.await(10, TimeUnit.SECONDS);
					return login(session, EMAIL, PASSWORD).getStatusCode().value();
				}));
			}
			for (Future<Integer> result : results) {
				assertThat(result.get(60, TimeUnit.SECONDS)).isEqualTo(200);
			}
		}
		finally {
			executor.shutdownNow();
		}
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_sessions WHERE member_id = ?",
				Integer.class, MEMBER_ID))
			.isEqualTo(5);
	}

	@Test
	void concurrentLoginsOfDifferentMembersNeverFailWithServerError() throws Exception {
		int members = 8;
		List<Session> anonymousSessions = new ArrayList<>();
		for (int i = 0; i < members; i++) {
			createMember(String.format("usr-%032x", i + 1), "m" + i + "@example.com", true);
			anonymousSessions.add(anonymous());
		}
		ExecutorService executor = Executors.newFixedThreadPool(members);
		try {
			for (int round = 0; round < 3; round++) {
				CountDownLatch ready = new CountDownLatch(members);
				List<Future<Integer>> results = new ArrayList<>();
				for (int i = 0; i < members; i++) {
					Session session = anonymousSessions.get(i);
					String email = "m" + i + "@example.com";
					results.add(executor.submit((Callable<Integer>) () -> {
						ready.countDown();
						ready.await(10, TimeUnit.SECONDS);
						return login(session, email, PASSWORD).getStatusCode().value();
					}));
				}
				for (int i = 0; i < members; i++) {
					// 회원마다 세션이 없던 상태에서 동시에 로그인해도 교착(500) 없이 모두 성공한다.
					assertThat(results.get(i).get(60, TimeUnit.SECONDS)).as("round " + round + " member " + i)
						.isEqualTo(200);
				}
				anonymousSessions.replaceAll(ignored -> anonymous());
				this.jdbcTemplate.update("DELETE FROM auth_sessions WHERE member_id IS NOT NULL");
			}
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void crossAccountSwitchesNeverFailWithServerError() throws Exception {
		String otherId = "usr-fedcba9876543210fedcba9876543210";
		createMember(MEMBER_ID, EMAIL, true);
		createMember(otherId, "other@example.com", true);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			for (int round = 0; round < 15; round++) {
				// A의 세션으로 B에 로그인하고, 동시에 B의 세션으로 A에 로그인한다(서로 상대 회원의 세션을 지우고 잠근다).
				String a = loginAs(EMAIL);
				String b = loginAs("other@example.com");
				Session fromA = new Session(a, csrfOf(a));
				Session fromB = new Session(b, csrfOf(b));
				CountDownLatch ready = new CountDownLatch(2);
				Future<ResponseEntity<String>> toB = executor.submit((Callable<ResponseEntity<String>>) () -> {
					ready.countDown();
					ready.await(10, TimeUnit.SECONDS);
					return login(fromA, "other@example.com", PASSWORD);
				});
				Future<ResponseEntity<String>> toA = executor.submit((Callable<ResponseEntity<String>>) () -> {
					ready.countDown();
					ready.await(10, TimeUnit.SECONDS);
					return login(fromB, EMAIL, PASSWORD);
				});
				assertSwitchedTo(toB.get(60, TimeUnit.SECONDS), otherId, "round " + round + " A->B");
				assertSwitchedTo(toA.get(60, TimeUnit.SECONDS), MEMBER_ID, "round " + round + " B->A");
				assertThat(sessionRowCount(a)).isZero();
				assertThat(sessionRowCount(b)).isZero();
			}
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void loginAndLogoutWithoutCsrfAreForbiddenAndChangeNothing() throws Exception {
		createMember(MEMBER_ID, EMAIL, true);
		Session anonymous = anonymous();

		assertBody(login(new Session(anonymous.id(), null), EMAIL, PASSWORD), 403, CSRF_INVALID);
		assertBody(login(new Session(null, anonymous.csrf()), EMAIL, PASSWORD), 403, CSRF_INVALID);
		assertBody(logout(new Session(anonymous.id(), null)), 403, CSRF_INVALID);

		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_sessions", Integer.class)).isEqualTo(1);
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_login_throttles", Integer.class))
			.isZero();
	}

	@Test
	void passwordsSessionIdsAndTokensAreNotLogged(CapturedOutput output) throws Exception {
		createMember(MEMBER_ID, EMAIL, true);
		Session anonymous = anonymous();
		login(anonymous, EMAIL, "abcdefgh1!y");
		ResponseEntity<String> response = login(anonymous, EMAIL, PASSWORD);
		String member = cookieValue(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
		String memberCsrf = csrfOf(member);
		getSession(member);
		logout(new Session(member, memberCsrf));

		assertThat(output.getAll()).doesNotContain(PASSWORD)
			.doesNotContain("abcdefgh1!y")
			.doesNotContain(anonymous.id())
			.doesNotContain(anonymous.csrf())
			.doesNotContain(member)
			.doesNotContain(memberCsrf);
		assertThat(allTableText()).doesNotContain(member).doesNotContain(anonymous.id());
	}

	private record Session(String id, String csrf) {
	}

	/** 전환 로그인이 200이고, 응답과 새 쿠키로 조회한 세션이 모두 대상 회원인지 본다. */
	private void assertSwitchedTo(ResponseEntity<String> response, String memberId, String description) {
		assertThat(response.getStatusCode().value()).as(description).isEqualTo(200);
		assertThat(response.getBody()).as(description).contains("\"userId\":\"" + memberId + "\"");
		String newSession = cookieValue(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
		ResponseEntity<String> session = getSession(newSession);
		assertThat(session.getStatusCode().value()).as(description).isEqualTo(200);
		assertThat(session.getBody()).as(description).contains("\"userId\":\"" + memberId + "\"");
	}

	private void createMember(String memberId, String email, boolean verified) {
		this.jdbcTemplate.update(
				"INSERT INTO members (member_id, email, password_hash, email_verified_at, created_at) VALUES (?, ?, ?, ?, ?)",
				memberId, email, PASSWORD_HASH, verified ? "2026-11-17 09:00:00" : null, "2026-11-17 09:00:00");
	}

	/** 새 익명 세션을 받고 그 세션으로 로그인해 회원 세션 ID를 돌려준다. */
	private String loginAs(String email) throws Exception {
		ResponseEntity<String> response = login(anonymous(), email, PASSWORD);
		assertThat(response.getStatusCode().value()).isEqualTo(200);
		return cookieValue(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
	}

	private Session anonymous() {
		ResponseEntity<String> issued = getCsrf(null);
		return new Session(cookieValue(issued.getHeaders().getFirst(HttpHeaders.SET_COOKIE)), token(issued.getBody()));
	}

	private String csrfOf(String sessionId) {
		ResponseEntity<String> response = getCsrf(sessionId);
		assertThat(cookieValue(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE))).isEqualTo(sessionId);
		return token(response.getBody());
	}

	private ResponseEntity<String> getCsrf(String sessionId) {
		RestClient.RequestHeadersSpec<?> request = client().get().uri("/api/v1/auth/csrf");
		if (sessionId != null) {
			request = request.header(HttpHeaders.COOKIE, "__Host-BEONE_SESSION=" + sessionId);
		}
		return request.retrieve().toEntity(String.class);
	}

	private ResponseEntity<String> getSession(String sessionId) {
		RestClient.RequestHeadersSpec<?> request = client().get().uri("/api/v1/auth/session");
		if (sessionId != null) {
			request = request.header(HttpHeaders.COOKIE, "__Host-BEONE_SESSION=" + sessionId);
		}
		return request.retrieve().toEntity(String.class);
	}

	private ResponseEntity<String> login(Session session, String email, String password) {
		return post("/api/v1/auth/login", session, "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
	}

	private ResponseEntity<String> logout(Session session) {
		return post("/api/v1/auth/logout", session, null);
	}

	private ResponseEntity<String> post(String path, Session session, String body) {
		RestClient.RequestBodySpec request = client().post().uri(path);
		if (session.id() != null) {
			request = request.header(HttpHeaders.COOKIE, "__Host-BEONE_SESSION=" + session.id());
		}
		if (session.csrf() != null) {
			request = request.header("X-CSRF-TOKEN", session.csrf());
		}
		if (body != null) {
			request = request.contentType(MediaType.APPLICATION_JSON).body(body);
		}
		return request.retrieve().toEntity(String.class);
	}

	private int sessionRowCount(String sessionId) throws Exception {
		return this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_sessions WHERE session_hash = ?",
				Integer.class, sha256Hex(sessionId));
	}

	private Object lastUsedAt(String sessionId) throws Exception {
		return this.jdbcTemplate.queryForObject("SELECT last_used_at FROM auth_sessions WHERE session_hash = ?",
				Object.class, sha256Hex(sessionId));
	}

	private String allTableText() {
		StringBuilder text = new StringBuilder();
		for (String table : List.of("members", "auth_sessions", "auth_login_throttles", "auth_login_failures")) {
			this.jdbcTemplate.queryForList("SELECT * FROM " + table).forEach(row -> text.append(row.values()));
		}
		return text.toString();
	}

	private static String sessionBody(String authenticatedAt, String expiresAt) {
		return "{\"data\":{\"userId\":\"" + MEMBER_ID + "\",\"email\":\"" + EMAIL + "\",\"authenticatedAt\":\""
				+ authenticatedAt + "\",\"expiresAt\":\"" + expiresAt
				+ "\",\"absoluteExpiresAt\":\"2026-12-18T10:00:00+09:00\"}}";
	}

	private static String cookieValue(String setCookie) {
		return setCookie.substring("__Host-BEONE_SESSION=".length(), setCookie.indexOf(';'));
	}

	private static String token(String body) {
		int start = body.indexOf("\"token\":\"") + "\"token\":\"".length();
		return body.substring(start, body.indexOf('"', start));
	}

	private RestClient client() {
		return RestClient.builder()
			.baseUrl("http://localhost:" + this.port)
			.defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
			})
			.build();
	}

	private static void assertBody(ResponseEntity<String> response, int status, String expected) throws Exception {
		assertThat(response.getStatusCode().value()).isEqualTo(status);
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch(expected, response.getBody());
	}

	private static String sha256Hex(String value) throws Exception {
		return HexFormat.of()
			.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
	}

}
