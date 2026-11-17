package com.beone.api.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import com.beone.api.auth.application.ActiveSession;
import com.beone.api.auth.application.AuthSessionService;
import com.beone.api.auth.application.CurrentMemberSession;
import com.beone.api.auth.application.IssuedSession;
import com.beone.api.auth.application.LoginResult;
import com.beone.api.auth.application.MemberLoginService;
import com.beone.api.auth.application.SessionTokens;
import com.beone.api.support.WebSliceTest;
import com.beone.api.user.application.DemoUserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 회원 로그인·로그아웃·현재 회원 세션 조회 API의 요청 검증과 응답 형식을 MockMvc로 확인한다.
 *
 * <p>기대값은 docs/api/auth.md(인증 명세 사본 반영)의 세 API 요청·응답 예시와 인증 오류 코드 표에서 옮겼다. 로그인 판정 결과는 서비스
 * 대역으로 정하고, 실제 세션·잠금 처리는 통합 테스트가 본다.
 */
@WebSliceTest(controllers = MemberSessionController.class)
@ActiveProfiles("local")
class MemberSessionApiWebTest {

	private static final String LOGIN = "/api/v1/auth/login";

	private static final String LOGOUT = "/api/v1/auth/logout";

	private static final String SESSION = "/api/v1/auth/session";

	private static final String CSRF_INVALID = "{\"code\":\"CSRF_INVALID\",\"message\":\"요청을 확인할 수 없습니다. 화면을 새로고침한 후 다시 시도해 주세요.\",\"errors\":[]}";

	private static final String SESSION_ID = SessionTokens.newSessionId();

	private static final String CSRF = SessionTokens.csrfTokenOf(SESSION_ID);

	private static final String NEW_SESSION_ID = SessionTokens.newSessionId();

	private static final String PASSWORD = "abcdefgh1!x";

	private static final String MEMBER_ID = "usr-0123456789abcdef0123456789abcdef";

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	MemberLoginService loginService;

	@MockitoBean
	AuthSessionService authSessionService;

	// local 프로필의 현재 사용자 해석기가 슬라이스에 함께 올라오므로 그 의존성만 대신한다. 인증 API는 시연 사용자를 쓰지 않는다.
	@MockitoBean
	DemoUserService demoUserService;

	@BeforeEach
	void validCsrfSession() {
		given(this.authSessionService.useWithCsrf(SESSION_ID, CSRF))
			.willReturn(Optional.of(new ActiveSession(SESSION_ID, Duration.ofHours(2))));
	}

	// 로그인

	@Test
	void loginReturnsMemberAndReplacesTheSessionCookieOnce() throws Exception {
		given(this.loginService.login(" User@Example.com ", PASSWORD, SESSION_ID))
			.willReturn(new LoginResult.Succeeded(MEMBER_ID, "user@example.com", new IssuedSession(NEW_SESSION_ID,
					SessionTokens.csrfTokenOf(NEW_SESSION_ID), AuthSessionService.MEMBER_ABSOLUTE_TIMEOUT)));

		MvcResult result = performLogin("{\"email\":\" User@Example.com \",\"password\":\"" + PASSWORD + "\"}");

		assertBody(result, 200, "{\"data\":{\"userId\":\"" + MEMBER_ID + "\",\"email\":\"user@example.com\"}}");
		// MockMvc 응답 객체는 Set-Cookie를 다시 써서 Expires를 덧붙인다. 헤더 원문은 SessionCookieTest와 통합 테스트(실제 HTTP)가 본다.
		assertThat(result.getResponse().getHeaders("Set-Cookie")).hasSize(1);
		assertThat(result.getResponse().getHeader("Set-Cookie"))
			.startsWith("__Host-BEONE_SESSION=" + NEW_SESSION_ID + "; Path=/; Max-Age=2592000;");
		assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
		assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain(NEW_SESSION_ID);
	}

	@Test
	void wrongCredentialsAre401() throws Exception {
		given(this.loginService.login(any(), any(), any())).willReturn(new LoginResult.InvalidCredentials());

		MvcResult result = performLogin("{\"email\":\"user@example.com\",\"password\":\"" + PASSWORD + "\"}");

		assertBody(result, 401,
				"{\"code\":\"INVALID_CREDENTIALS\",\"message\":\"이메일 또는 비밀번호를 확인해 주세요.\",\"errors\":[]}");
		// CSRF를 통과한 세션의 갱신 쿠키만 남고 새 세션은 없다.
		assertThat(result.getResponse().getHeaders("Set-Cookie")).hasSize(1);
		assertThat(result.getResponse().getHeader("Set-Cookie"))
			.startsWith("__Host-BEONE_SESSION=" + SESSION_ID + "; Path=/; Max-Age=7200;");
	}

	@Test
	void unverifiedEmailIs403() throws Exception {
		given(this.loginService.login(any(), any(), any())).willReturn(new LoginResult.EmailNotVerified());

		MvcResult result = performLogin("{\"email\":\"user@example.com\",\"password\":\"" + PASSWORD + "\"}");

		assertBody(result, 403, "{\"code\":\"EMAIL_NOT_VERIFIED\",\"message\":\"이메일 인증을 완료해 주세요.\",\"errors\":[]}");
	}

	@Test
	void lockedEmailIs429WithRetryAfter() throws Exception {
		given(this.loginService.login(any(), any(), any()))
			.willReturn(new LoginResult.RateLimited(Duration.ofSeconds(900)));

		MvcResult result = performLogin("{\"email\":\"user@example.com\",\"password\":\"" + PASSWORD + "\"}");

		assertBody(result, 429,
				"{\"code\":\"RATE_LIMITED\",\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.\",\"errors\":[]}");
		assertThat(result.getResponse().getHeader("Retry-After")).isEqualTo("900");
	}

	@Test
	void loginWithoutCsrfIsForbiddenBeforeBodyValidation() throws Exception {
		MvcResult result = this.mockMvc
			.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"not-email\"}"))
			.andReturn();

		assertBody(result, 403, CSRF_INVALID);
		verifyNoInteractions(this.loginService);
	}

	@ParameterizedTest
	@ValueSource(strings = { "{\"password\":\"abcdefgh1!x\"}", "{\"email\":\"user@example.com\"}",
			"{\"email\":\"not-email\",\"password\":\"abcdefgh1!x\"}", "{\"email\":\"user@example.com\",\"password\":\"\"}",
			"{\"email\":\"user@example.com\",\"password\":\"abcdefghijabcdefghijabcdefghij123\"}",
			"{\"email\":\"user@example.com\",\"password\":\"abcdefgh1!x\",\"rememberMe\":true}", "{" })
	void invalidLoginBodyIsInvalidRequestWithoutEchoingPassword(String body) throws Exception {
		MvcResult result = performLogin(body);

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
			.contains("\"code\":\"INVALID_REQUEST\"")
			.doesNotContain("abcdefgh1!x")
			.doesNotContain("abcdefghijabcdefghijabcdefghij123");
		verifyNoInteractions(this.loginService);
	}

	@Test
	void loginPasswordIsNotHeldToNewPasswordRules() throws Exception {
		// 로그인 비밀번호는 빈 값 불가·최대 32자만 본다. 새 비밀번호 규칙(11자 이상 등)으로 거절하지 않는다.
		given(this.loginService.login(any(), any(), any())).willReturn(new LoginResult.InvalidCredentials());

		MvcResult result = performLogin("{\"email\":\"user@example.com\",\"password\":\" a\"}");

		assertThat(result.getResponse().getStatus()).isEqualTo(401);
		verify(this.loginService).login("user@example.com", " a", SESSION_ID);
	}

	// 로그아웃

	@Test
	void logoutEndsCurrentSessionAndClearsCookie() throws Exception {
		MvcResult result = this.mockMvc
			.perform(post(LOGOUT).cookie(new Cookie("__Host-BEONE_SESSION", SESSION_ID)).header("X-CSRF-TOKEN", CSRF))
			.andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(204);
		assertThat(result.getResponse().getContentAsString()).isEmpty();
		assertThat(result.getResponse().getHeaders("Set-Cookie")).hasSize(1);
		assertThat(result.getResponse().getHeader("Set-Cookie")).startsWith("__Host-BEONE_SESSION=; Path=/; Max-Age=0;");
		assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
		verify(this.loginService).logout(SESSION_ID);
	}

	@Test
	void logoutWithoutCsrfIsForbidden() throws Exception {
		MvcResult result = this.mockMvc.perform(post(LOGOUT).cookie(new Cookie("__Host-BEONE_SESSION", SESSION_ID)))
			.andReturn();

		assertBody(result, 403, CSRF_INVALID);
		assertThat(result.getResponse().getHeaders("Set-Cookie")).isEmpty();
		verifyNoInteractions(this.loginService);
	}

	// 세션 조회

	@Test
	void sessionReturnsMemberAndExpiryTimesInSeoulOffset() throws Exception {
		given(this.loginService.currentSession(SESSION_ID)).willReturn(Optional.of(new CurrentMemberSession(MEMBER_ID,
				"user@example.com", LocalDateTime.of(2026, 11, 1, 10, 0), LocalDateTime.of(2026, 11, 8, 10, 0),
				LocalDateTime.of(2026, 12, 1, 10, 0))));

		MvcResult result = this.mockMvc.perform(get(SESSION).cookie(new Cookie("__Host-BEONE_SESSION", SESSION_ID)))
			.andReturn();

		assertBody(result, 200, "{\"data\":{\"userId\":\"" + MEMBER_ID + "\",\"email\":\"user@example.com\","
				+ "\"authenticatedAt\":\"2026-11-01T10:00:00+09:00\",\"expiresAt\":\"2026-11-08T10:00:00+09:00\","
				+ "\"absoluteExpiresAt\":\"2026-12-01T10:00:00+09:00\"}}");
		assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
		assertThat(result.getResponse().getHeaders("Set-Cookie")).isEmpty();
	}

	@Test
	void sessionWithoutMemberSessionIs401AndIgnoresDemoHeader() throws Exception {
		given(this.loginService.currentSession(null)).willReturn(Optional.empty());

		MvcResult result = this.mockMvc.perform(get(SESSION).header("X-BeONE-Demo-User", "demo-user-1")).andReturn();

		assertBody(result, 401, "{\"code\":\"UNAUTHENTICATED\",\"message\":\"로그인이 필요합니다.\",\"errors\":[]}");
		assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
		verifyNoInteractions(this.demoUserService);
	}

	private MvcResult performLogin(String body) throws Exception {
		return this.mockMvc
			.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
				.content(body)
				.cookie(new Cookie("__Host-BEONE_SESSION", SESSION_ID))
				.header("X-CSRF-TOKEN", CSRF))
			.andReturn();
	}

	private static void assertBody(MvcResult result, int status, String expected) throws Exception {
		assertThat(result.getResponse().getStatus()).isEqualTo(status);
		JsonAssert.comparator(JsonCompareMode.STRICT)
			.assertIsMatch(expected, result.getResponse().getContentAsString(StandardCharsets.UTF_8));
	}

}
