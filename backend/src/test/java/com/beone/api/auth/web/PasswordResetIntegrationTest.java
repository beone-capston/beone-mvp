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

import com.beone.api.auth.application.AuthMail;
import com.beone.api.auth.application.FakeAuthMailSender;
import com.beone.api.auth.application.PasswordHasher;
import com.beone.api.auth.persistence.AuthTokenPurpose;
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
 * 비밀번호 재설정 요청·완료를 실제 HTTP·MySQL로 확인한다. 재설정 토큰 15분·1회용·재발급 무효·해시만 저장, 다른 목적 토큰 거부,
 * 가입·인증메일 재요청과의 60초 합산 제한, 계정 존재 비공개, 완료 시 그 회원의 모든 세션 종료(D-24)와 동시 로그인 경쟁을 본다.
 *
 * <p>기대값은 docs/api/auth.md(인증 명세 사본 반영)와 관리자 판단(결정 28: 미인증 계정에도 재설정 메일 발송, 로그인 실패 잠금은 건드리지
 * 않음)이다. 시계는 주입한 가변 시계로 서울 2026-11-18 10:00에서 시작해 테스트가 앞으로 돌린다(sleep 없음). 매 요청은
 * {@code GET /api/v1/auth/csrf}로 받은 실제 세션·CSRF 토큰을 쓴다.
 */
@IntegrationTest
@ActiveProfiles("local")
@Import(AuthSessionIntegrationTest.MutableClockConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class PasswordResetIntegrationTest {

	private static final Instant START = Instant.parse("2026-11-18T01:00:00Z");

	private static final String ACCEPTED = "{\"data\":{\"message\":\"처리 가능한 요청이면 이메일로 안내합니다.\"}}";

	private static final String RATE_LIMITED = "{\"code\":\"RATE_LIMITED\",\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.\",\"errors\":[]}";

	private static final String TOKEN_INVALID = "{\"code\":\"AUTH_TOKEN_INVALID\",\"message\":\"링크가 유효하지 않거나 만료되었습니다. 다시 요청해 주세요.\",\"errors\":[]}";

	private static final String INVALID_CREDENTIALS = "{\"code\":\"INVALID_CREDENTIALS\",\"message\":\"이메일 또는 비밀번호를 확인해 주세요.\",\"errors\":[]}";

	private static final String EMAIL_NOT_VERIFIED = "{\"code\":\"EMAIL_NOT_VERIFIED\",\"message\":\"이메일 인증을 완료해 주세요.\",\"errors\":[]}";

	private static final String OLD_PASSWORD = "abcdefgh1!x";

	private static final String NEW_PASSWORD = "zyxwvuts9?q";

	private static final String EMAIL = "member@example.com";

	private static final String MEMBER_ID = "usr-0123456789abcdef0123456789abcdef";

	private static final String OTHER_ID = "usr-fedcba9876543210fedcba9876543210";

	/** 테스트 사이에 공유하는 이전 비밀번호 해시(Argon2id 계산을 줄이려고 한 번만 만든다). */
	private static final String OLD_PASSWORD_HASH = new PasswordHasher().hash(OLD_PASSWORD);

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	AuthSessionIntegrationTest.MutableClock clock;

	@Autowired
	FakeAuthMailSender mailSender;

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
		this.mailSender.clear();
	}

	@Test
	void resetRequestMailsAResetTokenAndStoresOnlyItsHash() throws Exception {
		createMember(MEMBER_ID, EMAIL, true);

		ResponseEntity<String> response = requestReset(anonymous(), " Member@Example.COM ");

		assertBody(response, 202, ACCEPTED);
		assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
		AuthMail mail = onlyMail();
		assertThat(mail.to()).isEqualTo(EMAIL);
		assertThat(mail.purpose()).isEqualTo(AuthTokenPurpose.PASSWORD_RESET);
		assertThat(mail.token()).matches("[A-Za-z0-9_-]{43}");
		Map<String, Object> token = this.jdbcTemplate.queryForMap("SELECT * FROM auth_tokens");
		assertThat(token.get("member_id")).isEqualTo(MEMBER_ID);
		assertThat(token.get("purpose")).isEqualTo("PASSWORD_RESET");
		assertThat(token.get("token_hash")).isEqualTo(sha256Hex(mail.token()));
		assertThat(token.get("created_at").toString()).isEqualTo("2026-11-18T10:00");
		assertThat(token.get("expires_at").toString()).isEqualTo("2026-11-18T10:15");
		assertThat(allTableText()).doesNotContain(mail.token());
		// 요청만으로는 비밀번호·세션이 바뀌지 않는다.
		assertThat(passwordHashOf(MEMBER_ID)).isEqualTo(OLD_PASSWORD_HASH);
	}

	/** 계정 없음·미인증·인증 계정의 응답이 같다. 미인증 계정에도 재설정 메일을 보낸다(관리자 판단, 결정 28). */
	@Test
	void responsesAreIdenticalForVerifiedUnverifiedAndMissingAccounts() throws Exception {
		createMember(MEMBER_ID, EMAIL, true);
		createMember(OTHER_ID, "unverified@example.com", false);
		Session session = anonymous();

		List<ResponseEntity<String>> responses = List.of(requestReset(session, EMAIL),
				requestReset(session, "unverified@example.com"), requestReset(session, "nobody@example.com"));

		for (ResponseEntity<String> response : responses) {
			assertBody(response, 202, ACCEPTED);
			assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
			assertThat(response.getHeaders().containsHeader(HttpHeaders.RETRY_AFTER)).isFalse();
		}
		assertThat(this.mailSender.sentMails()).extracting(AuthMail::to)
			.containsExactly(EMAIL, "unverified@example.com");
		assertThat(this.mailSender.sentMails()).extracting(AuthMail::purpose)
			.containsOnly(AuthTokenPurpose.PASSWORD_RESET);
		// 없는 주소는 원문으로 남기지 않는다(60초 제한 행도 해시만).
		assertThat(allTableText()).doesNotContain("nobody@example.com");
		assertThat(this.jdbcTemplate.queryForList("SELECT email_hash FROM auth_mail_throttles", String.class))
			.contains(sha256Hex("nobody@example.com"));
	}

	/** D-24: 완료하면 비밀번호가 바뀌고 그 회원의 모든 세션이 끝난다. 다른 회원 세션과 이메일 인증 상태는 그대로다. */
	@Test
	void resetChangesPasswordAndEndsEveryMemberSessionOnly() throws Exception {
		createMember(MEMBER_ID, EMAIL, true);
		createMember(OTHER_ID, "other@example.com", true);
		List<String> memberSessions = List.of(loginAs(EMAIL, OLD_PASSWORD), loginAs(EMAIL, OLD_PASSWORD),
				loginAs(EMAIL, OLD_PASSWORD));
		String otherSession = loginAs("other@example.com", OLD_PASSWORD);
		Session requester = anonymous();
		requestReset(requester, EMAIL);
		String token = onlyMail().token();
		Object verifiedAt = this.jdbcTemplate.queryForObject("SELECT email_verified_at FROM members WHERE member_id = ?",
				Object.class, MEMBER_ID);
		this.clock.advance(Duration.ofMinutes(5));

		ResponseEntity<String> response = reset(requester, token, NEW_PASSWORD);

		assertThat(response.getStatusCode().value()).isEqualTo(204);
		assertThat(response.getBody()).isNull();
		assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
		String newHash = passwordHashOf(MEMBER_ID);
		assertThat(newHash).startsWith("$argon2id$").isNotEqualTo(OLD_PASSWORD_HASH);
		assertThat(newHash).doesNotContain(NEW_PASSWORD);
		for (String sessionId : memberSessions) {
			assertThat(getSession(sessionId).getStatusCode().value()).isEqualTo(401);
		}
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_sessions WHERE member_id = ?",
				Integer.class, MEMBER_ID))
			.isZero();
		assertThat(getSession(otherSession).getStatusCode().value()).isEqualTo(200);
		// 자동 로그인하지 않는다: 요청한 익명 세션은 회원 세션이 되지 않는다.
		assertThat(getSession(requester.id()).getStatusCode().value()).isEqualTo(401);
		assertThat(this.jdbcTemplate.queryForObject("SELECT email_verified_at FROM members WHERE member_id = ?",
				Object.class, MEMBER_ID))
			.isEqualTo(verifiedAt);
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_tokens", Integer.class)).isZero();

		assertBody(login(anonymous(), EMAIL, OLD_PASSWORD), 401, INVALID_CREDENTIALS);
		assertThat(login(anonymous(), EMAIL, NEW_PASSWORD).getStatusCode().value()).isEqualTo(200);
	}

	@Test
	void resetDoesNotVerifyAnUnverifiedAccount() throws Exception {
		createMember(MEMBER_ID, EMAIL, false);
		Session session = anonymous();
		requestReset(session, EMAIL);

		assertThat(reset(session, onlyMail().token(), NEW_PASSWORD).getStatusCode().value()).isEqualTo(204);

		assertThat(this.jdbcTemplate.queryForObject("SELECT email_verified_at FROM members", Object.class)).isNull();
		assertBody(login(anonymous(), EMAIL, NEW_PASSWORD), 403, EMAIL_NOT_VERIFIED);
	}

	@Test
	void resetTokenIsSingleUse() throws Exception {
		createMember(MEMBER_ID, EMAIL, true);
		Session session = anonymous();
		requestReset(session, EMAIL);
		String token = onlyMail().token();
		assertThat(reset(session, token, NEW_PASSWORD).getStatusCode().value()).isEqualTo(204);
		String hashAfterFirst = passwordHashOf(MEMBER_ID);

		assertBody(reset(session, token, "another1!pass"), 400, TOKEN_INVALID);

		assertThat(passwordHashOf(MEMBER_ID)).isEqualTo(hashAfterFirst);
	}

	/** 1회용은 동시 사용에도 지켜진다: 같은 토큰을 겹쳐 보내면 정확히 하나만 바꾸고 저장된 비밀번호는 그 요청의 값이다. */
	@Test
	void concurrentUsesOfTheSameTokenSucceedExactlyOnce() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			for (int round = 0; round < 5; round++) {
				String memberId = "usr-%032d".formatted(round);
				String email = "twice" + round + "@example.com";
				createMember(memberId, email, true);
				this.mailSender.clear();
				assertBody(requestReset(anonymous(), email), 202, ACCEPTED);
				String token = onlyMail().token();
				Session first = anonymous();
				Session second = anonymous();
				CountDownLatch ready = new CountDownLatch(2);
				Future<ResponseEntity<String>> toNew = executor.submit((Callable<ResponseEntity<String>>) () -> {
					ready.countDown();
					ready.await(10, TimeUnit.SECONDS);
					return reset(first, token, NEW_PASSWORD);
				});
				Future<ResponseEntity<String>> toAnother = executor.submit((Callable<ResponseEntity<String>>) () -> {
					ready.countDown();
					ready.await(10, TimeUnit.SECONDS);
					return reset(second, token, "another1!pass");
				});

				ResponseEntity<String> newResult = toNew.get(60, TimeUnit.SECONDS);
				ResponseEntity<String> anotherResult = toAnother.get(60, TimeUnit.SECONDS);
				List<Integer> statuses = new ArrayList<>(
						List.of(newResult.getStatusCode().value(), anotherResult.getStatusCode().value()));
				assertThat(statuses).as("round " + round).containsExactlyInAnyOrder(204, 400);
				ResponseEntity<String> loser = newResult.getStatusCode().value() == 400 ? newResult : anotherResult;
				assertBody(loser, 400, TOKEN_INVALID);
				String winner = newResult.getStatusCode().value() == 204 ? NEW_PASSWORD : "another1!pass";
				String loserPassword = winner.equals(NEW_PASSWORD) ? "another1!pass" : NEW_PASSWORD;
				PasswordHasher hasher = new PasswordHasher();
				assertThat(hasher.matches(winner, passwordHashOf(memberId))).as("round " + round).isTrue();
				assertThat(hasher.matches(loserPassword, passwordHashOf(memberId))).as("round " + round).isFalse();
			}
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void resetTokenIsValidForFifteenMinutes() throws Exception {
		createMember(MEMBER_ID, EMAIL, true);
		createMember(OTHER_ID, "late@example.com", true);
		Session session = anonymous();
		requestReset(session, EMAIL);
		String early = onlyMail().token();
		this.clock.advance(Duration.ofMinutes(15).minusSeconds(1));
		assertThat(reset(session, early, NEW_PASSWORD).getStatusCode().value()).isEqualTo(204);

		this.mailSender.clear();
		requestReset(session, "late@example.com");
		String late = onlyMail().token();
		this.clock.advance(Duration.ofMinutes(15));

		assertBody(reset(session, late, NEW_PASSWORD), 400, TOKEN_INVALID);
		assertThat(passwordHashOf(OTHER_ID)).isEqualTo(OLD_PASSWORD_HASH);
	}

	@Test
	void reissuedResetTokenInvalidatesPreviousOne() throws Exception {
		createMember(MEMBER_ID, EMAIL, true);
		Session session = anonymous();
		requestReset(session, EMAIL);
		String first = onlyMail().token();
		this.clock.advance(Duration.ofSeconds(60));
		assertBody(requestReset(session, EMAIL), 202, ACCEPTED);
		String second = this.mailSender.sentMails().get(1).token();

		assertBody(reset(session, first, NEW_PASSWORD), 400, TOKEN_INVALID);
		assertThat(passwordHashOf(MEMBER_ID)).isEqualTo(OLD_PASSWORD_HASH);
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_tokens", Integer.class)).isOne();
		assertThat(reset(session, second, NEW_PASSWORD).getStatusCode().value()).isEqualTo(204);
	}

	@Test
	void unknownWellFormedTokenIsInvalid() throws Exception {
		assertBody(reset(anonymous(), "a".repeat(43), NEW_PASSWORD), 400, TOKEN_INVALID);
	}

	/** U2에서 이월: 이메일 인증 토큰과 재설정 토큰은 서로의 API에서 거부되고, 거부돼도 아무것도 바뀌지 않는다. */
	@Test
	void tokensOfTheOtherPurposeAreRejectedWithoutSideEffects() throws Exception {
		Session session = anonymous();
		assertBody(post("/api/v1/auth/sign-up", session,
				"{\"email\":\"" + EMAIL + "\",\"password\":\"" + OLD_PASSWORD + "\"}"), 202, ACCEPTED);
		String verificationToken = onlyMail().token();
		String memberId = this.jdbcTemplate.queryForObject("SELECT member_id FROM members", String.class);
		String signUpHash = passwordHashOf(memberId);
		this.clock.advance(Duration.ofSeconds(60));
		assertBody(requestReset(session, EMAIL), 202, ACCEPTED);
		String resetToken = this.mailSender.sentMails().get(1).token();
		assertThat(this.mailSender.sentMails()).extracting(AuthMail::purpose)
			.containsExactly(AuthTokenPurpose.EMAIL_VERIFICATION, AuthTokenPurpose.PASSWORD_RESET);

		assertBody(reset(session, verificationToken, NEW_PASSWORD), 400, TOKEN_INVALID);
		assertBody(post("/api/v1/auth/email-verifications", session, "{\"token\":\"" + resetToken + "\"}"), 400,
				TOKEN_INVALID);

		assertThat(passwordHashOf(memberId)).isEqualTo(signUpHash);
		assertThat(this.jdbcTemplate.queryForObject("SELECT email_verified_at FROM members", Object.class)).isNull();
		assertThat(this.jdbcTemplate.queryForList("SELECT purpose FROM auth_tokens ORDER BY purpose", String.class))
			.containsExactly("EMAIL_VERIFICATION", "PASSWORD_RESET");
		// 거부된 토큰은 자기 목적의 API에서는 그대로 쓸 수 있다. 재설정은 인증 토큰을 지우지 않는다.
		assertThat(reset(session, resetToken, NEW_PASSWORD).getStatusCode().value()).isEqualTo(204);
		assertThat(post("/api/v1/auth/email-verifications", session, "{\"token\":\"" + verificationToken + "\"}")
			.getStatusCode()
			.value()).isEqualTo(204);
	}

	@Test
	void resetRequestsShareTheSixtySecondIntervalWithSignUpAndResend() throws Exception {
		Session session = anonymous();
		assertBody(post("/api/v1/auth/sign-up", session,
				"{\"email\":\"" + EMAIL + "\",\"password\":\"" + OLD_PASSWORD + "\"}"), 202, ACCEPTED);

		ResponseEntity<String> limited = requestReset(session, " MEMBER@example.com ");
		assertBody(limited, 429, RATE_LIMITED);
		assertThat(limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
		assertThat(limited.getHeaders().getCacheControl()).isEqualTo("no-store");

		this.clock.advance(Duration.ofSeconds(59));
		assertThat(requestReset(session, EMAIL).getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
		this.clock.advance(Duration.ofSeconds(1));
		assertBody(requestReset(session, EMAIL), 202, ACCEPTED);
		// 재설정 요청도 다음 재요청의 60초에 들어간다.
		assertBody(post("/api/v1/auth/email-verification-requests", session, "{\"email\":\"" + EMAIL + "\"}"), 429,
				RATE_LIMITED);
		assertThat(this.mailSender.sentMails()).extracting(AuthMail::purpose)
			.containsExactly(AuthTokenPurpose.EMAIL_VERIFICATION, AuthTokenPurpose.PASSWORD_RESET);
	}

	@Test
	void missingAccountIsLimitedTheSameWay() throws Exception {
		Session session = anonymous();
		assertBody(requestReset(session, "nobody@example.com"), 202, ACCEPTED);

		ResponseEntity<String> limited = requestReset(session, "nobody@example.com");

		assertBody(limited, 429, RATE_LIMITED);
		assertThat(limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
		assertThat(this.mailSender.sentMails()).isEmpty();
	}

	/** 관리자 판단(결정 28): 재설정은 로그인 실패 잠금을 풀지 않는다. 잠금이 끝나면 새 비밀번호로 로그인된다. */
	@Test
	void resetDoesNotLiftTheLoginLock() throws Exception {
		createMember(MEMBER_ID, EMAIL, true);
		for (int i = 0; i < 5; i++) {
			login(anonymous(), EMAIL, "wrong1!password");
		}
		Session session = anonymous();
		requestReset(session, EMAIL);
		assertThat(reset(session, onlyMail().token(), NEW_PASSWORD).getStatusCode().value()).isEqualTo(204);

		assertThat(login(anonymous(), EMAIL, NEW_PASSWORD).getStatusCode().value()).isEqualTo(429);
		this.clock.advance(Duration.ofMinutes(15));
		assertThat(login(anonymous(), EMAIL, NEW_PASSWORD).getStatusCode().value()).isEqualTo(200);
	}

	/**
	 * 사본: 동시 로그인 경쟁에서도 이전 비밀번호로 만든 세션을 남기지 않는다. 이전 비밀번호 로그인과 재설정을 동시에 보내, 로그인이 먼저
	 * 끝났으면 그 세션이 재설정으로 지워지고 나중이면 401인지 본다. 어떤 순서에도 500이 없다.
	 */
	@Test
	void loginRacingResetNeverLeavesAnOldPasswordSession() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			for (int round = 0; round < 10; round++) {
				String memberId = "usr-%032d".formatted(round);
				String email = "race" + round + "@example.com";
				createMember(memberId, email, true);
				Session requester = anonymous();
				Session loginSession = anonymous();
				this.mailSender.clear();
				assertBody(requestReset(requester, email), 202, ACCEPTED);
				String token = onlyMail().token();
				CountDownLatch ready = new CountDownLatch(2);
				Future<ResponseEntity<String>> login = executor.submit((Callable<ResponseEntity<String>>) () -> {
					ready.countDown();
					ready.await(10, TimeUnit.SECONDS);
					return login(loginSession, email, OLD_PASSWORD);
				});
				Future<ResponseEntity<String>> reset = executor.submit((Callable<ResponseEntity<String>>) () -> {
					ready.countDown();
					ready.await(10, TimeUnit.SECONDS);
					return reset(requester, token, NEW_PASSWORD);
				});

				ResponseEntity<String> loginResponse = login.get(60, TimeUnit.SECONDS);
				assertThat(reset.get(60, TimeUnit.SECONDS).getStatusCode().value()).as("round " + round).isEqualTo(204);
				assertThat(loginResponse.getStatusCode().value()).as("round " + round).isIn(200, 401);
				if (loginResponse.getStatusCode().value() == 200) {
					String newSession = cookieValue(loginResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
					assertThat(getSession(newSession).getStatusCode().value()).as("round " + round).isEqualTo(401);
				}
				assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_sessions WHERE member_id = ?",
						Integer.class, memberId))
					.as("round " + round)
					.isZero();
			}
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void requestsWithoutCsrfAreForbiddenAndChangeNothing() throws Exception {
		createMember(MEMBER_ID, EMAIL, true);
		Session session = anonymous();
		requestReset(session, EMAIL);
		String token = onlyMail().token();
		Session noCsrf = new Session(session.id(), null);
		this.clock.advance(Duration.ofSeconds(60));

		assertThat(requestReset(noCsrf, EMAIL).getStatusCode().value()).isEqualTo(403);
		assertThat(reset(noCsrf, token, NEW_PASSWORD).getStatusCode().value()).isEqualTo(403);

		assertThat(this.mailSender.sentMails()).hasSize(1);
		assertThat(passwordHashOf(MEMBER_ID)).isEqualTo(OLD_PASSWORD_HASH);
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_tokens", Integer.class)).isOne();
	}

	@Test
	void tokensAndPasswordsAreNotLogged(CapturedOutput output) throws Exception {
		createMember(MEMBER_ID, EMAIL, true);
		Session session = anonymous();
		requestReset(session, EMAIL);
		String token = onlyMail().token();
		reset(session, token, "short1!");
		reset(session, token, NEW_PASSWORD);
		reset(session, token, NEW_PASSWORD);

		assertThat(output.getAll()).doesNotContain(token)
			.doesNotContain(sha256Hex(token))
			.doesNotContain(NEW_PASSWORD)
			.doesNotContain("short1!");
	}

	private record Session(String id, String csrf) {
	}

	private void createMember(String memberId, String email, boolean verified) {
		this.jdbcTemplate.update(
				"INSERT INTO members (member_id, email, password_hash, email_verified_at, created_at) VALUES (?, ?, ?, ?, ?)",
				memberId, email, OLD_PASSWORD_HASH, verified ? "2026-11-17 09:00:00" : null, "2026-11-17 09:00:00");
	}

	private String passwordHashOf(String memberId) {
		return this.jdbcTemplate.queryForObject("SELECT password_hash FROM members WHERE member_id = ?", String.class,
				memberId);
	}

	/** 새 익명 세션으로 로그인해 회원 세션 ID를 돌려준다. */
	private String loginAs(String email, String password) {
		ResponseEntity<String> response = login(anonymous(), email, password);
		assertThat(response.getStatusCode().value()).isEqualTo(200);
		return cookieValue(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
	}

	private Session anonymous() {
		ResponseEntity<String> issued = client().get().uri("/api/v1/auth/csrf").retrieve().toEntity(String.class);
		String body = issued.getBody();
		int start = body.indexOf("\"token\":\"") + "\"token\":\"".length();
		return new Session(cookieValue(issued.getHeaders().getFirst(HttpHeaders.SET_COOKIE)),
				body.substring(start, body.indexOf('"', start)));
	}

	private ResponseEntity<String> getSession(String sessionId) {
		return client().get()
			.uri("/api/v1/auth/session")
			.header(HttpHeaders.COOKIE, "__Host-BEONE_SESSION=" + sessionId)
			.retrieve()
			.toEntity(String.class);
	}

	private ResponseEntity<String> requestReset(Session session, String email) {
		return post("/api/v1/auth/password-reset-requests", session, "{\"email\":\"" + email + "\"}");
	}

	private ResponseEntity<String> reset(Session session, String token, String newPassword) {
		return post("/api/v1/auth/password-resets", session,
				"{\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}");
	}

	private ResponseEntity<String> login(Session session, String email, String password) {
		return post("/api/v1/auth/login", session, "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
	}

	private ResponseEntity<String> post(String path, Session session, String body) {
		RestClient.RequestBodySpec request = client().post()
			.uri(path)
			.header(HttpHeaders.COOKIE, "__Host-BEONE_SESSION=" + session.id());
		if (session.csrf() != null) {
			request = request.header("X-CSRF-TOKEN", session.csrf());
		}
		return request.contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toEntity(String.class);
	}

	private AuthMail onlyMail() {
		assertThat(this.mailSender.sentMails()).hasSize(1);
		return this.mailSender.sentMails().get(0);
	}

	private String allTableText() {
		StringBuilder text = new StringBuilder();
		for (String table : List.of("members", "auth_tokens", "auth_mail_throttles", "auth_sessions")) {
			this.jdbcTemplate.queryForList("SELECT * FROM " + table).forEach(row -> text.append(row.values()));
		}
		return text.toString();
	}

	private static String cookieValue(String setCookie) {
		return setCookie.substring("__Host-BEONE_SESSION=".length(), setCookie.indexOf(';'));
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
