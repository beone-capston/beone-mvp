package com.beone.api.monthlystate.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;

import com.beone.api.monthlystate.application.DatePeriod;
import com.beone.api.monthlystate.application.MonthlyStateCardNotFoundException;
import com.beone.api.monthlystate.application.MonthlyStateService;
import com.beone.api.monthlystate.application.SourceConflictNotFoundException;
import com.beone.api.monthlystate.application.SourceSelection;
import com.beone.api.monthlystate.application.StateSource;
import com.beone.api.support.WebSliceTest;
import com.beone.api.user.application.CurrentUser;
import com.beone.api.user.application.DemoUser;
import com.beone.api.user.application.DemoUserService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 출처 충돌 계산 기준 선택 API의 요청 검증·실패 응답을 MockMvc로 확인한다.
 *
 * <p>기대 JSON은 docs/api/monthly-states.md source-selections 절의 요청 예시·실패 목록, docs/api.md 공통 실패 응답과 업무 오류 코드
 * 표에서 옮겼다. 충돌이 없으면 항상 409 {@code SOURCE_CONFLICT_NOT_FOUND}인 것은 관리자 판단 P10이다.
 */
@WebSliceTest(controllers = MonthlyStateController.class)
@ActiveProfiles("local")
class SourceSelectionApiWebTest {

	private static final String PATH = "/api/v1/me/cards/uc-1001/monthly-states/2026-11/source-selections";

	private static final String HEADER = "X-BeONE-Demo-User";

	private static final CurrentUser MY_CARDS = new CurrentUser(1L, DemoUser.MY_CARDS);

	private static final CurrentUser PORTFOLIO = new CurrentUser(2L, DemoUser.VIRTUAL_PORTFOLIO);

	/** 계약 요청 예시. */
	private static final String CONTRACT_REQUEST = """
			{"item":"previousMonthPerformanceWon","period":{"from":"2026-10-01","to":"2026-10-31"},
			 "selectedSource":"MANUAL","conflictVersion":"cf-v3"}""";

	private static final String NOT_FOUND_CONFLICT = "{\"code\":\"SOURCE_CONFLICT_NOT_FOUND\",\"message\":\"출처 충돌이 없는 항목입니다.\",\"errors\":[]}";

	private static final String INVALID_REQUEST_EMPTY = "{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 값을 확인해 주세요.\",\"errors\":[]}";

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	MonthlyStateService monthlyStateService;

	@MockitoBean
	DemoUserService demoUserService;

	@BeforeEach
	void resolveDemoUsers() {
		given(this.demoUserService.currentUserOf(DemoUser.MY_CARDS)).willReturn(MY_CARDS);
		given(this.demoUserService.currentUserOf(DemoUser.VIRTUAL_PORTFOLIO)).willReturn(PORTFOLIO);
	}

	@Test
	void contractRequestWithoutConflictIsSourceConflictNotFound() throws Exception {
		willThrow(new SourceConflictNotFoundException()).given(this.monthlyStateService)
			.selectSource(eq(MY_CARDS), anyString(), any(), any());

		assertJson(perform(json(PATH, CONTRACT_REQUEST).header(HEADER, "MY_CARDS")), NOT_FOUND_CONFLICT, 409);
		verify(this.monthlyStateService).selectSource(MY_CARDS, "uc-1001", YearMonth.of(2026, 11),
				new SourceSelection("previousMonthPerformanceWon",
						new DatePeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)), StateSource.MANUAL,
						"cf-v3"));
	}

	@Test
	void virtualPortfolioIsNotReadOnlyHere() throws Exception {
		// docs/api.md: 월간 상태는 가상 포트폴리오에서도 동작한다. PORTFOLIO_READ_ONLY가 아니라 다른 사용자와 같은 판정이다
		willThrow(new SourceConflictNotFoundException()).given(this.monthlyStateService)
			.selectSource(eq(PORTFOLIO), anyString(), any(), any());

		assertJson(perform(json(PATH, CONTRACT_REQUEST).header(HEADER, "VIRTUAL_PORTFOLIO")), NOT_FOUND_CONFLICT,
				409);
	}

	@Test
	void missingCardIsNotFound() throws Exception {
		willThrow(new MonthlyStateCardNotFoundException()).given(this.monthlyStateService)
			.selectSource(eq(MY_CARDS), anyString(), any(), any());

		assertJson(perform(json(PATH, CONTRACT_REQUEST).header(HEADER, "MY_CARDS")),
				"{\"code\":\"USER_CARD_NOT_FOUND\",\"message\":\"보유 카드를 찾을 수 없습니다.\",\"errors\":[]}", 404);
	}

	@Test
	void serviceReturningWithoutConflictOutcomeIsNotDisguisedAsSuccess() throws Exception {
		// 충돌 후보가 생기기 전에는 200 응답을 만들지 않는다. 서비스가 예외 없이 돌아오면 성공으로 보이지 않게 500이다
		assertJson(perform(json(PATH, CONTRACT_REQUEST).header(HEADER, "MY_CARDS")),
				"{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"요청 처리 중 오류가 발생했습니다.\",\"errors\":[]}", 500);
	}

	@ParameterizedTest
	@ValueSource(strings = { "limitBucketUsage:B1", "otherBucketsUsedWon", "registeredOn" })
	void everyContractItemIsAccepted(String item) throws Exception {
		willThrow(new SourceConflictNotFoundException()).given(this.monthlyStateService)
			.selectSource(eq(MY_CARDS), anyString(), any(), any());

		assertJson(perform(json(PATH, CONTRACT_REQUEST.replace("previousMonthPerformanceWon", item))
			.header(HEADER, "MY_CARDS")), NOT_FOUND_CONFLICT, 409);
	}

	@ParameterizedTest
	@ValueSource(strings = { "CODEF", "INFERRED" })
	void everyContractSourceIsAccepted(String source) throws Exception {
		willThrow(new SourceConflictNotFoundException()).given(this.monthlyStateService)
			.selectSource(eq(MY_CARDS), anyString(), any(), any());

		assertJson(perform(json(PATH, CONTRACT_REQUEST.replace("\"MANUAL\"", "\"" + source + "\""))
			.header(HEADER, "MY_CARDS")), NOT_FOUND_CONFLICT, 409);
	}

	@Test
	void fieldRulesReportFieldPaths() throws Exception {
		String period = "\"period\":{\"from\":\"2026-10-01\",\"to\":\"2026-10-31\"}";
		assertFieldError("{" + period + ",\"selectedSource\":\"MANUAL\",\"conflictVersion\":\"cf-v3\"}", "item",
				"필수 값입니다.");
		assertFieldError("{\"item\":\"registeredOn\",\"selectedSource\":\"MANUAL\",\"conflictVersion\":\"cf-v3\"}",
				"period", "필수 값입니다.");
		assertFieldError("{\"item\":\"registeredOn\"," + period + ",\"conflictVersion\":\"cf-v3\"}", "selectedSource",
				"필수 값입니다.");
		assertFieldError("{\"item\":\"registeredOn\"," + period + ",\"selectedSource\":\"MANUAL\"}", "conflictVersion",
				"필수 값입니다.");
		assertFieldError(CONTRACT_REQUEST.replace("previousMonthPerformanceWon", "monthlyLimit"), "item",
				"허용되지 않는 값입니다.");
		assertFieldError(CONTRACT_REQUEST.replace("previousMonthPerformanceWon", "limitBucketUsage:"), "item",
				"허용되지 않는 값입니다.");
		assertFieldError(CONTRACT_REQUEST.replace("previousMonthPerformanceWon", "limitBucketUsage"), "item",
				"허용되지 않는 값입니다.");
		assertFieldError(CONTRACT_REQUEST.replace("\"previousMonthPerformanceWon\"", "1"), "item", "허용되지 않는 값입니다.");
		assertFieldError(CONTRACT_REQUEST.replace("\"MANUAL\"", "\"UNKNOWN\""), "selectedSource",
				"허용되지 않는 값입니다.");
		assertFieldError(CONTRACT_REQUEST.replace("\"MANUAL\"", "\"manual\""), "selectedSource",
				"허용되지 않는 값입니다.");
		assertFieldError(CONTRACT_REQUEST.replace("2026-10-01", "2026-11-01"), "period",
				"시작일은 종료일보다 늦을 수 없습니다.");
		verifyNoInteractions(this.monthlyStateService);
	}

	@ParameterizedTest
	@ValueSource(strings = { "{\"item\":\"registeredOn\",\"selectedSource\":\"MANUAL\",\"conflictVersion\":\"v\","
			+ "\"period\":{\"from\":\"2026-10-01\",\"to\":\"2026-10-01\"},\"selectedValue\":1}",
			// 날짜 형식 오류는 월간 상태 PUT과 같이 본문 역직렬화 오류다
			"{\"item\":\"registeredOn\",\"selectedSource\":\"MANUAL\",\"conflictVersion\":\"v\","
					+ "\"period\":{\"from\":\"2026-10-01\",\"to\":\"2026-10-1\"}}",
			"[]", "not json" })
	void malformedBodyIsBadRequestWithEmptyErrors(String body) throws Exception {
		assertJson(perform(json(PATH, body).header(HEADER, "MY_CARDS")), INVALID_REQUEST_EMPTY, 400);
		verifyNoInteractions(this.monthlyStateService);
	}

	@Test
	void invalidMonthIsBadRequestBeforeTheService() throws Exception {
		assertJson(perform(json(PATH.replace("2026-11", "2026-13"), CONTRACT_REQUEST).header(HEADER, "MY_CARDS")),
				"{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 값을 확인해 주세요.\",\"errors\":[{\"field\":\"month\",\"message\":\"형식이 올바르지 않습니다.\"}]}",
				400);
		verifyNoInteractions(this.monthlyStateService);
	}

	private void assertFieldError(String body, String field, String message) throws Exception {
		MvcResult result = perform(json(PATH, body).header(HEADER, "MY_CARDS"));
		assertThat(result.getResponse().getStatus()).as(body).isEqualTo(400);
		JsonAssert.comparator(JsonCompareMode.STRICT)
			.assertIsMatch("{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 값을 확인해 주세요.\",\"errors\":[{\"field\":"
					+ ((field == null) ? "null" : "\"" + field + "\"") + ",\"message\":\"" + message + "\"}]}",
					result.getResponse().getContentAsString(StandardCharsets.UTF_8));
	}

	private static MockHttpServletRequestBuilder json(String path, String body) {
		return put(path).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private MvcResult perform(MockHttpServletRequestBuilder request) throws Exception {
		return this.mockMvc.perform(request).andReturn();
	}

	private static void assertJson(MvcResult result, String expected, int status) throws Exception {
		assertThat(result.getResponse().getStatus()).isEqualTo(status);
		assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
		JsonAssert.comparator(JsonCompareMode.STRICT)
			.assertIsMatch(expected, result.getResponse().getContentAsString(StandardCharsets.UTF_8));
	}

}
