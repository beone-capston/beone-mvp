package com.beone.api.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.beone.api.auth.application.ActiveSession;
import com.beone.api.auth.application.AuthSessionService;
import com.beone.api.auth.application.IssuedSession;
import com.beone.api.auth.application.LoginResult;
import com.beone.api.auth.application.MemberLoginService;
import com.beone.api.auth.application.SessionTokens;
import com.beone.api.support.WebSliceTest;
import com.beone.api.user.application.MemberUserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.ForwardedHeaderFilter;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * 공개 프로필 {@code prod}에서 앱이 {@code X-Forwarded-*} 헤더를 해석하지 않음을 고정한다(결정 38-7, G5 2-3절·L5).
 *
 * <p>같은 요청을 전달 헤더 없이 한 번, 위조한 전달 헤더와 함께 한 번 보내 상태·본문·{@code Set-Cookie}·{@code Location}이 같은지 비교한다.
 * 실제 인증 API(로그인·로그아웃)로 쿠키 속성을, 테스트 전용 컨트롤러로 요청의 scheme·host·원격 주소와 그것으로 만든 {@code Location}을 본다.
 *
 * 슬라이스에 전달 헤더를 해석하는 필터({@link ForwardedHeaderFilter} 등)가 들어오면 응답 비교와 Bean 검사가 실패한다.
 *
 * <p>한계: 웹 슬라이스에는 웹 서버 자동 구성이 없어 {@code framework}로 바꿔도 {@link ForwardedHeaderFilter}가 자동 등록되지 않고,
 * MockMvc는 Tomcat을 거치지 않아 {@code native}(Tomcat {@code RemoteIpValve})도 보지 않는다. 두 경우는
 * {@code server.forward-headers-strategy}가 {@code none}인지 환경 값으로 확인해 막는다. 실제 HTTP 확인은 prod 통합 테스트 범위다.
 */
@WebSliceTest(controllers = MemberSessionController.class)
@ActiveProfiles("prod")
@Import(ForwardedHeadersPublicWebTest.RequestEchoController.class)
class ForwardedHeadersPublicWebTest {

	private static final String LOGIN = "/api/v1/auth/login";

	private static final String LOGOUT = "/api/v1/auth/logout";

	private static final String ECHO = "/test/forwarded/echo";

	private static final String SESSION_ID = SessionTokens.newSessionId();

	private static final String CSRF = SessionTokens.csrfTokenOf(SESSION_ID);

	private static final String NEW_SESSION_ID = SessionTokens.newSessionId();

	private static final String MEMBER_ID = "usr-0123456789abcdef0123456789abcdef";

	private static final String PASSWORD = "abcdefgh1!x";

	/** 공격자가 위조하거나 프록시가 붙이는 전달 헤더. 앱이 해석하면 scheme·host·주소가 이 값으로 바뀐다. */
	private static final Map<String, String> FORWARDED = Map.of("X-Forwarded-For", "203.0.113.7", "X-Forwarded-Proto",
			"https", "X-Forwarded-Host", "attacker.example", "X-Forwarded-Port", "8443");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ApplicationContext context;

	@Autowired
	Environment environment;

	@MockitoBean
	MemberLoginService loginService;

	@MockitoBean
	AuthSessionService authSessionService;

	// member 프로필의 현재 사용자 해석기(MemberCurrentUserResolver)가 슬라이스에 함께 올라오므로 그 의존성만 대신한다.
	@MockitoBean
	MemberUserService memberUserService;

	@BeforeEach
	void validCsrfSessionAndSuccessfulLogin() {
		given(this.authSessionService.useWithCsrf(SESSION_ID, CSRF))
			.willReturn(Optional.of(new ActiveSession(SESSION_ID, Duration.ofHours(2))));
		given(this.loginService.login("user@example.com", PASSWORD, SESSION_ID))
			.willReturn(new LoginResult.Succeeded(MEMBER_ID, "user@example.com", new IssuedSession(NEW_SESSION_ID,
					SessionTokens.csrfTokenOf(NEW_SESSION_ID), AuthSessionService.MEMBER_ABSOLUTE_TIMEOUT)));
	}

	@Test
	void prodDoesNotInterpretForwardedHeaders() {
		assertThat(this.environment.getProperty("server.forward-headers-strategy")).isEqualTo("none");
		assertThat(this.context.getBeanNamesForType(ForwardedHeaderFilter.class)).isEmpty();
	}

	@Test
	void loginCookieAndBodyDoNotChangeWithForwardedHeaders() throws Exception {
		Snapshot plain = perform(login());
		Snapshot forwarded = perform(withForwarded(login()));

		assertThat(plain.status()).isEqualTo(200);
		assertThat(plain.setCookies()).singleElement()
			.asString()
			.isEqualTo("__Host-BEONE_SESSION=" + NEW_SESSION_ID + "; Path=/; Max-Age=2592000; Secure; HttpOnly; SameSite=Lax");
		assertThat(forwarded).isEqualTo(plain);
	}

	@Test
	void logoutCookieDoesNotChangeWithForwardedHeaders() throws Exception {
		Snapshot plain = perform(logout());
		Snapshot forwarded = perform(withForwarded(logout()));

		assertThat(plain.status()).isEqualTo(204);
		assertThat(plain.setCookies()).singleElement()
			.asString()
			.isEqualTo("__Host-BEONE_SESSION=; Path=/; Max-Age=0; Secure; HttpOnly; SameSite=Lax");
		assertThat(forwarded).isEqualTo(plain);
	}

	@Test
	void requestSchemeHostAddressAndLocationDoNotChangeWithForwardedHeaders() throws Exception {
		Snapshot plain = perform(get(ECHO));
		Snapshot forwarded = perform(withForwarded(get(ECHO)));

		// MockMvc 기본 요청은 http://localhost:80, 원격 주소 127.0.0.1이다. 전달 헤더 값이 어디에도 나타나지 않는다
		assertThat(plain.status()).isEqualTo(201);
		assertThat(plain.location()).isEqualTo("http://localhost" + ECHO + "/created");
		assertThat(plain.body()).isEqualTo("http|localhost|80|127.0.0.1|false|http://localhost" + ECHO);
		assertThat(forwarded).isEqualTo(plain);
		assertThat(forwarded.body() + forwarded.location()).doesNotContain("attacker.example", "203.0.113.7", "https",
				"8443");
	}

	private MockHttpServletRequestBuilder login() {
		return post(LOGIN).contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"user@example.com\",\"password\":\"" + PASSWORD + "\"}")
			.cookie(new Cookie("__Host-BEONE_SESSION", SESSION_ID))
			.header("X-CSRF-TOKEN", CSRF);
	}

	private static MockHttpServletRequestBuilder logout() {
		return post(LOGOUT).cookie(new Cookie("__Host-BEONE_SESSION", SESSION_ID)).header("X-CSRF-TOKEN", CSRF);
	}

	private static MockHttpServletRequestBuilder withForwarded(MockHttpServletRequestBuilder request) {
		FORWARDED.forEach(request::header);
		return request;
	}

	private Snapshot perform(MockHttpServletRequestBuilder request) throws Exception {
		MvcResult result = this.mockMvc.perform(request).andReturn();
		// MockMvc 응답 객체는 Max-Age로 계산한 Expires를 덧붙인다. 요청 시각에 따라 초가 달라지므로 비교에서 뺀다
		List<String> setCookies = result.getResponse()
			.getHeaders("Set-Cookie")
			.stream()
			.map((cookie) -> cookie.replaceAll("; Expires=[^;]*", ""))
			.toList();
		return new Snapshot(result.getResponse().getStatus(), result.getResponse().getContentAsString(StandardCharsets.UTF_8),
				setCookies, result.getResponse().getHeader("Location"));
	}

	/** 비교 대상 응답 요소. 전달 헤더 유무로 이 값이 하나라도 달라지면 앱이 헤더를 해석한 것이다. */
	record Snapshot(int status, String body, List<String> setCookies, String location) {
	}

	/** 요청에서 읽은 scheme·host·port·원격 주소·secure 여부와 현재 요청 URL로 만든 {@code Location}을 그대로 돌려주는 테스트 전용 컨트롤러. */
	@RestController
	static class RequestEchoController {

		@GetMapping(ECHO)
		ResponseEntity<String> echo(HttpServletRequest request) {
			String body = String.join("|", request.getScheme(), request.getServerName(),
					String.valueOf(request.getServerPort()), request.getRemoteAddr(), String.valueOf(request.isSecure()),
					request.getRequestURL());
			return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/created").build().toUri())
				.body(body);
		}

	}

}
