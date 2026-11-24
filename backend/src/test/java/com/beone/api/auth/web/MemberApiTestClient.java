package com.beone.api.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.beone.api.auth.application.PasswordHasher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.web.client.RestClient;

/**
 * {@code member} 프로필 통합 테스트가 함께 쓰는 회원 준비·로그인·CSRF·HTTP 호출 도우미. 스프링 빈이 아니므로 테스트 컨텍스트 구성에
 * 영향을 주지 않는다. 각 테스트가 주입받은 포트와 {@link JdbcTemplate}으로 만든다.
 *
 * <p>고정 응답 본문은 docs/api/auth.md 오류 표와 docs/api/cards.md 404 문구다.
 */
final class MemberApiTestClient {

	static final String PASSWORD = "abcdefgh1!x";

	static final String UNAUTHENTICATED = "{\"code\":\"UNAUTHENTICATED\",\"message\":\"로그인이 필요합니다.\",\"errors\":[]}";

	static final String CSRF_INVALID = "{\"code\":\"CSRF_INVALID\",\"message\":\"요청을 확인할 수 없습니다. 화면을 새로고침한 후 다시 시도해 주세요.\",\"errors\":[]}";

	static final String USER_CARD_NOT_FOUND = "{\"code\":\"USER_CARD_NOT_FOUND\",\"message\":\"보유 카드를 찾을 수 없습니다.\",\"errors\":[]}";

	private static final String PASSWORD_HASH = new PasswordHasher().hash(PASSWORD);

	private final int port;

	private final JdbcTemplate jdbcTemplate;

	MemberApiTestClient(int port, JdbcTemplate jdbcTemplate) {
		this.port = port;
		this.jdbcTemplate = jdbcTemplate;
	}

	/**
	 * 세션 쿠키 값과 CSRF 토큰. 둘 다 {@code null}이면 쿠키·토큰 없이 보낸다.
	 * @param id 세션 쿠키 값
	 * @param csrf {@code X-CSRF-TOKEN} 값
	 */
	record Session(String id, String csrf) {

		static final Session NONE = new Session(null, null);

		Session withoutCsrf() {
			return new Session(this.id, null);
		}

	}

	/**
	 * 회원 테이블과 회원 장부가 가진 업무 행을 지운다. 외래 키 순서대로 지우며 시연 장부(member_id NULL)의 행은 건드리지 않는다.
	 */
	void clearMemberData() {
		this.jdbcTemplate.update("DELETE FROM auth_login_failures");
		this.jdbcTemplate.update("DELETE FROM auth_login_throttles");
		this.jdbcTemplate.update("DELETE FROM auth_sessions");
		this.jdbcTemplate.update("DELETE FROM auth_tokens");
		this.jdbcTemplate.update("DELETE FROM auth_mail_throttles");
		for (String table : new String[] { "payment_preferences", "recommendations", "orders", "planned_spends",
				"monthly_states", "user_cards" }) {
			this.jdbcTemplate.update("DELETE t FROM " + table + " t JOIN users u ON u.user_id = t.user_id"
					+ " WHERE u.member_id IS NOT NULL");
		}
		this.jdbcTemplate.update("DELETE FROM users WHERE member_id IS NOT NULL");
		this.jdbcTemplate.update("DELETE FROM members");
	}

	/** 이메일 인증을 마친 회원과 그 장부를 직접 넣는다(V14 백필과 같은 값). */
	void createMember(String memberId, String email) {
		this.jdbcTemplate.update(
				"INSERT INTO members (member_id, email, password_hash, email_verified_at, created_at) VALUES (?, ?, ?, ?, ?)",
				memberId, email, PASSWORD_HASH, "2026-11-17 09:00:00", "2026-11-17 09:00:00");
		this.jdbcTemplate.update(
				"INSERT INTO users (member_id, display_name, portfolio_type, read_only) VALUES (?, '내 카드', 'REAL', FALSE)",
				memberId);
	}

	Session loginNew(String memberId, String email) {
		createMember(memberId, email);
		return login(anonymous(), email);
	}

	Session login(Session anonymous, String email) {
		ResponseEntity<String> response = send(HttpMethod.POST, "/api/v1/auth/login", anonymous,
				"{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}");
		assertThat(response.getStatusCode().value()).isEqualTo(200);
		String id = cookieValue(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
		return new Session(id, token(getCsrf(id).getBody()));
	}

	/** 익명 CSRF 세션. 회원 세션이 아니므로 업무 API에서는 401이다. */
	Session anonymous() {
		ResponseEntity<String> issued = getCsrf(null);
		return new Session(cookieValue(issued.getHeaders().getFirst(HttpHeaders.SET_COOKIE)), token(issued.getBody()));
	}

	ResponseEntity<String> getCsrf(String sessionId) {
		RestClient.RequestHeadersSpec<?> request = client().get().uri("/api/v1/auth/csrf");
		if (sessionId != null) {
			request = request.header(HttpHeaders.COOKIE, cookie(sessionId));
		}
		return request.retrieve().toEntity(String.class);
	}

	ResponseEntity<String> send(HttpMethod method, String path, Session session, String body) {
		RestClient.RequestBodySpec request = client().method(method).uri(path);
		if (session.id() != null) {
			request = request.header(HttpHeaders.COOKIE, cookie(session.id()));
		}
		if (session.csrf() != null) {
			request = request.header("X-CSRF-TOKEN", session.csrf());
		}
		if (body != null && method != HttpMethod.GET && method != HttpMethod.DELETE) {
			request = request.contentType(MediaType.APPLICATION_JSON).body(body);
		}
		return request.retrieve().toEntity(String.class);
	}

	/** 시연 헤더를 붙여 조회한다(회원 쿠키는 세션이 있을 때만). member 프로필은 헤더를 무시해야 한다(결정 37 D4). */
	ResponseEntity<String> getWithDemoHeader(String path, Session session, String demoUser) {
		RestClient.RequestHeadersSpec<?> request = client().get().uri(path).header("X-BeONE-Demo-User", demoUser);
		if (session.id() != null) {
			request = request.header(HttpHeaders.COOKIE, cookie(session.id()));
		}
		return request.retrieve().toEntity(String.class);
	}

	/** 시연 헤더를 붙여 보낸다. 쓰기도 member 프로필에서는 헤더와 관계없이 회원 장부에 저장해야 한다(결정 37 D4). */
	ResponseEntity<String> sendWithDemoHeader(HttpMethod method, String path, Session session, String body,
			String demoUser) {
		RestClient.RequestBodySpec request = client().method(method).uri(path).header("X-BeONE-Demo-User", demoUser);
		if (session.id() != null) {
			request = request.header(HttpHeaders.COOKIE, cookie(session.id()));
		}
		if (session.csrf() != null) {
			request = request.header("X-CSRF-TOKEN", session.csrf());
		}
		if (body != null) {
			request = request.contentType(MediaType.APPLICATION_JSON).body(body);
		}
		return request.retrieve().toEntity(String.class);
	}

	/** 회원 장부의 사용자 ID. */
	long userIdOf(String memberId) {
		return this.jdbcTemplate.queryForObject("SELECT user_id FROM users WHERE member_id = ?", Long.class, memberId);
	}

	/** 등록 응답 본문에서 보유 카드 ID를 꺼낸다. */
	static String userCardId(String body) {
		int start = body.indexOf("\"userCardId\":\"") + "\"userCardId\":\"".length();
		return body.substring(start, body.indexOf('"', start));
	}

	static String cookie(String sessionId) {
		return "__Host-BEONE_SESSION=" + sessionId;
	}

	static String cookieValue(String setCookie) {
		return setCookie.substring("__Host-BEONE_SESSION=".length(), setCookie.indexOf(';'));
	}

	private static String token(String body) {
		int start = body.indexOf("\"token\":\"") + "\"token\":\"".length();
		return body.substring(start, body.indexOf('"', start));
	}

	static void assertBody(ResponseEntity<String> response, int status, String expected) {
		assertThat(response.getStatusCode().value()).isEqualTo(status);
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch(expected, response.getBody());
	}

	/** /api/v1/me·/api/v1/me/** 응답의 캐시 금지(결정 37 구현 범위, docs/api/auth.md와 같은 규칙). */
	static void assertNoStore(ResponseEntity<String> response) {
		assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
	}

	private RestClient client() {
		return RestClient.builder()
			.baseUrl("http://localhost:" + this.port)
			.defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
			})
			.build();
	}

}
