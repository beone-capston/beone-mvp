package com.beone.api.codef.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import com.beone.api.codef.application.CodefCardVerificationNotSupportedException;
import com.beone.api.codef.application.CodefConnectResult;
import com.beone.api.codef.application.CodefConnectionService;
import com.beone.api.codef.application.CodefConnectionStatus;
import com.beone.api.codef.application.CodefConnectionView;
import com.beone.api.codef.application.CodefLoginFailedException;
import com.beone.api.codef.application.CodefNotConnectedException;
import com.beone.api.codef.application.CodefPortfolioReadOnlyException;
import com.beone.api.codef.application.CodefUnavailableException;
import com.beone.api.support.WebSliceTest;
import com.beone.api.user.application.CurrentUser;
import com.beone.api.user.application.DemoUser;
import com.beone.api.user.application.DemoUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * CODEF 연결 GET·POST의 응답 형식·요청 검증·실패 응답을 MockMvc로 확인한다.
 *
 * <p>기대 JSON은 docs/api/codef.md 응답 예시(organization {@code 0301}, connectedAt·lastSyncedAt, reconnectRequired)와 실패 목록,
 * docs/api.md 공통 실패 응답에서 옮겼다. 비밀번호·아이디가 응답에 나오지 않는지도 본다.
 */
@WebSliceTest(controllers = CodefConnectionController.class)
@ActiveProfiles("local")
class CodefConnectionApiWebTest {

	private static final String PATH = "/api/v1/me/codef/connection";

	private static final String HEADER = "X-BeONE-Demo-User";

	private static final String INVALID_REQUEST_EMPTY = "{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 값을 확인해 주세요.\",\"errors\":[]}";

	private static final String BODY = """
			{"organization":"0301","loginType":"ID","loginId":"kbuser01","password":"p@ss-word-9"}
			""";

	private static final CurrentUser MY_CARDS = new CurrentUser(1L, DemoUser.MY_CARDS);

	private static final CurrentUser PORTFOLIO = new CurrentUser(2L, DemoUser.VIRTUAL_PORTFOLIO);

	private static final CodefConnectionView CONNECTED = new CodefConnectionView("0301",
			CodefConnectionStatus.CONNECTED, LocalDateTime.of(2026, 11, 4, 9, 0), null);

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	CodefConnectionService codefConnectionService;

	@MockitoBean
	DemoUserService demoUserService;

	@BeforeEach
	void resolveDemoUsers() {
		given(this.demoUserService.currentUserOf(DemoUser.MY_CARDS)).willReturn(MY_CARDS);
		given(this.demoUserService.currentUserOf(DemoUser.VIRTUAL_PORTFOLIO)).willReturn(PORTFOLIO);
	}

	// GET

	@Test
	void getReturnsContractBody() throws Exception {
		given(this.codefConnectionService.get(MY_CARDS)).willReturn(new CodefConnectionView("0301",
				CodefConnectionStatus.CONNECTED, LocalDateTime.of(2026, 11, 4, 9, 0), LocalDateTime.of(2026, 11, 4, 9, 1)));

		assertJson(perform(get(PATH).header(HEADER, "MY_CARDS")), """
				{"data":{"organization":"0301","status":"CONNECTED","connectedAt":"2026-11-04T09:00:00+09:00",
				 "lastSyncedAt":"2026-11-04T09:01:00+09:00","reconnectRequired":false}}
				""", 200);
	}

	@Test
	void getNotConnectedKeepsNulls() throws Exception {
		given(this.codefConnectionService.get(PORTFOLIO)).willReturn(CodefConnectionView.NOT_CONNECTED);

		assertJson(perform(get(PATH).header(HEADER, "VIRTUAL_PORTFOLIO")), """
				{"data":{"organization":"0301","status":"NOT_CONNECTED","connectedAt":null,"lastSyncedAt":null,
				 "reconnectRequired":false}}
				""", 200);
	}

	@Test
	void getReconnectRequiredSetsTheFlag() throws Exception {
		given(this.codefConnectionService.get(MY_CARDS)).willReturn(new CodefConnectionView("0301",
				CodefConnectionStatus.RECONNECT_REQUIRED, LocalDateTime.of(2026, 11, 4, 9, 0), null));

		assertJson(perform(get(PATH).header(HEADER, "MY_CARDS")), """
				{"data":{"organization":"0301","status":"RECONNECT_REQUIRED","connectedAt":"2026-11-04T09:00:00+09:00",
				 "lastSyncedAt":null,"reconnectRequired":true}}
				""", 200);
	}

	// POST

	@Test
	void firstConnectionIs201WithContractBodyAndNoCredentials() throws Exception {
		given(this.codefConnectionService.connect(eq(MY_CARDS), eq("kbuser01"), any()))
			.willReturn(new CodefConnectResult(CONNECTED, true));

		MvcResult result = perform(json(post(PATH), BODY).header(HEADER, "MY_CARDS"));

		assertJson(result, """
				{"data":{"organization":"0301","status":"CONNECTED","connectedAt":"2026-11-04T09:00:00+09:00",
				 "lastSyncedAt":null,"reconnectRequired":false}}
				""", 201);
		assertThat(body(result)).doesNotContain("kbuser01").doesNotContain("p@ss-word-9");
	}

	@Test
	void reconnectionIs200WithTheSameBodyShape() throws Exception {
		given(this.codefConnectionService.connect(eq(MY_CARDS), eq("kbuser01"), any()))
			.willReturn(new CodefConnectResult(CONNECTED, false));

		assertJson(perform(json(post(PATH), BODY).header(HEADER, "MY_CARDS")), """
				{"data":{"organization":"0301","status":"CONNECTED","connectedAt":"2026-11-04T09:00:00+09:00",
				 "lastSyncedAt":null,"reconnectRequired":false}}
				""", 200);
	}

	@Test
	void missingFieldsAreFieldErrorsWithoutValues() throws Exception {
		assertFieldError("{\"loginType\":\"ID\",\"loginId\":\"kbuser01\",\"password\":\"p@ss-word-9\"}", "organization",
				"필수 값입니다.");
		assertFieldError("{\"organization\":\"0301\",\"loginId\":\"kbuser01\",\"password\":\"p@ss-word-9\"}", "loginType",
				"필수 값입니다.");
		assertFieldError("{\"organization\":\"0301\",\"loginType\":\"ID\",\"password\":\"p@ss-word-9\"}", "loginId",
				"필수 값입니다.");
		assertFieldError("{\"organization\":\"0301\",\"loginType\":\"ID\",\"loginId\":\"kbuser01\",\"password\":null}",
				"password", "필수 값입니다.");
		verifyNoInteractions(this.codefConnectionService);
	}

	@Test
	void disallowedOrganizationOrLoginTypeIsRejected() throws Exception {
		assertFieldError("{\"organization\":\"0302\",\"loginType\":\"ID\",\"loginId\":\"kbuser01\",\"password\":\"p@ss-word-9\"}",
				"organization", "허용되지 않는 값입니다.");
		assertFieldError("{\"organization\":\"0301\",\"loginType\":\"CERT\",\"loginId\":\"kbuser01\",\"password\":\"p@ss-word-9\"}",
				"loginType", "허용되지 않는 값입니다.");
		assertFieldError("{\"organization\":\"0301\",\"loginType\":\"id\",\"loginId\":\"kbuser01\",\"password\":\"p@ss-word-9\"}",
				"loginType", "허용되지 않는 값입니다.");
		verifyNoInteractions(this.codefConnectionService);
	}

	// JSON 이스케이프로 넣은 아이디. 제어·서식·대체 문자, 줄·문단 구분자, 앞뒤 공백(전각·NBSP 포함)
	@ParameterizedTest
	@ValueSource(strings = { "kb\\u0000user01", "kb\\tuser01", "kbuser01\\n", "kb\\u001Fuser01", "kb\\u007Fuser01",
			"kb\\u200Buser01", "\\uFEFFkbuser01", "kb\\uFFFDuser01", "kb\\u2028user01", " kbuser01", "kbuser01 ",
			"\\u00A0kbuser01", "kbuser01\\u3000", "kb\\uD800user01" })
	void loginIdWithControlFormatOrEdgeSpaceIsRejectedBeforeCodef(String loginId) throws Exception {
		MvcResult result = perform(json(post(PATH),
				"{\"organization\":\"0301\",\"loginType\":\"ID\",\"loginId\":\"" + loginId + "\",\"password\":\"p@ss-word-9\"}")
			.header(HEADER, "MY_CARDS"));

		assertJson(result, """
				{"code":"INVALID_REQUEST","message":"요청 값을 확인해 주세요.",
				 "errors":[{"field":"loginId","message":"사용할 수 없는 문자가 있습니다."}]}
				""", 400);
		assertThat(body(result)).doesNotContain("kbuser01").doesNotContain("user01").doesNotContain("p@ss-word-9");
		// 서비스(게이트웨이·호출 총량)까지 가지 않는다
		verifyNoInteractions(this.codefConnectionService);
	}

	@ParameterizedTest
	@ValueSource(strings = { "zqx\\u0000vkj", "zqx\\nvkj", "zqx\\tvkj", "zqx\\u007Fvkj", "\\u0085zqxvkj" })
	void passwordWithControlCharacterIsRejectedWithoutHints(String password) throws Exception {
		MvcResult result = perform(json(post(PATH),
				"{\"organization\":\"0301\",\"loginType\":\"ID\",\"loginId\":\"kbuser01\",\"password\":\"" + password + "\"}")
			.header(HEADER, "MY_CARDS"));

		// 필드 이름과 고정 문구만. 값·길이·위치는 없다
		assertJson(result, """
				{"code":"INVALID_REQUEST","message":"요청 값을 확인해 주세요.",
				 "errors":[{"field":"password","message":"사용할 수 없는 문자가 있습니다."}]}
				""", 400);
		assertThat(body(result)).doesNotContain("zqx").doesNotContain("vkj").doesNotContain("kbuser01");
		verifyNoInteractions(this.codefConnectionService);
	}

	@Test
	void interiorSpaceInLoginIdAndEdgeSpacesInPasswordAreAccepted() throws Exception {
		given(this.codefConnectionService.connect(eq(MY_CARDS), eq("kb user01"), any()))
			.willReturn(new CodefConnectResult(CONNECTED, true));

		MvcResult result = perform(json(post(PATH),
				"{\"organization\":\"0301\",\"loginType\":\"ID\",\"loginId\":\"kb user01\",\"password\":\" p@ss word \"}")
			.header(HEADER, "MY_CARDS"));

		assertThat(result.getResponse().getStatus()).isEqualTo(201);
		ArgumentCaptor<char[]> password = ArgumentCaptor.forClass(char[].class);
		verify(this.codefConnectionService).connect(eq(MY_CARDS), eq("kb user01"), password.capture());
		// 비밀번호 앞뒤 공백은 그대로 넘긴다
		assertThat(new String(password.getValue())).isEqualTo(" p@ss word ");
	}

	@ParameterizedTest
	@ValueSource(strings = { "{\"organization\":301,\"loginType\":\"ID\",\"loginId\":\"a\",\"password\":\"b\"}",
			"{\"organization\":\"0301\",\"loginType\":\"ID\",\"loginId\":\"a\",\"password\":1234}",
			"{\"organization\":\"0301\",\"loginType\":\"ID\",\"loginId\":true,\"password\":\"b\"}",
			"{\"organization\":\"0301\",\"loginType\":\"ID\",\"loginId\":\"a\",\"password\":[\"b\"]}", "[]", "{", "" })
	void nonStringValuesAndMalformedBodiesAreRejectedWithoutDetails(String body) throws Exception {
		MvcResult result = perform(json(post(PATH), body).header(HEADER, "MY_CARDS"));

		assertJson(result, INVALID_REQUEST_EMPTY, 400);
		verifyNoInteractions(this.codefConnectionService);
	}

	@Test
	void loginFailureIs400() throws Exception {
		assertFailure(new CodefLoginFailedException(),
				"{\"code\":\"CODEF_LOGIN_FAILED\",\"message\":\"카드사 로그인에 실패했습니다.\",\"errors\":[]}", 400);
	}

	@Test
	void cardVerificationIs409() throws Exception {
		assertFailure(new CodefCardVerificationNotSupportedException(), """
				{"code":"CODEF_CARD_VERIFICATION_NOT_SUPPORTED",
				 "message":"카드소지확인이 필요한 계정은 지원하지 않습니다. 수동 입력을 이용해 주세요.","errors":[]}
				""", 409);
	}

	@Test
	void unavailableIs502() throws Exception {
		assertFailure(new CodefUnavailableException(), """
				{"code":"CODEF_UNAVAILABLE","message":"CODEF 응답을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.","errors":[]}
				""", 502);
	}

	@Test
	void portfolioUserIs409ReadOnly() throws Exception {
		given(this.codefConnectionService.connect(eq(PORTFOLIO), any(), any()))
			.willThrow(new CodefPortfolioReadOnlyException());

		assertJson(perform(json(post(PATH), BODY).header(HEADER, "VIRTUAL_PORTFOLIO")), """
				{"code":"PORTFOLIO_READ_ONLY","message":"가상 포트폴리오에서는 CODEF 연결을 바꿀 수 없습니다.","errors":[]}
				""", 409);
	}

	@Test
	void unexpectedFailureIs500WithoutDetails() throws Exception {
		given(this.codefConnectionService.connect(eq(MY_CARDS), any(), any()))
			.willThrow(new IllegalStateException("secret detail"));

		MvcResult result = perform(json(post(PATH), BODY).header(HEADER, "MY_CARDS"));

		assertJson(result, "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"요청 처리 중 오류가 발생했습니다.\",\"errors\":[]}",
				500);
		assertThat(body(result)).doesNotContain("secret").doesNotContain("p@ss-word-9");
	}

	// DELETE

	@Test
	void disconnectIs204WithoutBody() throws Exception {
		MvcResult result = perform(delete(PATH).header(HEADER, "MY_CARDS"));

		assertThat(result.getResponse().getStatus()).isEqualTo(204);
		assertThat(body(result)).isEmpty();
		verify(this.codefConnectionService).disconnect(MY_CARDS);
	}

	@Test
	void disconnectWithoutConnectionIs409() throws Exception {
		willThrow(new CodefNotConnectedException()).given(this.codefConnectionService).disconnect(MY_CARDS);

		assertJson(perform(delete(PATH).header(HEADER, "MY_CARDS")),
				"{\"code\":\"CODEF_NOT_CONNECTED\",\"message\":\"CODEF가 연결되어 있지 않습니다.\",\"errors\":[]}", 409);
	}

	@Test
	void portfolioDisconnectIs409ReadOnly() throws Exception {
		willThrow(new CodefPortfolioReadOnlyException()).given(this.codefConnectionService).disconnect(PORTFOLIO);

		assertJson(perform(delete(PATH).header(HEADER, "VIRTUAL_PORTFOLIO")), """
				{"code":"PORTFOLIO_READ_ONLY","message":"가상 포트폴리오에서는 CODEF 연결을 바꿀 수 없습니다.","errors":[]}
				""", 409);
	}

	@Test
	void unexpectedDisconnectFailureIs500WithoutDetails() throws Exception {
		willThrow(new IllegalStateException("secret detail")).given(this.codefConnectionService).disconnect(MY_CARDS);

		MvcResult result = perform(delete(PATH).header(HEADER, "MY_CARDS"));

		assertJson(result, "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"요청 처리 중 오류가 발생했습니다.\",\"errors\":[]}",
				500);
		assertThat(body(result)).doesNotContain("secret");
	}

	@Test
	void rejectsMissingOrInvalidDemoUserHeader() throws Exception {
		assertJson(perform(get(PATH)), INVALID_REQUEST_EMPTY, 400);
		assertJson(perform(json(post(PATH), BODY)), INVALID_REQUEST_EMPTY, 400);
		assertJson(perform(delete(PATH)), INVALID_REQUEST_EMPTY, 400);
		for (String invalid : List.of("my_cards", "UNKNOWN", "")) {
			assertJson(perform(get(PATH).header(HEADER, invalid)), INVALID_REQUEST_EMPTY, 400);
			assertJson(perform(json(post(PATH), BODY).header(HEADER, invalid)), INVALID_REQUEST_EMPTY, 400);
			assertJson(perform(delete(PATH).header(HEADER, invalid)), INVALID_REQUEST_EMPTY, 400);
		}
		verifyNoInteractions(this.codefConnectionService);
	}

	@Test
	void requestTextHidesCredentials() {
		CodefConnectionRequest request = new CodefConnectionRequest();
		request.setLoginId("kbuser01");
		request.setPassword("p@ss-word-9".toCharArray());

		assertThat(request.toString()).doesNotContain("kbuser01").doesNotContain("p@ss-word-9");
	}

	private void assertFailure(RuntimeException exception, String expected, int status) throws Exception {
		given(this.codefConnectionService.connect(eq(MY_CARDS), any(), any())).willThrow(exception);

		MvcResult result = perform(json(post(PATH), BODY).header(HEADER, "MY_CARDS"));

		assertJson(result, expected, status);
		assertThat(body(result)).doesNotContain("kbuser01").doesNotContain("p@ss-word-9");
	}

	private void assertFieldError(String body, String field, String message) throws Exception {
		MvcResult result = perform(json(post(PATH), body).header(HEADER, "MY_CARDS"));

		assertJson(result, """
				{"code":"INVALID_REQUEST","message":"요청 값을 확인해 주세요.",
				 "errors":[{"field":"%s","message":"%s"}]}
				""".formatted(field, message), 400);
		assertThat(body(result)).doesNotContain("kbuser01").doesNotContain("p@ss-word-9");
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private MvcResult perform(MockHttpServletRequestBuilder request) throws Exception {
		return this.mockMvc.perform(request).andReturn();
	}

	private static String body(MvcResult result) throws Exception {
		return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	private static void assertJson(MvcResult result, String expected, int status) throws Exception {
		assertThat(result.getResponse().getStatus()).isEqualTo(status);
		assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
		JsonAssert.comparator(JsonCompareMode.STRICT).assertIsMatch(expected, body(result));
	}

}
