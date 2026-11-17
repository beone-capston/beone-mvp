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
 * 회원가입·인증메일 재요청·이메일 인증 완료를 실제 HTTP·MySQL로 확인한다. Flyway {@code V11} 적용({@code ddl-auto: validate}),
 * 저장 내용(해시만 저장), 계정 존재 비공개, 토큰 30분·1회용·재발급 무효, 같은 주소 60초 합산 제한을 본다.
 *
 * <p>기대값은 docs/api/auth.md(인증 명세 사본 반영)와 관리자 판단(결정 28: 회원 ID {@code usr-}+32자, 링크 형식 미정·가짜 발송기
 * 메모리)이다. 시계는 주입한 가변 시계로 서울 2026-11-18 10:00에서 시작해 테스트가 앞으로 돌린다(sleep 없음). 매 요청은
 * {@code GET /api/v1/auth/csrf}로 받은 실제 세션·CSRF 토큰을 쓴다.
 */
@IntegrationTest
@ActiveProfiles("local")
@Import(AuthSessionIntegrationTest.MutableClockConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class MemberRegistrationIntegrationTest {

	private static final Instant START = Instant.parse("2026-11-18T01:00:00Z");

	private static final String ACCEPTED = "{\"data\":{\"message\":\"처리 가능한 요청이면 이메일로 안내합니다.\"}}";

	private static final String RATE_LIMITED = "{\"code\":\"RATE_LIMITED\",\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.\",\"errors\":[]}";

	private static final String TOKEN_INVALID = "{\"code\":\"AUTH_TOKEN_INVALID\",\"message\":\"링크가 유효하지 않거나 만료되었습니다. 다시 요청해 주세요.\",\"errors\":[]}";

	private static final String PASSWORD = "abcdefgh1!x";

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	AuthSessionIntegrationTest.MutableClock clock;

	@Autowired
	FakeAuthMailSender mailSender;

	private String sessionId;

	private String csrf;

	@BeforeEach
	void setUp() {
		clearTables();
		this.clock.set(START);
		ResponseEntity<String> issued = client().get().uri("/api/v1/auth/csrf").retrieve().toEntity(String.class);
		String setCookie = issued.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
		this.sessionId = setCookie.substring("__Host-BEONE_SESSION=".length(), setCookie.indexOf(';'));
		String body = issued.getBody();
		int start = body.indexOf("\"token\":\"") + "\"token\":\"".length();
		this.csrf = body.substring(start, body.indexOf('"', start));
	}

	@AfterEach
	void clearTables() {
		this.jdbcTemplate.update("DELETE FROM auth_tokens");
		this.jdbcTemplate.update("DELETE FROM members");
		this.jdbcTemplate.update("DELETE FROM auth_mail_throttles");
		this.jdbcTemplate.update("DELETE FROM auth_sessions");
		this.mailSender.clear();
	}

	@Test
	void memberMigrationIsApplied() {
		assertThat(this.jdbcTemplate.queryForObject("SELECT success FROM flyway_schema_history WHERE version = '11'",
				Boolean.class))
			.isTrue();
	}

	@Test
	void signUpCreatesUnverifiedMemberAndStoresOnlyHashes(CapturedOutput output) throws Exception {
		ResponseEntity<String> response = signUp(" New.User+tag@Example.COM ", PASSWORD);

		assertBody(response, 202, ACCEPTED);
		assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
		Map<String, Object> member = this.jdbcTemplate.queryForMap("SELECT * FROM members");
		assertThat((String) member.get("member_id")).matches("usr-[0-9a-f]{32}");
		assertThat(member.get("email")).isEqualTo("new.user+tag@example.com");
		assertThat(member.get("email_verified_at")).isNull();
		String passwordHash = (String) member.get("password_hash");
		assertThat(passwordHash).startsWith("$argon2id$v=19$m=19456,t=2,p=1$").doesNotContain(PASSWORD);
		assertThat(new PasswordHasher().matches(PASSWORD, passwordHash)).isTrue();

		AuthMail mail = onlyMail();
		assertThat(mail.to()).isEqualTo("new.user+tag@example.com");
		assertThat(mail.purpose()).isEqualTo(AuthTokenPurpose.EMAIL_VERIFICATION);
		assertThat(mail.token()).matches("[A-Za-z0-9_-]{43}");
		Map<String, Object> token = this.jdbcTemplate.queryForMap("SELECT * FROM auth_tokens");
		assertThat(token.get("token_hash")).isEqualTo(sha256Hex(mail.token()));
		assertThat(token.get("purpose")).isEqualTo("EMAIL_VERIFICATION");
		assertThat(token.get("member_id")).isEqualTo(member.get("member_id"));
		assertThat(token.get("created_at").toString()).startsWith("2026-11-18T10:00");
		assertThat(token.get("expires_at").toString()).startsWith("2026-11-18T10:30");
		assertThat(this.jdbcTemplate.queryForList("SELECT email_hash FROM auth_mail_throttles", String.class))
			.containsExactly(sha256Hex("new.user+tag@example.com"));

		// DB와 로그 어디에도 원문 토큰·비밀번호가 없다.
		assertThat(allTableText()).doesNotContain(mail.token()).doesNotContain(PASSWORD);
		assertThat(output.getAll()).doesNotContain(mail.token()).doesNotContain(PASSWORD);
	}

	@Test
	void responsesAreIdenticalWhetherAccountIsNewUnverifiedVerifiedOrMissing() throws Exception {
		List<ResponseEntity<String>> responses = new ArrayList<>();
		responses.add(signUp("new@example.com", PASSWORD));
		String firstToken = onlyMail().token();
		advance(Duration.ofSeconds(60));
		// 미인증 기존 계정: 다른 비밀번호로 가입해도 비밀번호는 그대로이고 새 인증메일만 간다.
		String hashBefore = this.jdbcTemplate.queryForObject("SELECT password_hash FROM members", String.class);
		responses.add(signUp("new@example.com", "zyxwvuts9?q"));
		assertThat(this.jdbcTemplate.queryForObject("SELECT password_hash FROM members", String.class)).isEqualTo(hashBefore);
		assertThat(this.mailSender.sentMails()).hasSize(2);
		String secondToken = this.mailSender.sentMails().get(1).token();
		assertThat(secondToken).isNotEqualTo(firstToken);
		assertThat(verify(secondToken).getStatusCode().value()).isEqualTo(204);
		advance(Duration.ofSeconds(60));
		// 인증된 기존 계정: 가입·재요청 모두 메일을 보내지 않는다.
		responses.add(signUp("new@example.com", PASSWORD));
		advance(Duration.ofSeconds(60));
		responses.add(resend("new@example.com"));
		// 계정 없음: 재요청도 같은 응답이고 메일이 없다.
		responses.add(resend("nobody@example.com"));

		for (ResponseEntity<String> response : responses) {
			assertBody(response, 202, ACCEPTED);
			assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
		}
		assertThat(this.mailSender.sentMails()).hasSize(2);
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM members", Integer.class)).isOne();
	}

	@Test
	void verificationMarksEmailVerifiedOnceAndInvalidatesToken() throws Exception {
		signUp("user@example.com", PASSWORD);
		String token = onlyMail().token();
		String hashBefore = this.jdbcTemplate.queryForObject("SELECT password_hash FROM members", String.class);
		advance(Duration.ofMinutes(5));

		ResponseEntity<String> verified = verify(token);

		assertThat(verified.getStatusCode().value()).isEqualTo(204);
		assertThat(verified.getBody()).isNull();
		assertThat(verified.getHeaders().getCacheControl()).isEqualTo("no-store");
		Map<String, Object> member = this.jdbcTemplate.queryForMap("SELECT * FROM members");
		assertThat(member.get("email_verified_at").toString()).startsWith("2026-11-18T10:05");
		assertThat(member.get("password_hash")).isEqualTo(hashBefore);
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_tokens", Integer.class)).isZero();
		// 로그인 세션을 만들지 않는다: 이 요청의 세션은 그대로 익명 세션 하나다.
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_sessions", Integer.class)).isOne();

		// 1회용: 같은 토큰을 다시 쓰면 400이다.
		assertBody(verify(token), 400, TOKEN_INVALID);
		assertThat(this.jdbcTemplate.queryForObject("SELECT email_verified_at FROM members", Object.class).toString())
			.startsWith("2026-11-18T10:05");
	}

	@Test
	void verificationTokenIsValidForThirtyMinutes() throws Exception {
		signUp("early@example.com", PASSWORD);
		String early = onlyMail().token();
		advance(Duration.ofMinutes(30).minusSeconds(1));
		assertThat(verify(early).getStatusCode().value()).isEqualTo(204);

		this.mailSender.clear();
		signUp("late@example.com", PASSWORD);
		String late = onlyMail().token();
		advance(Duration.ofMinutes(30));
		assertBody(verify(late), 400, TOKEN_INVALID);
		assertThat(this.jdbcTemplate.queryForObject(
				"SELECT email_verified_at FROM members WHERE email = 'late@example.com'", Object.class))
			.isNull();
	}

	@Test
	void reissuedTokenInvalidatesPreviousOne() throws Exception {
		signUp("user@example.com", PASSWORD);
		String first = onlyMail().token();
		advance(Duration.ofSeconds(60));
		assertBody(resend("user@example.com"), 202, ACCEPTED);
		String second = this.mailSender.sentMails().get(1).token();

		assertBody(verify(first), 400, TOKEN_INVALID);
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_tokens", Integer.class)).isOne();
		assertThat(verify(second).getStatusCode().value()).isEqualTo(204);
	}

	@Test
	void unknownWellFormedTokenIsInvalid() throws Exception {
		assertBody(verify("a".repeat(43)), 400, TOKEN_INVALID);
	}

	@Test
	void mailRequestsForSameAddressShareSixtySecondInterval() throws Exception {
		assertBody(signUp("user@example.com", PASSWORD), 202, ACCEPTED);

		// 가입 뒤 30초: 재요청도 같은 주소로 합산되어 429다. 대소문자·공백이 달라도 같은 주소다.
		advance(Duration.ofSeconds(30));
		ResponseEntity<String> limited = resend(" USER@example.com ");
		assertBody(limited, 429, RATE_LIMITED);
		assertThat(limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("30");
		assertThat(limited.getHeaders().getCacheControl()).isEqualTo("no-store");

		// 거절은 기준 시각을 늦추지 않는다: 가입 뒤 59초에 1초 남음, 60초에 접수.
		advance(Duration.ofSeconds(29));
		assertThat(signUp("user@example.com", PASSWORD).getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
		advance(Duration.ofSeconds(1));
		assertBody(resend("user@example.com"), 202, ACCEPTED);
		assertThat(this.mailSender.sentMails()).hasSize(2);
	}

	@Test
	void missingAccountIsCountedTheSameWay() throws Exception {
		assertBody(resend("nobody@example.com"), 202, ACCEPTED);
		advance(Duration.ofSeconds(10));

		ResponseEntity<String> limited = resend("nobody@example.com");

		assertBody(limited, 429, RATE_LIMITED);
		assertThat(limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("50");
		assertThat(this.mailSender.sentMails()).isEmpty();
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM members", Integer.class)).isZero();
		// 비회원 주소는 원문이 남지 않는다.
		assertThat(allTableText()).doesNotContain("nobody@example.com");
	}

	@Test
	void concurrentSignUpsForSameAddressAcceptOnlyOne() throws Exception {
		int requests = 4;
		ExecutorService executor = Executors.newFixedThreadPool(requests);
		CountDownLatch ready = new CountDownLatch(requests);
		try {
			List<Future<Integer>> results = new ArrayList<>();
			for (int i = 0; i < requests; i++) {
				results.add(executor.submit((Callable<Integer>) () -> {
					ready.countDown();
					ready.await(10, TimeUnit.SECONDS);
					return signUp("race@example.com", PASSWORD).getStatusCode().value();
				}));
			}
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> result : results) {
				statuses.add(result.get(30, TimeUnit.SECONDS));
			}
			assertThat(statuses).containsOnly(202, 429).filteredOn(status -> status == 202).hasSize(1);
		}
		finally {
			executor.shutdownNow();
		}
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM members", Integer.class)).isOne();
		assertThat(this.mailSender.sentMails()).hasSize(1);
	}

	@Test
	void requestsWithoutCsrfAreForbiddenAndCreateNothing() {
		ResponseEntity<String> response = client().post()
			.uri("/api/v1/auth/sign-up")
			.contentType(MediaType.APPLICATION_JSON)
			.body("{\"email\":\"user@example.com\",\"password\":\"" + PASSWORD + "\"}")
			.retrieve()
			.toEntity(String.class);

		assertThat(response.getStatusCode().value()).isEqualTo(403);
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM members", Integer.class)).isZero();
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_mail_throttles", Integer.class)).isZero();
	}

	private ResponseEntity<String> signUp(String email, String password) {
		return post("/api/v1/auth/sign-up", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
	}

	private ResponseEntity<String> resend(String email) {
		return post("/api/v1/auth/email-verification-requests", "{\"email\":\"" + email + "\"}");
	}

	private ResponseEntity<String> verify(String token) {
		return post("/api/v1/auth/email-verifications", "{\"token\":\"" + token + "\"}");
	}

	private ResponseEntity<String> post(String path, String body) {
		return client().post()
			.uri(path)
			.contentType(MediaType.APPLICATION_JSON)
			.header(HttpHeaders.COOKIE, "__Host-BEONE_SESSION=" + this.sessionId)
			.header("X-CSRF-TOKEN", this.csrf)
			.body(body)
			.retrieve()
			.toEntity(String.class);
	}

	/** 익명 세션이 미사용으로 끝나지 않도록 짧게 돌린다(미사용 2시간보다 짧음). */
	private void advance(Duration duration) {
		this.clock.advance(duration);
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
