package com.beone.api.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.beone.api.auth.application.AuthSessionService;
import com.beone.api.auth.application.MemberRequestCheck;
import com.beone.api.auth.application.SessionTokens;
import com.beone.api.common.web.ApiResponse;
import com.beone.api.support.WebSliceTest;
import com.beone.api.user.application.MemberUserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로컬 회원 프로필 {@code member}의 업무 경로({@code /api/v1/me}, {@code /api/v1/me/**}) 응답에 {@code Cache-Control: no-store}가
 * 붙는지 MockMvc로 확인한다.
 *
 * <p>기대값은 G1 D5 권장 4항(401에도 no-store)과 인증 API의 no-store 규칙(docs/api/auth.md, 모든 응답)이다. 실제 업무 컨트롤러 대신
 * 테스트 전용 컨트롤러로 401·403·쿠키를 다시 심는 200·미등록 404를 만든다. 실제 HTTP 확인은 {@code MemberProfileIntegrationTest}가 한다.
 */
@WebSliceTest(controllers = MemberApiNoStoreWebTest.ProbeController.class)
@ActiveProfiles("member")
@Import(MemberApiNoStoreWebTest.ProbeController.class)
class MemberApiNoStoreWebTest {

	private static final String ME = "/api/v1/me";

	private static final String PROBE = "/api/v1/me/test-probe";

	private static final String UNAUTHENTICATED = "{\"code\":\"UNAUTHENTICATED\",\"message\":\"로그인이 필요합니다.\",\"errors\":[]}";

	private static final String CSRF_INVALID = "{\"code\":\"CSRF_INVALID\",\"message\":\"요청을 확인할 수 없습니다. 화면을 새로고침한 후 다시 시도해 주세요.\",\"errors\":[]}";

	private static final String SESSION_ID = SessionTokens.newSessionId();

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	AuthSessionService authSessionService;

	// member 프로필의 현재 사용자 해석기가 슬라이스에 함께 올라오므로 그 의존성만 대신한다. 테스트 컨트롤러는 현재 사용자를 쓰지 않는다.
	@MockitoBean
	MemberUserService memberUserService;

	@Test
	void unauthenticatedResponseIsNoStore() throws Exception {
		given(this.authSessionService.checkMemberRequest(isNull(), isNull(), anyBoolean()))
			.willReturn(new MemberRequestCheck.Unauthenticated());

		MvcResult result = this.mockMvc.perform(get(ME)).andReturn();

		assertBody(result, 401, UNAUTHENTICATED);
		assertNoStore(result);
	}

	@Test
	void csrfInvalidResponseIsNoStore() throws Exception {
		given(this.authSessionService.checkMemberRequest(any(), isNull(), anyBoolean()))
			.willReturn(new MemberRequestCheck.CsrfInvalid());

		MvcResult result = this.mockMvc.perform(post(PROBE).cookie(sessionCookie())).andReturn();

		assertBody(result, 403, CSRF_INVALID);
		assertNoStore(result);
	}

	@Test
	void authenticatedResponseThatReissuesCookieIsNoStore() throws Exception {
		given(this.authSessionService.checkMemberRequest(any(), any(), anyBoolean()))
			.willReturn(new MemberRequestCheck.Authenticated("usr-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", SESSION_ID,
					Duration.ofDays(7)));

		for (String path : new String[] { ME, PROBE }) {
			MvcResult result = this.mockMvc.perform(get(path).cookie(sessionCookie())).andReturn();

			assertThat(result.getResponse().getStatus()).as(path).isEqualTo(200);
			assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).as(path).hasSize(1);
			assertNoStore(result);
		}
	}

	@Test
	void unregisteredMemberPathNotFoundIsNoStore() throws Exception {
		MvcResult result = this.mockMvc.perform(get("/api/v1/me/not-registered")).andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(404);
		assertNoStore(result);
	}

	private static Cookie sessionCookie() {
		return new Cookie("__Host-BEONE_SESSION", SESSION_ID);
	}

	private static void assertNoStore(MvcResult result) {
		assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
	}

	private static void assertBody(MvcResult result, int status, String expected) throws Exception {
		assertThat(result.getResponse().getStatus()).isEqualTo(status);
		JsonAssert.comparator(JsonCompareMode.STRICT)
			.assertIsMatch(expected, result.getResponse().getContentAsString(StandardCharsets.UTF_8));
	}

	@RestController
	static class ProbeController {

		@GetMapping({ ME, PROBE })
		ApiResponse<String> read() {
			return ApiResponse.of("ok");
		}

		@PostMapping(PROBE)
		ApiResponse<String> write() {
			return ApiResponse.of("ok");
		}

	}

}
