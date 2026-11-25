package com.beone.api.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import com.beone.api.auth.application.ActiveSession;
import com.beone.api.auth.application.AuthSessionService;
import com.beone.api.auth.application.IssuedSession;
import com.beone.api.auth.application.SessionTokens;
import com.beone.api.common.web.ApiResponse;
import com.beone.api.support.WebSliceTest;
import com.beone.api.user.application.DemoUserService;
import jakarta.servlet.http.Cookie;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * CSRF 토큰 조회 API와 인증 POST의 CSRF 검사를 MockMvc로 확인한다.
 *
 * <p>기대값은 docs/api/auth.md(인증 명세 사본 반영)의 CSRF 토큰 조회 응답 예시, 로그인 응답의 쿠키 속성, 인증 POST 공통 실패
 * 403 {@code CSRF_INVALID}에서 옮겼다. U1에는 인증 POST API가 아직 없으므로 테스트 전용 POST 경로로 검사기를 확인한다.
 */
@WebSliceTest(controllers = { AuthCsrfController.class, AuthCsrfApiWebTest.ProbeController.class })
@ActiveProfiles("local")
@Import(AuthCsrfApiWebTest.ProbeController.class)
class AuthCsrfApiWebTest {

	private static final String CSRF_PATH = "/api/v1/auth/csrf";

	private static final String PROBE_PATH = "/api/v1/auth/test-probe";

	private static final String CSRF_INVALID = "{\"code\":\"CSRF_INVALID\",\"message\":\"요청을 확인할 수 없습니다. 화면을 새로고침한 후 다시 시도해 주세요.\",\"errors\":[]}";

	private static final String SESSION_ID = SessionTokens.newSessionId();

	private static final String TOKEN = SessionTokens.csrfTokenOf(SESSION_ID);

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	AuthSessionService authSessionService;

	// local 프로필의 현재 사용자 해석기가 슬라이스에 함께 올라오므로 그 의존성만 대신한다. 인증 API는 시연 사용자를 쓰지 않는다.
	@MockitoBean
	DemoUserService demoUserService;

	// GET /api/v1/auth/csrf

	@Test
	void withoutCookieIssuesAnonymousSessionCookieAndToken() throws Exception {
		given(this.authSessionService.use(null)).willReturn(Optional.empty());
		given(this.authSessionService.issueAnonymous())
			.willReturn(new IssuedSession(SESSION_ID, TOKEN, Duration.ofHours(2)));

		MvcResult result = this.mockMvc.perform(get(CSRF_PATH)).andReturn();

		assertBody(result, 200, "{\"data\":{\"headerName\":\"X-CSRF-TOKEN\",\"token\":\"" + TOKEN + "\"}}");
		assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
		// MockMvc 응답 객체는 Set-Cookie를 다시 써서 Expires를 덧붙인다. 헤더 원문은 SessionCookieTest와 통합 테스트(실제 HTTP)가 본다.
		assertThat(result.getResponse().getHeaders("Set-Cookie")).hasSize(1);
		Cookie cookie = result.getResponse().getCookie("__Host-BEONE_SESSION");
		assertThat(cookie).isNotNull();
		assertThat(cookie.getValue()).isEqualTo(SESSION_ID);
		assertThat(cookie.getPath()).isEqualTo("/");
		assertThat(cookie.getMaxAge()).isEqualTo(7200);
		assertThat(cookie.getSecure()).isTrue();
		assertThat(cookie.isHttpOnly()).isTrue();
		assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
		assertThat(cookie.getDomain()).isNull();
		assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain(SESSION_ID);
	}

	@Test
	void cookieMaxAgeFollowsIssuedLifetime() throws Exception {
		given(this.authSessionService.use(null)).willReturn(Optional.empty());
		given(this.authSessionService.issueAnonymous())
			.willReturn(new IssuedSession(SESSION_ID, TOKEN, Duration.ofHours(3)));

		MvcResult result = this.mockMvc.perform(get(CSRF_PATH)).andReturn();

		assertThat(result.getResponse().getCookie("__Host-BEONE_SESSION").getMaxAge()).isEqualTo(10800);
	}

	@Test
	void validSessionCookieReturnsItsTokenAndRefreshesSameCookieMaxAge() throws Exception {
		given(this.authSessionService.use(SESSION_ID))
			.willReturn(Optional.of(new ActiveSession(SESSION_ID, Duration.ofHours(2))));

		MvcResult result = this.mockMvc.perform(get(CSRF_PATH).cookie(sessionCookie(SESSION_ID))).andReturn();

		assertBody(result, 200, "{\"data\":{\"headerName\":\"X-CSRF-TOKEN\",\"token\":\"" + TOKEN + "\"}}");
		assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
		assertRefreshedCookie(result, 7200);
		verify(this.authSessionService, never()).issueAnonymous();
	}

	@Test
	void expiredOrUnknownCookieGetsNewAnonymousSession() throws Exception {
		String stale = SessionTokens.newSessionId();
		given(this.authSessionService.use(stale)).willReturn(Optional.empty());
		given(this.authSessionService.issueAnonymous())
			.willReturn(new IssuedSession(SESSION_ID, TOKEN, Duration.ofHours(2)));

		MvcResult result = this.mockMvc.perform(get(CSRF_PATH).cookie(sessionCookie(stale))).andReturn();

		assertBody(result, 200, "{\"data\":{\"headerName\":\"X-CSRF-TOKEN\",\"token\":\"" + TOKEN + "\"}}");
		assertThat(result.getResponse().getHeader("Set-Cookie")).startsWith("__Host-BEONE_SESSION=" + SESSION_ID + ";");
	}

	@Test
	void demoUserHeaderIsIgnored() throws Exception {
		given(this.authSessionService.use(null)).willReturn(Optional.empty());
		given(this.authSessionService.issueAnonymous())
			.willReturn(new IssuedSession(SESSION_ID, TOKEN, Duration.ofHours(2)));

		MvcResult result = this.mockMvc.perform(get(CSRF_PATH).header("X-BeONE-Demo-User", "WRONG")).andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		verifyNoInteractions(this.demoUserService);
	}

	@Test
	void postToCsrfPathIsMethodNotAllowed() throws Exception {
		MvcResult result = this.mockMvc.perform(post(CSRF_PATH)).andReturn();

		assertBody(result, 405, "{\"code\":\"METHOD_NOT_ALLOWED\",\"message\":\"지원하지 않는 요청 방식입니다.\",\"errors\":[]}");
		assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
	}

	@Test
	void unknownAuthPathIsNotFoundWithNoStore() throws Exception {
		MvcResult result = this.mockMvc.perform(get("/api/v1/auth/unknown")).andReturn();

		assertBody(result, 404, "{\"code\":\"NOT_FOUND\",\"message\":\"요청한 리소스를 찾을 수 없습니다.\",\"errors\":[]}");
		assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
	}

	// 인증 POST의 CSRF 검사

	@Test
	void postWithoutCookieIsCsrfInvalid() throws Exception {
		given(this.authSessionService.useWithCsrf(any(), any())).willReturn(Optional.empty());

		MvcResult result = this.mockMvc.perform(probe().header("X-CSRF-TOKEN", TOKEN)).andReturn();

		assertCsrfInvalid(result);
	}

	@Test
	void postWithExpiredSessionIsCsrfInvalidEvenWithFormerToken() throws Exception {
		given(this.authSessionService.useWithCsrf(any(), any())).willReturn(Optional.empty());

		MvcResult result = this.mockMvc
			.perform(probe().cookie(sessionCookie(SESSION_ID)).header("X-CSRF-TOKEN", TOKEN))
			.andReturn();

		assertCsrfInvalid(result);
	}

	@Test
	void postWithoutTokenHeaderIsCsrfInvalid() throws Exception {
		given(this.authSessionService.useWithCsrf(SESSION_ID, TOKEN))
			.willReturn(Optional.of(new ActiveSession(SESSION_ID, Duration.ofHours(2))));

		MvcResult result = this.mockMvc.perform(probe().cookie(sessionCookie(SESSION_ID))).andReturn();

		assertCsrfInvalid(result);
	}

	@Test
	void postWithTokenOfAnotherSessionIsCsrfInvalid() throws Exception {
		given(this.authSessionService.useWithCsrf(SESSION_ID, TOKEN))
			.willReturn(Optional.of(new ActiveSession(SESSION_ID, Duration.ofHours(2))));
		String otherToken = SessionTokens.csrfTokenOf(SessionTokens.newSessionId());

		MvcResult result = this.mockMvc
			.perform(probe().cookie(sessionCookie(SESSION_ID)).header("X-CSRF-TOKEN", otherToken))
			.andReturn();

		assertCsrfInvalid(result);
	}

	@Test
	void csrfIsCheckedBeforeBodyValidation() throws Exception {
		given(this.authSessionService.useWithCsrf(any(), any())).willReturn(Optional.empty());

		MvcResult result = this.mockMvc
			.perform(post(PROBE_PATH).contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"\"}"))
			.andReturn();

		assertCsrfInvalid(result);
	}

	@Test
	void postWithMatchingCookieAndTokenReachesHandler() throws Exception {
		given(this.authSessionService.useWithCsrf(SESSION_ID, TOKEN))
			.willReturn(Optional.of(new ActiveSession(SESSION_ID, Duration.ofHours(2))));

		MvcResult result = this.mockMvc
			.perform(probe().cookie(sessionCookie(SESSION_ID)).header("X-CSRF-TOKEN", TOKEN))
			.andReturn();

		assertBody(result, 200, "{\"data\":{\"value\":\"ok\"}}");
		assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
		// CSRF를 통과한 인증 POST도 쿠키 수명을 다시 심는다(사본의 POST 응답 헤더 예시에 덧붙임, 2026-11-18 사용자 직접 결정).
		assertRefreshedCookie(result, 7200);
	}

	@Test
	void validCsrfThenInvalidBodyIsInvalidRequest() throws Exception {
		given(this.authSessionService.useWithCsrf(SESSION_ID, TOKEN))
			.willReturn(Optional.of(new ActiveSession(SESSION_ID, Duration.ofHours(2))));

		MvcResult result = this.mockMvc
			.perform(post(PROBE_PATH).contentType(MediaType.APPLICATION_JSON)
				.content("{\"value\":\"\"}")
				.cookie(sessionCookie(SESSION_ID))
				.header("X-CSRF-TOKEN", TOKEN))
			.andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
			.contains("\"code\":\"INVALID_REQUEST\"");
	}

	@Test
	void csrfFailureDoesNotEchoTokenOrCookie() throws Exception {
		given(this.authSessionService.useWithCsrf(any(), any())).willReturn(Optional.empty());

		MvcResult result = this.mockMvc
			.perform(probe().cookie(sessionCookie(SESSION_ID)).header("X-CSRF-TOKEN", TOKEN))
			.andReturn();

		assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain(SESSION_ID)
			.doesNotContain(TOKEN);
		assertThat(result.getResponse().getHeaders("Set-Cookie")).isEmpty();
	}

	private static MockHttpServletRequestBuilder probe() {
		return post(PROBE_PATH).contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"ok\"}");
	}

	private static Cookie sessionCookie(String value) {
		return new Cookie("__Host-BEONE_SESSION", value);
	}

	private static void assertRefreshedCookie(MvcResult result, int maxAge) {
		assertThat(result.getResponse().getHeaders("Set-Cookie")).hasSize(1);
		Cookie cookie = result.getResponse().getCookie("__Host-BEONE_SESSION");
		assertThat(cookie).isNotNull();
		assertThat(cookie.getValue()).isEqualTo(SESSION_ID);
		assertThat(cookie.getMaxAge()).isEqualTo(maxAge);
		assertThat(cookie.getPath()).isEqualTo("/");
		assertThat(cookie.getSecure()).isTrue();
		assertThat(cookie.isHttpOnly()).isTrue();
		assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
	}

	private static void assertCsrfInvalid(MvcResult result) throws Exception {
		assertThat(result.getResponse().getHeaders("Set-Cookie")).isEmpty();
		assertBody(result, 403, CSRF_INVALID);
		assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
	}

	private static void assertBody(MvcResult result, int status, String expected) throws Exception {
		assertThat(result.getResponse().getStatus()).isEqualTo(status);
		JsonAssert.comparator(JsonCompareMode.STRICT)
			.assertIsMatch(expected, result.getResponse().getContentAsString(StandardCharsets.UTF_8));
	}

	/** 인증 POST 검사기를 확인하기 위한 테스트 전용 경로. 본문 검증이 CSRF 검사 뒤에 오는지도 본다. */
	@RestController
	static class ProbeController {

		@PostMapping(PROBE_PATH)
		ApiResponse<ProbeBody> probe(@Valid @RequestBody ProbeBody body) {
			return ApiResponse.of(body);
		}

	}

	record ProbeBody(@NotBlank String value) {
	}

}
