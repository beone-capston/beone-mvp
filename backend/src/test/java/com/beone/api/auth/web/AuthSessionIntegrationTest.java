package com.beone.api.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.beone.api.common.web.ApiResponse;
import com.beone.api.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 세션 쿠키·CSRF 기반을 실제 HTTP·MySQL로 확인한다. Flyway {@code V9} 적용({@code ddl-auto: validate}), 세션 ID 해시 저장,
 * 쿠키 헤더 원문, 익명 세션 수명, 인증 POST의 CSRF 검사를 본다.
 *
 * <p>기대값은 docs/api/auth.md(인증 명세 사본 반영)와 D-24, 2026-11-18 사용자 직접 결정(익명 세션은 미사용 2시간에 만료, 발급 기준
 * 절대 상한 없음, 유효한 사용마다 쿠키 {@code Max-Age}와 서버 미사용 만료를 함께 갱신)이다. 수명은 설정을 덮어쓰지 않고 기본값(2시간)을
 * 쓴다. 시계는 주입한 가변 시계로 서울 2026-11-18 10:00에서 시작해 테스트가 앞으로 돌린다(sleep 없음). U1에는 인증 POST API가 없으므로
 * 테스트 전용 POST 경로로 검사기를 확인한다.
 */
@IntegrationTest
@ActiveProfiles("local")
@Import({ AuthSessionIntegrationTest.MutableClockConfiguration.class, AuthSessionIntegrationTest.ProbeController.class })
class AuthSessionIntegrationTest {

	private static final String CSRF_PATH = "/api/v1/auth/csrf";

	private static final String PROBE_PATH = "/api/v1/auth/test-probe";

	private static final Instant START = Instant.parse("2026-11-18T01:00:00Z");

	private static final String CSRF_INVALID = "{\"code\":\"CSRF_INVALID\",\"message\":\"요청을 확인할 수 없습니다. 화면을 새로고침한 후 다시 시도해 주세요.\",\"errors\":[]}";

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	MutableClock clock;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Autowired
	MySQLContainer mysql;

	@BeforeEach
	@AfterEach
	void clearSessions() {
		this.jdbcTemplate.update("DELETE FROM auth_sessions");
		this.clock.set(START);
	}

	@Test
	void sessionMigrationIsApplied() {
		Boolean success = this.jdbcTemplate.queryForObject(
				"SELECT success FROM flyway_schema_history WHERE version = '9'", Boolean.class);

		assertThat(success).isTrue();
	}

	@Test
	void firstCsrfRequestIssuesCookieAndStoresOnlyHash() throws Exception {
		ResponseEntity<String> response = get(null);

		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
		List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
		assertThat(setCookies).hasSize(1);
		String sessionId = cookieValue(setCookies.get(0));
		assertThat(setCookies.get(0)).isEqualTo("__Host-BEONE_SESSION=" + sessionId
				+ "; Path=/; Max-Age=7200; Secure; HttpOnly; SameSite=Lax");
		assertThat(sessionId).matches("[A-Za-z0-9_-]{43}");
		String token = token(response);
		assertThat(token).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(sessionId);
		assertThat(response.getBody()).doesNotContain(sessionId);

		Map<String, Object> row = this.jdbcTemplate.queryForMap("SELECT * FROM auth_sessions");
		assertThat(row.get("session_hash")).isEqualTo(sha256Hex(sessionId));
		assertThat(row.values()).noneMatch(value -> String.valueOf(value).contains(sessionId))
			.noneMatch(value -> String.valueOf(value).contains(token));
		assertThat(row.get("created_at").toString()).startsWith("2026-11-18T10:00");
		assertThat(row.get("idle_expires_at").toString()).startsWith("2026-11-18T12:00");
		assertThat(row.get("absolute_expires_at")).isNull();
	}

	/** 필수 ①: 2시간 안에 계속 쓰면 발급 뒤 2시간을 넘겨도, 발급이 40일 전이어도 유지된다(절대 상한 없음). */
	@Test
	void sustainedUseKeepsAnonymousSessionBeyondTwoHoursAfterCreation() throws Exception {
		ResponseEntity<String> first = get(null);
		String sessionId = cookieValue(first.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
		for (int i = 0; i < 6; i++) {
			this.clock.advance(Duration.ofMinutes(110));
			ResponseEntity<String> used = get(sessionId);
			assertThat(cookieValue(used.getHeaders().getFirst(HttpHeaders.SET_COOKIE))).isEqualTo(sessionId);
			assertThat(token(used)).isEqualTo(token(first));
		}
		// 발급 뒤 11시간. 같은 세션 한 행만 있다.
		assertThat(this.jdbcTemplate.queryForList("SELECT session_hash FROM auth_sessions", String.class))
			.containsExactly(sha256Hex(sessionId));

		// 발급 시각을 40일 전으로 옮겨도(회원 최대 30일보다 오래) 생성 기준으로 끝나지 않는다.
		this.jdbcTemplate.update("UPDATE auth_sessions SET created_at = DATE_SUB(created_at, INTERVAL 40 DAY)");
		this.clock.advance(Duration.ofMinutes(110));
		ResponseEntity<String> later = get(sessionId);
		assertThat(cookieValue(later.getHeaders().getFirst(HttpHeaders.SET_COOKIE))).isEqualTo(sessionId);
		assertThat(this.jdbcTemplate.queryForObject("SELECT absolute_expires_at FROM auth_sessions", Object.class))
			.isNull();
	}

	/** 필수 ②: 마지막 사용 뒤 2시간 동안 쓰지 않으면 만료하고, 다음 조회는 새 익명 세션을 발급하며 옛 행을 지운다. */
	@Test
	void anonymousSessionExpiresAfterTwoHoursWithoutUse() throws Exception {
		ResponseEntity<String> first = get(null);
		String oldSession = cookieValue(first.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
		this.clock.advance(Duration.ofMinutes(30));
		assertThat(cookieValue(get(oldSession).getHeaders().getFirst(HttpHeaders.SET_COOKIE))).isEqualTo(oldSession);

		// 마지막 사용(10:30) 뒤 1시간 59분 59초: 아직 유효.
		this.clock.advance(Duration.ofHours(2).minusSeconds(1));
		assertThat(cookieValue(get(oldSession).getHeaders().getFirst(HttpHeaders.SET_COOKIE))).isEqualTo(oldSession);

		// 마지막 사용(12:29:59) 뒤 정확히 2시간: 만료.
		this.clock.advance(Duration.ofHours(2));
		ResponseEntity<String> expired = get(oldSession);
		String newSession = cookieValue(expired.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
		assertThat(newSession).isNotEqualTo(oldSession);
		assertThat(token(expired)).isNotEqualTo(token(first));
		assertThat(this.jdbcTemplate.queryForList("SELECT session_hash FROM auth_sessions", String.class))
			.containsExactly(sha256Hex(newSession));
		assertBody(post(oldSession, token(first)), 403, CSRF_INVALID);
	}

	/** 필수 ③: 유효한 사용(GET csrf, CSRF를 통과한 POST)마다 쿠키 Max-Age=7200을 다시 심고, 서버 미사용 만료와 같은 시각을 가리킨다. */
	@Test
	void cookieMaxAgeIsRefreshedOnEachValidUseAndMatchesServerIdleExpiry() throws Exception {
		ResponseEntity<String> issued = get(null);
		String sessionId = cookieValue(issued.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
		String token = token(issued);
		assertCookieMatchesServer(issued, sessionId, "2026-11-18T10:00", "2026-11-18T12:00");

		this.clock.advance(Duration.ofMinutes(50));
		assertCookieMatchesServer(get(sessionId), sessionId, "2026-11-18T10:50", "2026-11-18T12:50");

		this.clock.advance(Duration.ofMinutes(70));
		ResponseEntity<String> posted = post(sessionId, token);
		assertBody(posted, 200, "{\"data\":\"ok\"}");
		assertCookieMatchesServer(posted, sessionId, "2026-11-18T12:00", "2026-11-18T14:00");

		// CSRF 실패는 세션을 쓰지 않은 것으로 보고 쿠키를 심지 않는다. 서버 만료도 그대로다.
		this.clock.advance(Duration.ofMinutes(10));
		ResponseEntity<String> rejected = post(sessionId, "wrong");
		assertBody(rejected, 403, CSRF_INVALID);
		assertThat(rejected.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
	}

	private void assertCookieMatchesServer(ResponseEntity<String> response, String sessionId, String lastUsed,
			String idleExpiry) throws Exception {
		assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).containsExactly("__Host-BEONE_SESSION=" + sessionId
				+ "; Path=/; Max-Age=7200; Secure; HttpOnly; SameSite=Lax");
		Map<String, Object> row = this.jdbcTemplate.queryForMap("SELECT * FROM auth_sessions");
		assertThat(row.get("last_used_at").toString()).startsWith(lastUsed);
		assertThat(row.get("idle_expires_at").toString()).startsWith(idleExpiry);
		// 쿠키 수명(7200초) = 서버 미사용 만료 - 마지막 사용.
		assertThat(Duration.between((LocalDateTime) row.get("last_used_at"),
				(LocalDateTime) row.get("idle_expires_at"))).isEqualTo(Duration.ofSeconds(7200));
	}

	@Test
	void postRequiresCookieAndMatchingTokenOfLiveSession() throws Exception {
		ResponseEntity<String> issued = get(null);
		String sessionId = cookieValue(issued.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
		String token = token(issued);

		assertBody(post(null, token), 403, CSRF_INVALID);
		assertBody(post(sessionId, null), 403, CSRF_INVALID);
		assertBody(post(sessionId, token(get(null))), 403, CSRF_INVALID);

		ResponseEntity<String> ok = post(sessionId, token);
		assertBody(ok, 200, "{\"data\":\"ok\"}");
		assertThat(ok.getHeaders().getCacheControl()).isEqualTo("no-store");

		this.clock.advance(Duration.ofHours(2));
		ResponseEntity<String> expired = post(sessionId, token);
		assertBody(expired, 403, CSRF_INVALID);
		assertThat(expired.getHeaders().getCacheControl()).isEqualTo("no-store");
	}

	@Test
	void methodMismatchAndUnknownAuthPathsAreNoStore() {
		ResponseEntity<String> notAllowed = client().post().uri(CSRF_PATH).retrieve().toEntity(String.class);
		assertThat(notAllowed.getStatusCode().value()).isEqualTo(405);
		assertThat(notAllowed.getHeaders().getCacheControl()).isEqualTo("no-store");

		ResponseEntity<String> notFound = client().get().uri("/api/v1/auth/unknown").retrieve().toEntity(String.class);
		assertThat(notFound.getStatusCode().value()).isEqualTo(404);
		assertThat(notFound.getHeaders().getCacheControl()).isEqualTo("no-store");
	}

	@Test
	void concurrentUseWaitsForExtensionAndDoesNotDeleteExtendedSession() throws Exception {
		String sessionId = cookieValue(get(null).getHeaders().getFirst(HttpHeaders.SET_COOKIE));
		String hash = sha256Hex(sessionId);
		// 저장된 미사용 만료(12:00)에 도달했다. 다른 요청이 같은 순간 이 세션을 연장하고 있다.
		this.clock.advance(Duration.ofHours(2));
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			CompletableFuture<ResponseEntity<String>> concurrent = new CompletableFuture<>();
			new TransactionTemplate(this.transactionManager).executeWithoutResult(status -> {
				this.jdbcTemplate.queryForObject("SELECT auth_session_id FROM auth_sessions WHERE session_hash = ? FOR UPDATE",
						Long.class, hash);
				this.jdbcTemplate.update(
						"UPDATE auth_sessions SET last_used_at = '2026-11-18 11:59:59', idle_expires_at = '2026-11-18 13:59:59' "
								+ "WHERE session_hash = ?",
						hash);
				long connection = this.jdbcTemplate.queryForObject("SELECT CONNECTION_ID()", Long.class);
				executor.submit(() -> concurrent.complete(get(sessionId)));
				awaitLockWaitOnSessions(connection);
			});

			ResponseEntity<String> response = concurrent.get(20, TimeUnit.SECONDS);
			// 잠금 없이 옛 만료 값으로 판정하면 연장된 세션을 지우고 새 세션 쿠키를 심는다. 잠금이 있으면 같은 세션 쿠키를 갱신한다.
			assertThat(cookieValue(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE))).isEqualTo(sessionId);
			assertThat(this.jdbcTemplate.queryForList("SELECT session_hash FROM auth_sessions", String.class))
				.containsExactly(hash);
		}
		finally {
			executor.shutdownNow();
		}
	}

	// 이 테스트 트랜잭션(blockingConnection)이 잠근 auth_sessions 행을 다른 세션이 기다리기 시작할 때까지 최대 10초 기다린다.
	// 잠금 대기 표(performance_schema)는 전역 권한이 필요해 컨테이너의 root 계정으로 읽는다(PaymentPreferenceIntegrationTest와 같음).
	private void awaitLockWaitOnSessions(long blockingConnection) {
		String query = "SELECT COUNT(*) FROM performance_schema.data_lock_waits w "
				+ "JOIN performance_schema.data_locks l ON l.ENGINE_LOCK_ID = w.REQUESTING_ENGINE_LOCK_ID "
				+ "JOIN performance_schema.threads t ON t.THREAD_ID = w.BLOCKING_THREAD_ID "
				+ "WHERE l.OBJECT_NAME = 'auth_sessions' AND t.PROCESSLIST_ID = ?";
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		try (Connection connection = DriverManager.getConnection(this.mysql.getJdbcUrl(), "root",
				this.mysql.getPassword()); PreparedStatement statement = connection.prepareStatement(query)) {
			statement.setLong(1, blockingConnection);
			while (System.nanoTime() < deadline) {
				try (ResultSet result = statement.executeQuery()) {
					result.next();
					if (result.getLong(1) > 0) {
						return;
					}
				}
				Thread.sleep(50);
			}
		}
		catch (SQLException ex) {
			throw new IllegalStateException(ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
		throw new AssertionError("CSRF 토큰 조회 요청이 10초 안에 테스트가 잠근 세션 행의 잠금 대기에 들어가지 않았습니다.");
	}

	private ResponseEntity<String> get(String sessionId) {
		RestClient.RequestHeadersSpec<?> spec = client().get().uri(CSRF_PATH);
		if (sessionId != null) {
			spec = spec.header(HttpHeaders.COOKIE, "__Host-BEONE_SESSION=" + sessionId);
		}
		return spec.retrieve().toEntity(String.class);
	}

	private ResponseEntity<String> post(String sessionId, String token) {
		RestClient.RequestBodySpec spec = client().post().uri(PROBE_PATH).contentType(MediaType.APPLICATION_JSON);
		if (sessionId != null) {
			spec = spec.header(HttpHeaders.COOKIE, "__Host-BEONE_SESSION=" + sessionId);
		}
		if (token != null) {
			spec = spec.header("X-CSRF-TOKEN", token);
		}
		return spec.body("{}").retrieve().toEntity(String.class);
	}

	private RestClient client() {
		return RestClient.builder()
			.baseUrl("http://localhost:" + this.port)
			.defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
			})
			.build();
	}

	private static String cookieValue(String setCookie) {
		assertThat(setCookie).startsWith("__Host-BEONE_SESSION=");
		return setCookie.substring("__Host-BEONE_SESSION=".length(), setCookie.indexOf(';'));
	}

	private static String token(ResponseEntity<String> response) {
		String body = response.getBody();
		assertThat(body).contains("\"headerName\":\"X-CSRF-TOKEN\"");
		int start = body.indexOf("\"token\":\"") + "\"token\":\"".length();
		return body.substring(start, body.indexOf('"', start));
	}

	private static void assertBody(ResponseEntity<String> response, int status, String expected) throws Exception {
		assertThat(response.getStatusCode().value()).isEqualTo(status);
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch(expected, response.getBody());
	}

	private static String sha256Hex(String value) throws Exception {
		return HexFormat.of()
			.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
	}

	/** 테스트가 앞으로 돌릴 수 있는 시계. */
	static final class MutableClock extends Clock {

		private volatile Instant instant = START;

		void set(Instant value) {
			this.instant = value;
		}

		void advance(Duration duration) {
			this.instant = this.instant.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			// 서비스는 서울 시간대로 바꿔 쓴다. 같은 순간을 공유하도록 시간대만 감싼다.
			MutableClock self = this;
			return new Clock() {
				@Override
				public ZoneId getZone() {
					return zone;
				}

				@Override
				public Clock withZone(ZoneId other) {
					return self.withZone(other);
				}

				@Override
				public Instant instant() {
					return self.instant();
				}
			};
		}

		@Override
		public Instant instant() {
			return this.instant;
		}

	}

	@TestConfiguration(proxyBeanMethods = false)
	static class MutableClockConfiguration {

		@Bean
		MutableClock mutableClock() {
			return new MutableClock();
		}

	}

	/** 인증 POST 검사기를 확인하기 위한 테스트 전용 경로. */
	@RestController
	static class ProbeController {

		@PostMapping(PROBE_PATH)
		ApiResponse<String> probe() {
			return ApiResponse.of("ok");
		}

	}

}
