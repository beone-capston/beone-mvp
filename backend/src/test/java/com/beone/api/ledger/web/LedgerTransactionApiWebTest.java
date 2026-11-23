package com.beone.api.ledger.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.List;

import com.beone.api.ledger.application.LedgerCardNotFoundException;
import com.beone.api.ledger.application.LedgerMonthView;
import com.beone.api.ledger.application.LedgerQueryService;
import com.beone.api.ledger.application.LedgerSource;
import com.beone.api.support.WebSliceTest;
import com.beone.api.user.application.CurrentUser;
import com.beone.api.user.application.DemoUser;
import com.beone.api.user.application.DemoUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
 * 원장 조회 API의 응답 형식·실패 응답을 MockMvc로 확인한다. 기대 JSON은 docs/api/ledger.md 응답 예시와 실패 목록, docs/api.md 공통
 * 실패 응답(필수 쿼리 누락·타입 오류·시연 사용자 헤더 오류)에서 옮겼다.
 */
@WebSliceTest(controllers = LedgerTransactionController.class)
@ActiveProfiles("local")
class LedgerTransactionApiWebTest {

	private static final String HEADER = "X-BeONE-Demo-User";

	private static final String PATH = "/api/v1/me/ledger-transactions";

	private static final CurrentUser MY_CARDS = new CurrentUser(1L, DemoUser.MY_CARDS);

	private static final String INVALID_REQUEST_EMPTY = "{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 값을 확인해 주세요.\",\"errors\":[]}";

	private static final String CONTRACT_EXAMPLE = """
			{"data":{"month":"2026-11","items":[
			 {"ledgerTransactionId":"lt-9001","userCardId":"uc-1001","source":"SIMULATION","entryType":"APPROVAL",
			  "amountWon":10000,"transactionDate":"2026-11-03","transactionTime":null,"merchantClass":"STARBUCKS_STORE",
			  "paymentMethod":"DIRECT","transactionNature":null,"merchantName":"스타벅스 송도점","originalTransactionId":null,
			  "simulatedPaymentId":"pay-7001","attributionMonth":{"value":"2026-11","status":"CONFIRMED"}},
			 {"ledgerTransactionId":"lt-9002","userCardId":"uc-1001","source":"SIMULATION","entryType":"CANCELLATION",
			  "amountWon":-4000,"transactionDate":"2026-11-03","transactionTime":null,"merchantClass":"STARBUCKS_STORE",
			  "paymentMethod":"DIRECT","transactionNature":null,"merchantName":"스타벅스 송도점","originalTransactionId":"lt-9001",
			  "simulatedPaymentId":"pay-7001","attributionMonth":{"value":"2026-11","status":"CONFIRMED"}}],
			 "coverage":[{"userCardId":"uc-1001","source":"SIMULATION","period":{"from":"2026-11-01","to":"2026-11-30"},
			  "complete":true,"incompleteReasons":[]}]}}
			""";

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	LedgerQueryService ledgerQueryService;

	@MockitoBean
	DemoUserService demoUserService;

	@BeforeEach
	void resolveDemoUser() {
		given(this.demoUserService.currentUserOf(DemoUser.MY_CARDS)).willReturn(MY_CARDS);
	}

	@Test
	void returnsTheContractExample() throws Exception {
		given(this.ledgerQueryService.find(MY_CARDS, YearMonth.of(2026, 11), "uc-1001", LedgerSource.SIMULATION))
			.willReturn(contractView());

		assertJson(perform(get(PATH).param("month", "2026-11")
			.param("userCardId", "uc-1001")
			.param("source", "SIMULATION")
			.header(HEADER, "MY_CARDS")), CONTRACT_EXAMPLE, 200);
	}

	@Test
	void optionalParametersMayBeOmittedAndCodefCoverageIsNotComplete() throws Exception {
		given(this.ledgerQueryService.find(MY_CARDS, YearMonth.of(2026, 11), null, null))
			.willReturn(new LedgerMonthView("2026-11", List.of(), List.of(
					new LedgerMonthView.Coverage("uc-1001", "SIMULATION",
							new LedgerMonthView.Period("2026-11-01", "2026-11-30"), true, List.of()),
					new LedgerMonthView.Coverage("uc-1001", "CODEF",
							new LedgerMonthView.Period("2026-11-01", "2026-11-30"), false, List.of()))));

		assertJson(perform(get(PATH).param("month", "2026-11").header(HEADER, "MY_CARDS")), """
				{"data":{"month":"2026-11","items":[],"coverage":[
				 {"userCardId":"uc-1001","source":"SIMULATION","period":{"from":"2026-11-01","to":"2026-11-30"},"complete":true,"incompleteReasons":[]},
				 {"userCardId":"uc-1001","source":"CODEF","period":{"from":"2026-11-01","to":"2026-11-30"},"complete":false,"incompleteReasons":[]}]}}
				""", 200);
	}

	@Test
	void missingMonthIsARequiredFieldError() throws Exception {
		assertJson(perform(get(PATH).header(HEADER, "MY_CARDS")),
				"{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 값을 확인해 주세요.\",\"errors\":[{\"field\":\"month\",\"message\":\"필수 값입니다.\"}]}",
				400);
		verify(this.ledgerQueryService, never()).find(any(), any(), any(), any());
	}

	@Test
	void malformedMonthOrSourceIsAFormatError() throws Exception {
		for (String month : List.of("2026-1", "2026-13", "")) {
			assertJson(perform(get(PATH).param("month", month).header(HEADER, "MY_CARDS")),
					"{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 값을 확인해 주세요.\",\"errors\":[{\"field\":\"month\",\"message\":\"형식이 올바르지 않습니다.\"}]}",
					400);
		}
		for (String source : List.of("MANUAL", "simulation", "")) {
			assertJson(perform(get(PATH).param("month", "2026-11").param("source", source).header(HEADER, "MY_CARDS")),
					"{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 값을 확인해 주세요.\",\"errors\":[{\"field\":\"source\",\"message\":\"형식이 올바르지 않습니다.\"}]}",
					400);
		}
		verify(this.ledgerQueryService, never()).find(any(), any(), any(), any());
	}

	@Test
	void unknownCardIs404() throws Exception {
		given(this.ledgerQueryService.find(eq(MY_CARDS), any(), anyString(), any()))
			.willThrow(new LedgerCardNotFoundException());

		MvcResult result = perform(
				get(PATH).param("month", "2026-11").param("userCardId", "uc-SECRET-9").header(HEADER, "MY_CARDS"));

		assertJson(result,
				"{\"code\":\"USER_CARD_NOT_FOUND\",\"message\":\"보유 카드를 찾을 수 없습니다.\",\"errors\":[]}", 404);
		assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain("SECRET");
	}

	@Test
	void demoUserHeaderMissingOrUnknownIs400() throws Exception {
		assertJson(perform(get(PATH).param("month", "2026-11")), INVALID_REQUEST_EMPTY, 400);
		assertJson(perform(get(PATH).param("month", "2026-11").header(HEADER, "UNKNOWN")), INVALID_REQUEST_EMPTY, 400);
	}

	private static LedgerMonthView contractView() {
		return new LedgerMonthView("2026-11", List.of(
				new LedgerMonthView.Item("lt-9001", "uc-1001", "SIMULATION", "APPROVAL", 10000, "2026-11-03", null,
						"STARBUCKS_STORE", "DIRECT", null, "스타벅스 송도점", null, "pay-7001",
						new LedgerMonthView.AttributionMonth("2026-11", "CONFIRMED")),
				new LedgerMonthView.Item("lt-9002", "uc-1001", "SIMULATION", "CANCELLATION", -4000, "2026-11-03", null,
						"STARBUCKS_STORE", "DIRECT", null, "스타벅스 송도점", "lt-9001", "pay-7001",
						new LedgerMonthView.AttributionMonth("2026-11", "CONFIRMED"))),
				List.of(new LedgerMonthView.Coverage("uc-1001", "SIMULATION",
						new LedgerMonthView.Period("2026-11-01", "2026-11-30"), true, List.of())));
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
