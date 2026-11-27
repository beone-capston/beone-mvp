package com.beone.api.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.beone.api.auth.application.AuthSessionService;
import com.beone.api.auth.application.MemberRequestCheck;
import com.beone.api.auth.application.MemberWithdrawalService;
import com.beone.api.auth.application.SessionTokens;
import com.beone.api.auth.application.WithdrawalResult;
import com.beone.api.support.WebSliceTest;
import com.beone.api.user.application.MemberUserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 회원 탈퇴 {@code DELETE /api/v1/me}의 웹 계약(결정 D10)을 MockMvc로 확인한다. 기대값은 관리자 T5 지시와 PR '결정 필요' 권장안이다.
 * 실제 삭제는 {@code MemberWithdrawalIntegrationTest}가 확인한다.
 */
@WebSliceTest(controllers = MemberWithdrawalController.class)
@ActiveProfiles("member")
class MemberWithdrawalApiWebTest {

	private static final String ME = "/api/v1/me";

	private static final String MEMBER = "usr-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

	private static final String SESSION_ID = SessionTokens.newSessionId();

	private static final String BODY = "{\"password\":\"abcdefgh1!x\"}";

	private static final String UNAUTHENTICATED = "{\"code\":\"UNAUTHENTICATED\",\"message\":\"로그인이 필요합니다.\",\"errors\":[]}";

	private static final String CSRF_INVALID = "{\"code\":\"CSRF_INVALID\",\"message\":\"요청을 확인할 수 없습니다. 화면을 새로고침한 후 다시 시도해 주세요.\",\"errors\":[]}";

	private static final String PASSWORD_MISMATCH = "{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 값을 확인해 주세요.\","
			+ "\"errors\":[{\"field\":\"password\",\"message\":\"비밀번호가 일치하지 않습니다.\"}]}";

	private static final String RATE_LIMITED = "{\"code\":\"RATE_LIMITED\",\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.\",\"errors\":[]}";

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	AuthSessionService authSessionService;

	@MockitoBean
	MemberWithdrawalService withdrawalService;

	// member 프로필의 현재 사용자 해석기가 슬라이스에 함께 올라오므로 그 의존성만 대신한다
	@MockitoBean
	MemberUserService memberUserService;

	@Test
	void unauthenticatedComesFirst() throws Exception {
		given(this.authSessionService.checkMemberRequest(isNull(), any(), anyBoolean()))
			.willReturn(new MemberRequestCheck.Unauthenticated());

		MvcResult result = this.mockMvc.perform(request("{}").header("X-CSRF-TOKEN", "x")).andReturn();

		assertBody(result, 401, UNAUTHENTICATED);
		verify(this.withdrawalService, never()).withdraw(anyString(), anyString());
	}

	@Test
	void csrfIsCheckedAsAWriteBeforeTheBody() throws Exception {
		given(this.authSessionService.checkMemberRequest(any(), isNull(), anyBoolean()))
			.willReturn(new MemberRequestCheck.CsrfInvalid());

		MvcResult result = this.mockMvc.perform(request("{}").cookie(sessionCookie())).andReturn();

		assertBody(result, 403, CSRF_INVALID);
		verify(this.authSessionService).checkMemberRequest(SESSION_ID, null, true);
	}

	@Test
	void invalidBodyIs400AndNeverReachesTheService() throws Exception {
		authenticated();

		for (String body : new String[] { "{}", "{\"password\":\"\"}", "{\"password\":\"" + "a".repeat(33) + "\"}",
				"{\"password\":\"abcdefgh1!x\",\"email\":\"a@example.com\"}" }) {
			MvcResult result = this.mockMvc.perform(request(body).cookie(sessionCookie()).header("X-CSRF-TOKEN", "t"))
				.andReturn();
			assertThat(result.getResponse().getStatus()).as(body).isEqualTo(400);
			assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).as(body)
				.contains("\"code\":\"INVALID_REQUEST\"");
		}
		verify(this.withdrawalService, never()).withdraw(anyString(), anyString());
	}

	@Test
	void withdrawnIs204AndClearsTheSessionCookie() throws Exception {
		authenticated();
		given(this.withdrawalService.withdraw(MEMBER, "abcdefgh1!x")).willReturn(new WithdrawalResult.Withdrawn());

		MvcResult result = this.mockMvc.perform(request(BODY).cookie(sessionCookie()).header("X-CSRF-TOKEN", "t"))
			.andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(204);
		assertThat(result.getResponse().getContentAsString()).isEmpty();
		assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).singleElement()
			.satisfies(cookie -> assertThat(cookie).contains("Max-Age=0"));
		assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
	}

	@Test
	void passwordMismatchIsAFieldErrorNot401() throws Exception {
		authenticated();
		given(this.withdrawalService.withdraw(MEMBER, "abcdefgh1!x"))
			.willReturn(new WithdrawalResult.PasswordMismatch());

		MvcResult result = this.mockMvc.perform(request(BODY).cookie(sessionCookie()).header("X-CSRF-TOKEN", "t"))
			.andReturn();

		assertBody(result, 400, PASSWORD_MISMATCH);
		// 세션은 유지되므로 갱신 쿠키(지우는 쿠키 아님)가 남는다
		assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).singleElement()
			.satisfies(cookie -> assertThat(cookie).doesNotContain("Max-Age=0"));
	}

	@Test
	void lockedIs429WithRetryAfter() throws Exception {
		authenticated();
		given(this.withdrawalService.withdraw(MEMBER, "abcdefgh1!x"))
			.willReturn(new WithdrawalResult.RateLimited(Duration.ofSeconds(840)));

		MvcResult result = this.mockMvc.perform(request(BODY).cookie(sessionCookie()).header("X-CSRF-TOKEN", "t"))
			.andReturn();

		assertBody(result, 429, RATE_LIMITED);
		assertThat(result.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("840");
	}

	@Test
	void memberGoneIs401AndClearsTheCookie() throws Exception {
		authenticated();
		given(this.withdrawalService.withdraw(MEMBER, "abcdefgh1!x")).willReturn(new WithdrawalResult.MemberGone());

		MvcResult result = this.mockMvc.perform(request(BODY).cookie(sessionCookie()).header("X-CSRF-TOKEN", "t"))
			.andReturn();

		assertBody(result, 401, UNAUTHENTICATED);
		assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).singleElement()
			.satisfies(cookie -> assertThat(cookie).contains("Max-Age=0"));
	}

	private void authenticated() {
		given(this.authSessionService.checkMemberRequest(any(), any(), anyBoolean()))
			.willReturn(new MemberRequestCheck.Authenticated(MEMBER, SESSION_ID, Duration.ofDays(7)));
	}

	private static MockHttpServletRequestBuilder request(String body) {
		return delete(ME).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static Cookie sessionCookie() {
		return new Cookie("__Host-BEONE_SESSION", SESSION_ID);
	}

	private static void assertBody(MvcResult result, int status, String expected) throws Exception {
		assertThat(result.getResponse().getStatus()).isEqualTo(status);
		JsonAssert.comparator(JsonCompareMode.STRICT)
			.assertIsMatch(expected, result.getResponse().getContentAsString(StandardCharsets.UTF_8));
	}

}
