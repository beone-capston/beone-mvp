package com.beone.api.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * 세션 쿠키·CSRF 기반을 실제 HTTP·MySQL로 확인한다. Flyway {@code V9} 적용({@code ddl-auto: validate}), 세션 ID 해시 저장,
 * 쿠키 헤더 원문, 미사용·절대 만료, 인증 POST의 CSRF 검사를 본다.
 *
 * <p>기대값은 docs/api/auth.md(인증 명세 사본 반영)와 D-24다. 만료 경계를 짧게 시험하려고 익명 세션 수명을 미사용 1시간·최대
 * 3시간으로 명시해 주입한다(기본값은 미사용 2시간, AnonymousSessionPropertiesTest). 시계는 서울 2026-11-18 10:00에서 시작해 테스트가 앞으로 돌린다. U1에는 인증 POST API가 없으므로 테스트 전용
 * POST 경로로 검사기를 확인한다.
 */
@IntegrationTest
@ActiveProfiles("local")
@TestPropertySource(properties = { "beone.auth.anonymous-session.idle-timeout=PT1H",
		"beone.auth.anonymous-session.absolute-timeout=PT3H" })
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
				+ "; Path=/; Max-Age=10800; Secure; HttpOnly; SameSite=Lax");
		assertThat(sessionId).matches("[A-Za-z0-9_-]{43}");
		String token = token(response);
		assertThat(token).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(sessionId);
		assertThat(response.getBody()).doesNotContain(sessionId);

		Map<String, Object> row = this.jdbcTemplate.queryForMap("SELECT * FROM auth_sessions");
		assertThat(row.get("session_hash")).isEqualTo(sha256Hex(sessionId));
		assertThat(row.values()).noneMatch(value -> String.valueOf(value).contains(sessionId))
			.noneMatch(value -> String.valueOf(value).contains(token));
		assertThat(row.get("created_at").toString()).startsWith("2026-11-18T10:00");
		assertThat(row.get("idle_expires_at").toString()).startsWith("2026-11-18T11:00");
		assertThat(row.get("absolute_expires_at").toString()).startsWith("2026-11-18T13:00");
	}

	@Test
	void sameSessionReturnsSameTokenWithoutNewCookieAndExtendsIdleExpiry() throws Exception {
		ResponseEntity<String> first = get(null);
		String sessionId = cookieValue(first.getHeaders().getFirst(HttpHeaders.SET_COOKIE));

		this.clock.advance(Duration.ofMinutes(50));
		ResponseEntity<String> second = get(sessionId);

		assertThat(second.getStatusCode().value()).isEqualTo(200);
		assertThat(second.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
		assertThat(token(second)).isEqualTo(token(first));
		assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_sessions", Integer.class)).isOne();
		Map<String, Object> row = this.jdbcTemplate.queryForMap("SELECT * FROM auth_sessions");
		assertThat(row.get("last_used_at").toString()).startsWith("2026-11-18T10:50");
		assertThat(row.get("idle_expires_at").toString()).startsWith("2026-11-18T11:50");
	}

	@Test
	void idleExpiredSessionIsReplacedAndOldRowDeleted() throws Exception {
		ResponseEntity<String> first = get(null);
		String oldSession = cookieValue(first.getHeaders().getFirst(HttpHeaders.SET_COOKIE));

		this.clock.advance(Duration.ofHours(1));
		ResponseEntity<String> second = get(oldSession);

		String newSession = cookieValue(second.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
		assertThat(newSession).isNotEqualTo(oldSession);
		assertThat(token(second)).isNotEqualTo(token(first));
		assertThat(this.jdbcTemplate.queryForList("SELECT session_hash FROM auth_sessions", String.class))
			.containsExactly(sha256Hex(newSession));
	}

	@Test
	void absoluteExpiryEndsSessionEvenWhenUsedRegularly() throws Exception {
		String sessionId = cookieValue(get(null).getHeaders().getFirst(HttpHeaders.SET_COOKIE));
		for (int i = 0; i < 5; i++) {
			this.clock.advance(Duration.ofMinutes(35));
			assertThat(get(sessionId).getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
		}
		// 발급 뒤 2시간 55분. 사용을 이어 가도 3시간이 되면 끝난다.
		this.clock.advance(Duration.ofMinutes(5));

		assertThat(get(sessionId).getHeaders().getFirst(HttpHeaders.SET_COOKIE)).isNotNull()
			.doesNotContain(sessionId);
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

		this.clock.advance(Duration.ofHours(3));
		ResponseEntity<String> expired = post(sessionId, token);
		assertBody(expired, 403, CSRF_INVALID);
		assertThat(expired.getHeaders().getCacheControl()).isEqualTo("no-store");
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
