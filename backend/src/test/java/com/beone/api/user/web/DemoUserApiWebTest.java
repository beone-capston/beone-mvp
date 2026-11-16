package com.beone.api.user.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.beone.api.common.web.ApiResponse;
import com.beone.api.support.WebSliceTest;
import com.beone.api.user.application.CurrentUser;
import com.beone.api.user.application.DemoUser;
import com.beone.api.user.application.DemoUserProfile;
import com.beone.api.user.application.DemoUserService;
import com.beone.api.user.application.PortfolioType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * local 프로필의 시연 사용자 API·헤더 판정·CORS를 MockMvc로 확인한다.
 *
 * <p>기대 JSON은 docs/api/cards.md 응답 예시와 docs/api.md 공통 실패 응답, CORS 값은 D-23 11항에서 옮겼다.
 * 현재 사용자 주입은 테스트 전용 컨트롤러로 확인한다({@code GET /api/v1/me}는 보유 카드 작업에서 구현).
 */
@WebSliceTest(controllers = DemoUserController.class, properties = "logging.level.com.beone.api.user.web=DEBUG")
@ActiveProfiles("local")
@Import(DemoUserApiWebTest.CurrentUserProbeController.class)
@ExtendWith(OutputCaptureExtension.class)
class DemoUserApiWebTest {

	private static final String DEMO_USERS = "/api/v1/demo-users";

	private static final String PROBE = "/api/v1/test-current-user";

	private static final String FRONT_ORIGIN = "http://localhost:5173";

	private static final String INVALID_REQUEST_BODY = "{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 값을 확인해 주세요.\",\"errors\":[]}";

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	DemoUserService demoUserService;

	@Test
	void demoUsersReturnsContractBodyWithoutHeader() throws Exception {
		given(this.demoUserService.listDemoUsers()).willReturn(List.of(
				new DemoUserProfile(DemoUser.MY_CARDS, "내 카드", PortfolioType.REAL, false),
				new DemoUserProfile(DemoUser.VIRTUAL_PORTFOLIO, "가상 포트폴리오", PortfolioType.SYNTHETIC, true)));

		MvcResult result = this.mockMvc.perform(get(DEMO_USERS)).andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertJson(result, """
				{
				  "data": {
				    "users": [
				      {"demoUser": "MY_CARDS", "displayName": "내 카드", "portfolioType": "REAL", "readOnly": false},
				      {"demoUser": "VIRTUAL_PORTFOLIO", "displayName": "가상 포트폴리오", "portfolioType": "SYNTHETIC", "readOnly": true}
				    ]
				  }
				}
				""");
		verify(this.demoUserService, never()).currentUserOf(ArgumentMatchers.any());
	}

	@Test
	void currentUserIsResolvedFromMyCardsHeader() throws Exception {
		given(this.demoUserService.currentUserOf(DemoUser.MY_CARDS)).willReturn(new CurrentUser(1L, DemoUser.MY_CARDS));

		MvcResult result = this.mockMvc.perform(get(PROBE).header(DemoUserHeader.NAME, "MY_CARDS")).andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertJson(result, "{\"data\":{\"userId\":1,\"demoUser\":\"MY_CARDS\"}}");
	}

	@Test
	void currentUserIsResolvedFromVirtualPortfolioHeader() throws Exception {
		given(this.demoUserService.currentUserOf(DemoUser.VIRTUAL_PORTFOLIO))
			.willReturn(new CurrentUser(2L, DemoUser.VIRTUAL_PORTFOLIO));

		MvcResult result = this.mockMvc.perform(get(PROBE).header(DemoUserHeader.NAME, "VIRTUAL_PORTFOLIO"))
			.andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertJson(result, "{\"data\":{\"userId\":2,\"demoUser\":\"VIRTUAL_PORTFOLIO\"}}");
	}

	@Test
	void missingHeaderIsInvalidRequest(CapturedOutput output) throws Exception {
		MvcResult result = this.mockMvc.perform(get(PROBE)).andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertJson(result, INVALID_REQUEST_BODY);
		assertThat(output.getAll()).contains("API error code=INVALID_REQUEST status=400 "
				+ "exception=com.beone.api.user.web.InvalidDemoUserHeaderException");
		verify(this.demoUserService, never()).currentUserOf(ArgumentMatchers.any());
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "my_cards", "SECRET-DEMO-USER-4b2e", "MY_CARDS,VIRTUAL_PORTFOLIO" })
	void invalidHeaderValueIsInvalidRequestWithoutEchoingValue(String value, CapturedOutput output) throws Exception {
		MvcResult result = this.mockMvc.perform(get(PROBE).header(DemoUserHeader.NAME, value)).andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertJson(result, INVALID_REQUEST_BODY);
		assertThat(output.getAll()).doesNotContain("SECRET-DEMO-USER-4b2e");
		verify(this.demoUserService, never()).currentUserOf(ArgumentMatchers.any());
	}

	@Test
	void repeatedHeaderIsInvalidRequest() throws Exception {
		MvcResult result = this.mockMvc
			.perform(get(PROBE).header(DemoUserHeader.NAME, "MY_CARDS").header(DemoUserHeader.NAME, "MY_CARDS"))
			.andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertJson(result, INVALID_REQUEST_BODY);
	}

	@Test
	void preflightFromFrontOriginIsAllowed() throws Exception {
		MvcResult result = this.mockMvc
			.perform(preflight(FRONT_ORIGIN, "PATCH", "Content-Type, X-BeONE-Demo-User, Idempotency-Key"))
			.andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(FRONT_ORIGIN);
		assertThat(splitHeader(result, HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS))
			.containsExactlyInAnyOrder("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
		assertThat(splitHeader(result, HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
			.containsExactlyInAnyOrder("Content-Type", "X-BeONE-Demo-User", "Idempotency-Key");
		assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isNull();
	}

	@Test
	void simpleRequestFromFrontOriginGetsAllowOrigin() throws Exception {
		given(this.demoUserService.listDemoUsers()).willReturn(List.of());

		MvcResult result = this.mockMvc.perform(get(DEMO_USERS).header(HttpHeaders.ORIGIN, FRONT_ORIGIN)).andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(FRONT_ORIGIN);
		assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = { "http://evil.example", "http://localhost:3000", "https://localhost:5173",
			"http://127.0.0.1:5173" })
	void preflightFromOtherOriginIsRejected(String origin) throws Exception {
		MvcResult result = this.mockMvc.perform(preflight(origin, "GET", "X-BeONE-Demo-User")).andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(403);
		assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
	}

	@Test
	void preflightWithHeaderOutsideAllowListIsRejected() throws Exception {
		MvcResult result = this.mockMvc.perform(preflight(FRONT_ORIGIN, "GET", "Authorization")).andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(403);
		assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
	}

	@Test
	void preflightWithMethodOutsideAllowListIsRejected() throws Exception {
		MvcResult result = this.mockMvc.perform(preflight(FRONT_ORIGIN, "TRACE", "X-BeONE-Demo-User")).andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(403);
		assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
	}

	private static MockHttpServletRequestBuilder preflight(String origin, String method, String headers) {
		return options(DEMO_USERS).header(HttpHeaders.ORIGIN, origin)
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, headers);
	}

	private static List<String> splitHeader(MvcResult result, String name) {
		String value = result.getResponse().getHeader(name);
		assertThat(value).isNotNull();
		return List.of(value.split("\\s*,\\s*"));
	}

	private static void assertJson(MvcResult result, String expected) throws Exception {
		assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
		JsonAssert.comparator(JsonCompareMode.STRICT)
			.assertIsMatch(expected, result.getResponse().getContentAsString(StandardCharsets.UTF_8));
	}

	/** 현재 사용자 인자 주입만 확인하는 테스트 전용 컨트롤러. */
	@RestController
	static class CurrentUserProbeController {

		@GetMapping(PROBE)
		ApiResponse<CurrentUser> currentUser(CurrentUser currentUser) {
			return ApiResponse.of(currentUser);
		}

	}

}
