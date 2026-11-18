package com.beone.api.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import com.beone.api.auth.application.ActiveSession;
import com.beone.api.auth.application.AuthSessionService;
import com.beone.api.auth.application.AuthTokenInvalidException;
import com.beone.api.auth.application.MailRateLimitedException;
import com.beone.api.auth.application.PasswordResetService;
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
 * 비밀번호 재설정 요청·완료 API의 요청 검증과 응답 형식을 MockMvc로 확인한다.
 *
 * <p>기대값은 docs/api/auth.md(인증 명세 사본 반영)의 두 API 요청·응답, 「값 검증」, 인증 오류 코드 표에서 옮겼다. 계정 처리 결과는
 * 서비스 대역으로 정하고, 실제 처리는 통합 테스트가 본다.
 */
@WebSliceTest(controllers = PasswordResetController.class)
@ActiveProfiles("local")
class PasswordResetApiWebTest {

	private static final String REQUEST = "/api/v1/auth/password-reset-requests";

	private static final String RESET = "/api/v1/auth/password-resets";

	private static final String ACCEPTED = "{\"data\":{\"message\":\"처리 가능한 요청이면 이메일로 안내합니다.\"}}";

	private static final String CSRF_INVALID = "{\"code\":\"CSRF_INVALID\",\"message\":\"요청을 확인할 수 없습니다. 화면을 새로고침한 후 다시 시도해 주세요.\",\"errors\":[]}";

	private static final String RATE_LIMITED = "{\"code\":\"RATE_LIMITED\",\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.\",\"errors\":[]}";

	private static final String TOKEN_INVALID = "{\"code\":\"AUTH_TOKEN_INVALID\",\"message\":\"링크가 유효하지 않거나 만료되었습니다. 다시 요청해 주세요.\",\"errors\":[]}";

	private static final String SESSION_ID = SessionTokens.newSessionId();

	private static final String CSRF = SessionTokens.csrfTokenOf(SESSION_ID);

	private static final String PASSWORD = "abcdefgh1!x";

	private static final String MAIL_TOKEN = SessionTokens.newMailToken();

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	PasswordResetService resetService;

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

	// 재설정 요청

	@Test
	void resetRequestIsAcceptedWithSameMessageAsSignUp() throws Exception {
		MvcResult result = perform(REQUEST, "{\"email\":\" User@Example.com \"}");

		assertBody(result, 202, ACCEPTED);
		assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
		verify(this.resetService).requestReset(" User@Example.com ");
	}

	@Test
	void resetRequestWithoutCsrfIsForbiddenBeforeBodyValidation() throws Exception {
		MvcResult result = this.mockMvc
			.perform(post(REQUEST).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"not-email\"}"))
			.andReturn();

		assertBody(result, 403, CSRF_INVALID);
		verifyNoInteractions(this.resetService);
	}

	@ParameterizedTest
	@ValueSource(strings = { "{}", "{\"email\":null}", "{\"email\":\"user\"}",
			"{\"email\":\"user@example.com\",\"password\":\"abcdefgh1!x\"}", "{" })
	void invalidResetRequestBodyIsInvalidRequest(String body) throws Exception {
		MvcResult result = perform(REQUEST, body);

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("\"code\":\"INVALID_REQUEST\"");
		assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
		verifyNoInteractions(this.resetService);
	}

	@Test
	void rateLimitedResetRequestIs429WithRetryAfter() throws Exception {
		willThrow(new MailRateLimitedException(Duration.ofSeconds(37))).given(this.resetService).requestReset(any());

		MvcResult result = perform(REQUEST, "{\"email\":\"user@example.com\"}");

		assertBody(result, 429, RATE_LIMITED);
		assertThat(result.getResponse().getHeader("Retry-After")).isEqualTo("37");
		assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
	}

	// 재설정 완료

	@Test
	void resetIsNoContent() throws Exception {
		MvcResult result = perform(RESET, "{\"token\":\"" + MAIL_TOKEN + "\",\"newPassword\":\"" + PASSWORD + "\"}");

		assertThat(result.getResponse().getStatus()).isEqualTo(204);
		assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).isEmpty();
		assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
		verify(this.resetService).resetPassword(MAIL_TOKEN, PASSWORD);
	}

	@Test
	void resetWithoutCsrfIsForbidden() throws Exception {
		MvcResult result = this.mockMvc
			.perform(post(RESET).contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + MAIL_TOKEN + "\",\"newPassword\":\"" + PASSWORD + "\"}"))
			.andReturn();

		assertBody(result, 403, CSRF_INVALID);
		verifyNoInteractions(this.resetService);
	}

	@Test
	void invalidResetTokenIsAuthTokenInvalidWithoutEchoingSecrets() throws Exception {
		willThrow(new AuthTokenInvalidException()).given(this.resetService).resetPassword(any(), any());

		MvcResult result = perform(RESET, "{\"token\":\"" + MAIL_TOKEN + "\",\"newPassword\":\"" + PASSWORD + "\"}");

		assertBody(result, 400, TOKEN_INVALID);
		assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain(MAIL_TOKEN)
			.doesNotContain(PASSWORD);
	}

	@ParameterizedTest
	@ValueSource(strings = { "{}", "{\"newPassword\":\"abcdefgh1!x\"}",
			"{\"token\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}",
			"{\"token\":\"short\",\"newPassword\":\"abcdefgh1!x\"}",
			"{\"token\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa=\",\"newPassword\":\"abcdefgh1!x\"}",
			"{\"token\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"newPassword\":\"short1!\"}",
			"{\"token\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"newPassword\":\"abcdefghijk!\"}",
			"{\"token\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"newPassword\":\"abcdefgh1!xabcdefgh1!xabcdefgh1!x\"}",
			"{\"token\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"password\":\"abcdefgh1!x\"}",
			"{\"token\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"newPassword\":\"abcdefgh1!x\",\"newPasswordConfirm\":\"abcdefgh1!x\"}" })
	void invalidResetBodyIsInvalidRequestWithoutEchoingPassword(String body) throws Exception {
		MvcResult result = perform(RESET, body);

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("\"code\":\"INVALID_REQUEST\"")
			.doesNotContain("abcdefgh1!x")
			.doesNotContain("short1!");
		verifyNoInteractions(this.resetService);
	}

	@Test
	void invalidNewPasswordReportsFieldWithRuleMessage() throws Exception {
		MvcResult result = perform(RESET, "{\"token\":\"" + MAIL_TOKEN + "\",\"newPassword\":\"short1!\"}");

		assertBody(result, 400, "{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 값을 확인해 주세요.\",\"errors\":["
				+ "{\"field\":\"newPassword\",\"message\":\"비밀번호는 11~32자이며 영문, 숫자, 특수기호를 각각 1개 이상 포함해야 합니다.\"}]}");
	}

	private MvcResult perform(String path, String body) throws Exception {
		return this.mockMvc
			.perform(post(path).contentType(MediaType.APPLICATION_JSON)
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
