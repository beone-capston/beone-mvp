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
import com.beone.api.auth.application.MemberRegistrationService;
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
 * 회원가입·인증메일 재요청·이메일 인증 완료 API의 요청 검증과 응답 형식을 MockMvc로 확인한다.
 *
 * <p>기대값은 docs/api/auth.md(인증 명세 사본 반영)의 세 API 요청·응답 예시, 「값 검증」, 인증 오류 코드 표에서 옮겼다. 계정 처리
 * 결과는 서비스 대역으로 정하고, 실제 처리는 통합 테스트가 본다.
 */
@WebSliceTest(controllers = MemberRegistrationController.class)
@ActiveProfiles("local")
class MemberRegistrationApiWebTest {

	private static final String SIGN_UP = "/api/v1/auth/sign-up";

	private static final String RESEND = "/api/v1/auth/email-verification-requests";

	private static final String VERIFY = "/api/v1/auth/email-verifications";

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
	MemberRegistrationService registrationService;

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

	// 가입

	@Test
	void signUpIsAcceptedWithFixedMessage() throws Exception {
		MvcResult result = perform(SIGN_UP, "{\"email\":\" User@Example.com \",\"password\":\"" + PASSWORD + "\"}");

		assertBody(result, 202, ACCEPTED);
		assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
		verify(this.registrationService).signUp(" User@Example.com ", PASSWORD);
	}

	@Test
	void signUpWithoutCsrfIsForbiddenBeforeBodyValidation() throws Exception {
		MvcResult result = this.mockMvc
			.perform(post(SIGN_UP).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"not-email\"}"))
			.andReturn();

		assertBody(result, 403, CSRF_INVALID);
		verifyNoInteractions(this.registrationService);
	}

	@ParameterizedTest
	@ValueSource(strings = { "{\"password\":\"abcdefgh1!x\"}", "{\"email\":\"user@example.com\"}",
			"{\"email\":\"not-email\",\"password\":\"abcdefgh1!x\"}", "{\"email\":\"user@example.com\",\"password\":\"short1!\"}",
			"{\"email\":\"user@example.com\",\"password\":\"abcdefghijk!\"}",
			"{\"email\":\"user@example.com\",\"password\":\"abcd efgh1!x\"}",
			"{\"email\":\"user@example.com\",\"password\":\"abcdefgh1!x\",\"passwordConfirm\":\"abcdefgh1!x\"}",
			"{\"email\":\"user@example.com\",\"password\":\"abcdefgh1!x\",\"nickname\":\"a\"}", "{" })
	void invalidSignUpBodyIsInvalidRequestWithoutEchoingPassword(String body) throws Exception {
		MvcResult result = perform(SIGN_UP, body);

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(content).contains("\"code\":\"INVALID_REQUEST\"").doesNotContain("abcdefgh1!x").doesNotContain("short1!");
		assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
		verifyNoInteractions(this.registrationService);
	}

	@Test
	void invalidPasswordReportsFieldWithRuleMessage() throws Exception {
		MvcResult result = perform(SIGN_UP, "{\"email\":\"user@example.com\",\"password\":\"short1!\"}");

		assertBody(result, 400, "{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 값을 확인해 주세요.\",\"errors\":["
				+ "{\"field\":\"password\",\"message\":\"비밀번호는 11~32자이며 영문, 숫자, 특수기호를 각각 1개 이상 포함해야 합니다.\"}]}");
	}

	@Test
	void rateLimitedSignUpIs429WithRetryAfter() throws Exception {
		willThrow(new MailRateLimitedException(Duration.ofSeconds(42))).given(this.registrationService).signUp(any(), any());

		MvcResult result = perform(SIGN_UP, "{\"email\":\"user@example.com\",\"password\":\"" + PASSWORD + "\"}");

		assertBody(result, 429, RATE_LIMITED);
		assertThat(result.getResponse().getHeader("Retry-After")).isEqualTo("42");
		assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
	}

	// 인증메일 재요청

	@Test
	void resendIsAcceptedWithSameMessage() throws Exception {
		MvcResult result = perform(RESEND, "{\"email\":\"user@example.com\"}");

		assertBody(result, 202, ACCEPTED);
		verify(this.registrationService).requestVerificationEmail("user@example.com");
	}

	@ParameterizedTest
	@ValueSource(strings = { "{}", "{\"email\":\"user\"}", "{\"email\":\"user@example.com\",\"password\":\"abcdefgh1!x\"}" })
	void invalidResendBodyIsInvalidRequest(String body) throws Exception {
		MvcResult result = perform(RESEND, body);

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("\"code\":\"INVALID_REQUEST\"");
		verifyNoInteractions(this.registrationService);
	}

	@Test
	void rateLimitedResendIs429WithRetryAfter() throws Exception {
		willThrow(new MailRateLimitedException(Duration.ofSeconds(1))).given(this.registrationService)
			.requestVerificationEmail(any());

		MvcResult result = perform(RESEND, "{\"email\":\"user@example.com\"}");

		assertBody(result, 429, RATE_LIMITED);
		assertThat(result.getResponse().getHeader("Retry-After")).isEqualTo("1");
	}

	// 이메일 인증 완료

	@Test
	void verificationIsNoContent() throws Exception {
		MvcResult result = perform(VERIFY, "{\"token\":\"" + MAIL_TOKEN + "\"}");

		assertThat(result.getResponse().getStatus()).isEqualTo(204);
		assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).isEmpty();
		assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
		verify(this.registrationService).verifyEmail(MAIL_TOKEN);
	}

	@Test
	void invalidTokenIsAuthTokenInvalidWithoutEchoingToken() throws Exception {
		willThrow(new AuthTokenInvalidException()).given(this.registrationService).verifyEmail(any());

		MvcResult result = perform(VERIFY, "{\"token\":\"" + MAIL_TOKEN + "\"}");

		assertBody(result, 400, TOKEN_INVALID);
		assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain(MAIL_TOKEN);
	}

	@ParameterizedTest
	@ValueSource(strings = { "{}", "{\"token\":\"short\"}", "{\"token\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa=\"}",
			"{\"token\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"email\":\"user@example.com\"}" })
	void malformedTokenBodyIsInvalidRequest(String body) throws Exception {
		MvcResult result = perform(VERIFY, body);

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("\"code\":\"INVALID_REQUEST\"");
		verifyNoInteractions(this.registrationService);
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
