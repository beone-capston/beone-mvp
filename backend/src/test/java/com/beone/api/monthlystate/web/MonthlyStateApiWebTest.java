package com.beone.api.monthlystate.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import com.beone.api.monthlystate.application.DatePeriod;
import com.beone.api.monthlystate.application.MappingRequiredException;
import com.beone.api.monthlystate.application.MonthlyStateCardNotFoundException;
import com.beone.api.monthlystate.application.MonthlyStateInput;
import com.beone.api.monthlystate.application.MonthlyStateService;
import com.beone.api.monthlystate.application.MonthlyStateView;
import com.beone.api.monthlystate.application.RuleMismatchException;
import com.beone.api.monthlystate.application.ServiceSelectionStatus;
import com.beone.api.monthlystate.application.StateSource;
import com.beone.api.monthlystate.application.StateValue;
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
 * 월간 상태 API 2개의 요청 검증·응답 형식·실패 응답을 MockMvc로 확인한다.
 *
 * <p>기대 JSON은 docs/api/monthly-states.md 응답 예시와 실패 목록, docs/api.md 공통 실패 응답에서 옮겼다. 계약에 없던 값은 결정 27
 * A3절을 따른다.
 */
@WebSliceTest(controllers = MonthlyStateController.class)
@ActiveProfiles("local")
class MonthlyStateApiWebTest {

	private static final String PATH = "/api/v1/me/cards/uc-1001/monthly-states/2026-11";

	private static final String HEADER = "X-BeONE-Demo-User";

	private static final CurrentUser MY_CARDS = new CurrentUser(1L, DemoUser.MY_CARDS);

	private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);

	private static final LocalDateTime SNAPSHOT = LocalDateTime.of(2026, 11, 3, 10, 20, 0);

	private static final DatePeriod OCTOBER = new DatePeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

	private static final DatePeriod NOVEMBER_PERIOD = new DatePeriod(LocalDate.of(2026, 11, 1),
			LocalDate.of(2026, 11, 30));

	private static final String INVALID_REQUEST_EMPTY = "{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 값을 확인해 주세요.\",\"errors\":[]}";

	private static final String UNKNOWN_STATE = """
			{"value":null,"source":"UNKNOWN","period":null,"updatedAt":null,"usable":false,"reason":"NOT_ENTERED"}""";

	/** 계약 GET 예시의 공통 부분(afterSnapshot 제외). */
	private static final String CONTRACT_BODY = """
			"userCardId": "uc-1001",
			"month": "2026-11",
			"snapshotAt": "2026-11-03T10:20:00+09:00",
			"previousMonthPerformanceWon": {
			  "value": 523000, "source": "MANUAL", "period": {"from": "2026-10-01", "to": "2026-10-31"},
			  "updatedAt": "2026-11-03T10:20:00+09:00", "usable": true, "reason": null, "referencedCodef": null,
			  "codefReference": null
			},
			"limitBucketUsages": [
			  {"limitBucketId": "LB-GREAT", "value": 4000, "source": "MANUAL",
			   "period": {"from": "2026-11-01", "to": "2026-11-30"},
			   "updatedAt": "2026-11-03T10:20:00+09:00", "usable": true, "reason": null}
			],
			"otherBucketsUsedWon": %s,
			"selectedServices": [
			  {"serviceId": "SVC-STORE", "effectiveFrom": "2026-10-15", "source": "MANUAL",
			   "updatedAt": "2026-11-03T10:20:00+09:00", "usable": true, "reason": null}
			],
			"serviceSelectionStatus": "SELECTED",
			"noServiceSelection": null,
			"registeredOn": %s,
			"conflicts": []
			""".formatted(UNKNOWN_STATE, UNKNOWN_STATE);

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	MonthlyStateService monthlyStateService;

	@MockitoBean
	DemoUserService demoUserService;

	@BeforeEach
	void resolveDemoUsers() {
		given(this.demoUserService.currentUserOf(DemoUser.MY_CARDS)).willReturn(MY_CARDS);
	}

	// GET

	@Test
	void getReturnsContractBodyWithAfterSnapshot() throws Exception {
		given(this.monthlyStateService.get(MY_CARDS, "uc-1001", NOVEMBER))
			.willReturn(contractView(new MonthlyStateView.AfterSnapshot(1, 12000, 0)));

		assertJson(perform(get(PATH).header(HEADER, "MY_CARDS")), """
				{"data":{%s,"afterSnapshot":{"approvalCount":1,"approvedAmountWon":12000,"cancellationCount":0}}}
				""".formatted(CONTRACT_BODY), 200);
	}

	@Test
	void monthNeverEnteredReturnsAllUnknownWithNullSnapshot() throws Exception {
		given(this.monthlyStateService.get(MY_CARDS, "uc-1001", NOVEMBER))
			.willReturn(new MonthlyStateView("uc-1001", NOVEMBER, null, StateValue.unknown(), List.of(),
					StateValue.unknown(), List.of(), ServiceSelectionStatus.UNKNOWN, null, StateValue.unknown(),
					null));

		assertJson(perform(get(PATH).header(HEADER, "MY_CARDS")), """
				{"data":{"userCardId":"uc-1001","month":"2026-11","snapshotAt":null,
				 "previousMonthPerformanceWon":{"value":null,"source":"UNKNOWN","period":null,"updatedAt":null,
				   "usable":false,"reason":"NOT_ENTERED","referencedCodef":null,"codefReference":null},
				 "limitBucketUsages":[],"otherBucketsUsedWon":%s,"selectedServices":[],
				 "serviceSelectionStatus":"UNKNOWN","noServiceSelection":null,"registeredOn":%s,
				 "conflicts":[],"afterSnapshot":null}}
				""".formatted(UNKNOWN_STATE, UNKNOWN_STATE), 200);
	}

	@Test
	void negativePerformanceZeroUsageAndNoSelectionAreWrittenAsGiven() throws Exception {
		LocalDate registered = LocalDate.of(2026, 10, 2);
		given(this.monthlyStateService.get(MY_CARDS, "uc-1001", NOVEMBER)).willReturn(new MonthlyStateView("uc-1001",
				NOVEMBER, SNAPSHOT, StateValue.manual(-12000L, OCTOBER, SNAPSHOT), List.of(),
				StateValue.manual(0L, NOVEMBER_PERIOD, SNAPSHOT), List.of(), ServiceSelectionStatus.NONE,
				new MonthlyStateView.NoSelection(null, SNAPSHOT),
				StateValue.manual(registered, new DatePeriod(registered, registered), SNAPSHOT),
				MonthlyStateView.AfterSnapshot.NO_SIMULATED_PAYMENTS));

		assertJson(perform(get(PATH).header(HEADER, "MY_CARDS")), """
				{"data":{"userCardId":"uc-1001","month":"2026-11","snapshotAt":"2026-11-03T10:20:00+09:00",
				 "previousMonthPerformanceWon":{"value":-12000,"source":"MANUAL",
				   "period":{"from":"2026-10-01","to":"2026-10-31"},"updatedAt":"2026-11-03T10:20:00+09:00",
				   "usable":true,"reason":null,"referencedCodef":null,"codefReference":null},
				 "limitBucketUsages":[],
				 "otherBucketsUsedWon":{"value":0,"source":"MANUAL","period":{"from":"2026-11-01","to":"2026-11-30"},
				   "updatedAt":"2026-11-03T10:20:00+09:00","usable":true,"reason":null},
				 "selectedServices":[],"serviceSelectionStatus":"NONE",
				 "noServiceSelection":{"effectiveFrom":null,"source":"MANUAL","updatedAt":"2026-11-03T10:20:00+09:00",
				   "usable":false,"reason":"NOT_ENTERED"},
				 "registeredOn":{"value":"2026-10-02","source":"MANUAL","period":{"from":"2026-10-02","to":"2026-10-02"},
				   "updatedAt":"2026-11-03T10:20:00+09:00","usable":true,"reason":null},
				 "conflicts":[],"afterSnapshot":{"approvalCount":0,"approvedAmountWon":0,"cancellationCount":0}}}
				""", 200);
	}

	@ParameterizedTest
	@ValueSource(strings = { "2026-1", "2026-13", "2026-00", "202611", "2026-11-01", "abcd-ef" })
	void malformedMonthIsBadRequestBeforeService(String month) throws Exception {
		assertJson(perform(get("/api/v1/me/cards/uc-1001/monthly-states/" + month).header(HEADER, "MY_CARDS")), """
				{"code":"INVALID_REQUEST","message":"요청 값을 확인해 주세요.",
				 "errors":[{"field":"month","message":"형식이 올바르지 않습니다."}]}
				""", 400);
		assertJson(perform(json(put("/api/v1/me/cards/uc-1001/monthly-states/" + month), "{}").header(HEADER,
				"MY_CARDS")), """
				{"code":"INVALID_REQUEST","message":"요청 값을 확인해 주세요.",
				 "errors":[{"field":"month","message":"형식이 올바르지 않습니다."}]}
				""", 400);
		verifyNoInteractions(this.monthlyStateService);
	}

	@Test
	void getMapsMissingCardToNotFound() throws Exception {
		given(this.monthlyStateService.get(MY_CARDS, "uc-1001", NOVEMBER))
			.willThrow(new MonthlyStateCardNotFoundException());

		assertJson(perform(get(PATH).header(HEADER, "MY_CARDS")),
				"{\"code\":\"USER_CARD_NOT_FOUND\",\"message\":\"보유 카드를 찾을 수 없습니다.\",\"errors\":[]}", 404);
	}

	@Test
	void missingDemoUserHeaderIsBadRequest() throws Exception {
		MvcResult result = perform(get(PATH));

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
			.contains("\"code\":\"INVALID_REQUEST\"");
		verifyNoInteractions(this.monthlyStateService);
	}

	// PUT

	@Test
	void putPassesInputAndReturnsContractBodyWithoutAfterSnapshot() throws Exception {
		given(this.monthlyStateService.replace(eq(MY_CARDS), eq("uc-1001"), eq(NOVEMBER), any()))
			.willReturn(contractView(null));

		MvcResult result = perform(json(put(PATH), """
				{
				  "previousMonthPerformanceWon": {"value": 523000, "period": {"from": "2026-10-01", "to": "2026-10-31"}},
				  "limitBucketUsages": [
				    {"limitBucketId": "LB-GREAT", "value": 4000, "period": {"from": "2026-11-01", "to": "2026-11-30"}}
				  ],
				  "selectedServices": [{"serviceId": "SVC-STORE", "effectiveFrom": "2026-10-15"}],
				  "registeredOn": {"value": "2026-10-02", "period": {"from": "2026-10-02", "to": "2026-10-02"}}
				}
				""").header(HEADER, "MY_CARDS"));

		assertJson(result, "{\"data\":{%s}}".formatted(CONTRACT_BODY), 200);
		ArgumentCaptor<MonthlyStateInput> captor = ArgumentCaptor.forClass(MonthlyStateInput.class);
		verify(this.monthlyStateService).replace(eq(MY_CARDS), eq("uc-1001"), eq(NOVEMBER), captor.capture());
		LocalDate registered = LocalDate.of(2026, 10, 2);
		assertThat(captor.getValue()).isEqualTo(new MonthlyStateInput(new MonthlyStateInput.Amount(523000, OCTOBER),
				List.of(new MonthlyStateInput.BucketUsage("LB-GREAT", 4000, NOVEMBER_PERIOD)), null,
				List.of(new MonthlyStateInput.Selection("SVC-STORE", LocalDate.of(2026, 10, 15))), null, registered));
	}

	@Test
	void putAcceptsEmptyBodyNullsNegativePerformanceAndMissingEffectiveFrom() throws Exception {
		given(this.monthlyStateService.replace(eq(MY_CARDS), eq("uc-1001"), eq(NOVEMBER), any()))
			.willReturn(contractView(null));

		for (String body : List.of("{}", """
				{"previousMonthPerformanceWon":{"value":-30000,"period":{"from":"2026-10-01","to":"2026-10-31"}},
				 "limitBucketUsages":null,"otherBucketsUsedWon":null,"selectedServices":[],
				 "noServiceSelection":{},"registeredOn":null}
				""", "{\"selectedServices\":[{\"serviceId\":\"S\"}]}")) {
			assertThat(perform(json(put(PATH), body).header(HEADER, "MY_CARDS")).getResponse().getStatus())
				.isEqualTo(200);
		}
		ArgumentCaptor<MonthlyStateInput> captor = ArgumentCaptor.forClass(MonthlyStateInput.class);
		verify(this.monthlyStateService, org.mockito.Mockito.times(3)).replace(eq(MY_CARDS), eq("uc-1001"),
				eq(NOVEMBER), captor.capture());
		assertThat(captor.getAllValues().get(0))
			.isEqualTo(new MonthlyStateInput(null, null, null, null, null, null));
		assertThat(captor.getAllValues().get(1)).isEqualTo(new MonthlyStateInput(
				new MonthlyStateInput.Amount(-30000, OCTOBER), null, null, null,
				new MonthlyStateInput.NoSelection(null), null));
		assertThat(captor.getAllValues().get(2).selectedServices())
			.containsExactly(new MonthlyStateInput.Selection("S", null));
	}

	@Test
	void getShowsCodefReferenceTheReferencedValueAndAConflict() throws Exception {
		// D-16(사용자 결정 2026-11-27, #281): 참고값은 계산 미사용으로 보여 주고, 충돌한 전월 실적은 값을 둔 채 usable=false다
		LocalDateTime seen = LocalDateTime.of(2026, 11, 4, 9, 1, 0);
		LocalDateTime newer = LocalDateTime.of(2026, 11, 20, 8, 0, 0);
		given(this.monthlyStateService.get(MY_CARDS, "uc-1001", NOVEMBER)).willReturn(new MonthlyStateView("uc-1001",
				NOVEMBER, SNAPSHOT, StateValue.manual(510000L, OCTOBER, SNAPSHOT).conflicted(), List.of(),
				StateValue.unknown(), List.of(), ServiceSelectionStatus.UNKNOWN, null, StateValue.unknown(),
				MonthlyStateView.AfterSnapshot.NO_SIMULATED_PAYMENTS,
				new MonthlyStateView.CodefReference(6, OCTOBER, 530000L, newer),
				new MonthlyStateView.ReferencedCodef(5L, 41, OCTOBER, 518000L, seen),
				List.of(new MonthlyStateView.Conflict("previousMonthPerformanceWon", OCTOBER, "cf-6-20261103102000",
						List.of(new MonthlyStateView.Candidate(StateSource.MANUAL, 510000L, OCTOBER, SNAPSHOT),
								new MonthlyStateView.Candidate(StateSource.CODEF, 530000L, OCTOBER, newer))))));

		assertJson(perform(get(PATH).header(HEADER, "MY_CARDS")), """
				{"data":{"userCardId":"uc-1001","month":"2026-11","snapshotAt":"2026-11-03T10:20:00+09:00",
				 "previousMonthPerformanceWon":{"value":510000,"source":"MANUAL",
				   "period":{"from":"2026-10-01","to":"2026-10-31"},"updatedAt":"2026-11-03T10:20:00+09:00",
				   "usable":false,"reason":"SOURCE_CONFLICT",
				   "referencedCodef":{"referenceId":"cpr-5","syncId":"sync-41","inquiryMonth":"2026-10",
				     "period":{"from":"2026-10-01","to":"2026-10-31"},"currentUseWon":518000,
				     "fetchedAt":"2026-11-04T09:01:00+09:00"},
				   "codefReference":{"referenceId":"cpr-6","label":"카드사 조회 실적(참고)","inquiryMonth":"2026-10",
				     "period":{"from":"2026-10-01","to":"2026-10-31"},"currentUseWon":530000,
				     "fetchedAt":"2026-11-20T08:00:00+09:00","usedInCalculation":false}},
				 "limitBucketUsages":[],"otherBucketsUsedWon":%s,"selectedServices":[],
				 "serviceSelectionStatus":"UNKNOWN","noServiceSelection":null,"registeredOn":%s,
				 "conflicts":[{"item":"previousMonthPerformanceWon","period":{"from":"2026-10-01","to":"2026-10-31"},
				   "conflictVersion":"cf-6-20261103102000",
				   "candidates":[
				     {"source":"MANUAL","value":510000,"period":{"from":"2026-10-01","to":"2026-10-31"},
				      "updatedAt":"2026-11-03T10:20:00+09:00"},
				     {"source":"CODEF","value":530000,"period":{"from":"2026-10-01","to":"2026-10-31"},
				      "updatedAt":"2026-11-20T08:00:00+09:00"}],
				   "selection":null}],
				 "afterSnapshot":{"approvalCount":0,"approvedAmountWon":0,"cancellationCount":0}}}
				""".formatted(UNKNOWN_STATE, UNKNOWN_STATE), 200);
	}

	@Test
	void unknownReferenceAmountAndDeletedReferenceStayNull() throws Exception {
		LocalDateTime seen = LocalDateTime.of(2026, 11, 4, 9, 1, 0);
		given(this.monthlyStateService.get(MY_CARDS, "uc-1001", NOVEMBER)).willReturn(new MonthlyStateView("uc-1001",
				NOVEMBER, SNAPSHOT, StateValue.manual(1L, OCTOBER, SNAPSHOT), List.of(), StateValue.unknown(),
				List.of(), ServiceSelectionStatus.UNKNOWN, null, StateValue.unknown(), null,
				new MonthlyStateView.CodefReference(6, OCTOBER, null, seen),
				new MonthlyStateView.ReferencedCodef(null, 41, OCTOBER, null, seen), List.of()));

		String body = perform(get(PATH).header(HEADER, "MY_CARDS")).getResponse()
			.getContentAsString(StandardCharsets.UTF_8);

		// 미확인 금액은 0이 아니라 null이고, 지워진 참고값의 ID는 null이다
		assertThat(body).contains("\"codefReference\":{\"referenceId\":\"cpr-6\"")
			.contains("\"referencedCodef\":{\"referenceId\":null,\"syncId\":\"sync-41\"")
			.contains("\"currentUseWon\":null")
			.doesNotContain("\"currentUseWon\":0");
	}

	@Test
	void putPassesTheReferencedCodefId() throws Exception {
		given(this.monthlyStateService.replace(eq(MY_CARDS), eq("uc-1001"), eq(NOVEMBER), any()))
			.willReturn(contractView(null));

		assertThat(perform(json(put(PATH), """
				{"previousMonthPerformanceWon":{"value":510000,"period":{"from":"2026-10-01","to":"2026-10-31"},
				 "referencedCodefId":"cpr-5"}}
				""").header(HEADER, "MY_CARDS")).getResponse().getStatus()).isEqualTo(200);

		ArgumentCaptor<MonthlyStateInput> captor = ArgumentCaptor.forClass(MonthlyStateInput.class);
		verify(this.monthlyStateService).replace(eq(MY_CARDS), eq("uc-1001"), eq(NOVEMBER), captor.capture());
		assertThat(captor.getValue().previousMonthPerformanceWon())
			.isEqualTo(new MonthlyStateInput.Amount(510000, OCTOBER, 5L));
	}

	@ParameterizedTest
	@ValueSource(strings = { "5", "cpr-0", "cpr-05", "uc-5", "", "cpr-5 " })
	void malformedReferencedCodefIdIsAFieldError(String id) throws Exception {
		assertFieldError("{\"previousMonthPerformanceWon\":{\"value\":1,\"period\":{\"from\":\"2026-10-01\",\"to\":\"2026-10-31\"},"
				+ "\"referencedCodefId\":\"" + id + "\"}}", "previousMonthPerformanceWon.referencedCodefId",
				"형식이 올바르지 않습니다.");
		verifyNoInteractions(this.monthlyStateService);
	}

	@Test
	void referencedCodefIdOnOtherAmountsIsAnUnknownField() throws Exception {
		assertJson(perform(json(put(PATH), "{\"otherBucketsUsedWon\":{\"value\":1,\"period\":{\"from\":\"2026-11-01\","
				+ "\"to\":\"2026-11-30\"},\"referencedCodefId\":\"cpr-5\"}}").header(HEADER, "MY_CARDS")),
				INVALID_REQUEST_EMPTY, 400);
		verifyNoInteractions(this.monthlyStateService);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			// 금액은 JSON 정수만
			"{\"previousMonthPerformanceWon\":{\"value\":1000.5,\"period\":{\"from\":\"2026-10-01\",\"to\":\"2026-10-31\"}}}",
			"{\"previousMonthPerformanceWon\":{\"value\":\"1000\",\"period\":{\"from\":\"2026-10-01\",\"to\":\"2026-10-31\"}}}",
			"{\"otherBucketsUsedWon\":{\"value\":true,\"period\":{\"from\":\"2026-11-01\",\"to\":\"2026-11-30\"}}}",
			"{\"otherBucketsUsedWon\":{\"value\":99999999999999999999,\"period\":{\"from\":\"2026-11-01\",\"to\":\"2026-11-30\"}}}",
			// 날짜 형식
			"{\"previousMonthPerformanceWon\":{\"value\":1,\"period\":{\"from\":\"2026-10-1\",\"to\":\"2026-10-31\"}}}",
			"{\"previousMonthPerformanceWon\":{\"value\":1,\"period\":{\"from\":\"2026-02-30\",\"to\":\"2026-10-31\"}}}",
			"{\"selectedServices\":[{\"serviceId\":\"S\",\"effectiveFrom\":20261015}]}",
			// 알 수 없는 필드(서버가 정하는 값 포함)
			"{\"source\":\"MANUAL\"}",
			"{\"previousMonthPerformanceWon\":{\"value\":1,\"source\":\"MANUAL\",\"period\":{\"from\":\"2026-10-01\",\"to\":\"2026-10-31\"}}}",
			"{\"serviceSelectionStatus\":\"NONE\"}",
			"{\"noServiceSelection\":{\"effectiveFrom\":\"2026-11-01\",\"usable\":true}}",
			// 본문 형식
			"[]", "not json" })
	void malformedBodyIsBadRequestWithEmptyErrors(String body) throws Exception {
		assertJson(perform(json(put(PATH), body).header(HEADER, "MY_CARDS")), INVALID_REQUEST_EMPTY, 400);
		verifyNoInteractions(this.monthlyStateService);
	}

	@Test
	void fieldRulesReportFieldPaths() throws Exception {
		String november = "\"period\":{\"from\":\"2026-11-01\",\"to\":\"2026-11-30\"}";
		assertFieldError("{\"previousMonthPerformanceWon\":{\"value\":1000}}", "previousMonthPerformanceWon.period",
				"필수 값입니다.");
		assertFieldError("{\"previousMonthPerformanceWon\":{" + november + "}}", "previousMonthPerformanceWon.value",
				"필수 값입니다.");
		assertFieldError(
				"{\"previousMonthPerformanceWon\":{\"value\":1,\"period\":{\"from\":\"2026-10-31\",\"to\":\"2026-10-01\"}}}",
				"previousMonthPerformanceWon.period", "시작일은 종료일보다 늦을 수 없습니다.");
		assertFieldError("{\"otherBucketsUsedWon\":{\"value\":-1," + november + "}}", "otherBucketsUsedWon.value",
				"0 이상이어야 합니다.");
		assertFieldError("{\"limitBucketUsages\":[{\"limitBucketId\":\"A\",\"value\":-1," + november + "}]}",
				"limitBucketUsages[0].value", "0 이상이어야 합니다.");
		assertFieldError("{\"limitBucketUsages\":[{\"value\":1," + november + "}]}",
				"limitBucketUsages[0].limitBucketId", "필수 값입니다.");
		assertFieldError("{\"limitBucketUsages\":[{\"limitBucketId\":\"A\",\"value\":1}]}",
				"limitBucketUsages[0].period", "필수 값입니다.");
		assertFieldError("{\"limitBucketUsages\":[null]}", "limitBucketUsages[0]", "필수 값입니다.");
		assertFieldError("{\"limitBucketUsages\":[{\"limitBucketId\":\"A\",\"value\":1," + november
				+ "},{\"limitBucketId\":\"A\",\"value\":2," + november + "}]}", "limitBucketUsages",
				"같은 한도 묶음을 두 번 보낼 수 없습니다.");
		assertFieldError("{\"selectedServices\":[{\"serviceId\":\"S\"},{\"serviceId\":\"S\"}]}", "selectedServices",
				"같은 서비스를 두 번 보낼 수 없습니다.");
		assertFieldError("{\"selectedServices\":[{\"effectiveFrom\":\"2026-10-15\"}]}",
				"selectedServices[0].serviceId", "필수 값입니다.");
		assertFieldError("{\"selectedServices\":[{\"serviceId\":\"S\"}],\"noServiceSelection\":{}}",
				"noServiceSelection", "selectedServices와 함께 보낼 수 없습니다.");
		assertFieldError(
				"{\"registeredOn\":{\"value\":\"2026-10-02\",\"period\":{\"from\":\"2026-10-01\",\"to\":\"2026-10-02\"}}}",
				"registeredOn.period", "기준 기간은 등록일 하루여야 합니다.");
		assertFieldError("{\"registeredOn\":{\"period\":{\"from\":\"2026-10-02\",\"to\":\"2026-10-02\"}}}",
				"registeredOn.value", "필수 값입니다.");
		verifyNoInteractions(this.monthlyStateService);
	}

	@Test
	void putMapsServiceFailures() throws Exception {
		given(this.monthlyStateService.replace(eq(MY_CARDS), anyString(), any(), any()))
			.willThrow(new MonthlyStateCardNotFoundException());
		assertJson(perform(json(put(PATH), "{}").header(HEADER, "MY_CARDS")),
				"{\"code\":\"USER_CARD_NOT_FOUND\",\"message\":\"보유 카드를 찾을 수 없습니다.\",\"errors\":[]}", 404);

		org.mockito.Mockito.reset(this.monthlyStateService);
		given(this.monthlyStateService.replace(eq(MY_CARDS), anyString(), any(), any()))
			.willThrow(new MappingRequiredException());
		assertJson(perform(json(put(PATH), "{}").header(HEADER, "MY_CARDS")),
				"{\"code\":\"USER_CARD_MAPPING_REQUIRED\",\"message\":\"카드 상품 매핑을 먼저 확인해 주세요.\",\"errors\":[]}",
				409);

		org.mockito.Mockito.reset(this.monthlyStateService);
		given(this.monthlyStateService.replace(eq(MY_CARDS), anyString(), any(), any()))
			.willThrow(new RuleMismatchException(
					List.of(new RuleMismatchException.Violation("limitBucketUsages[1].limitBucketId", "카드 규칙에 없는 값입니다."),
							new RuleMismatchException.Violation("noServiceSelection", "선택형 서비스가 없는 카드입니다."))));
		assertJson(perform(json(put(PATH), "{}").header(HEADER, "MY_CARDS")), """
				{"code":"INVALID_REQUEST","message":"요청 값을 확인해 주세요.",
				 "errors":[{"field":"limitBucketUsages[1].limitBucketId","message":"카드 규칙에 없는 값입니다."},
				           {"field":"noServiceSelection","message":"선택형 서비스가 없는 카드입니다."}]}
				""", 400);
	}

	private static MonthlyStateView contractView(MonthlyStateView.AfterSnapshot afterSnapshot) {
		return new MonthlyStateView("uc-1001", NOVEMBER, SNAPSHOT, StateValue.manual(523000L, OCTOBER, SNAPSHOT),
				List.of(new MonthlyStateView.BucketUsage("LB-GREAT", StateValue.manual(4000L, NOVEMBER_PERIOD, SNAPSHOT))),
				StateValue.unknown(),
				List.of(new MonthlyStateView.Selection("SVC-STORE", LocalDate.of(2026, 10, 15), SNAPSHOT)),
				ServiceSelectionStatus.SELECTED, null, StateValue.unknown(), afterSnapshot);
	}

	private void assertFieldError(String body, String field, String message) throws Exception {
		assertJson(perform(json(put(PATH), body).header(HEADER, "MY_CARDS")), """
				{"code":"INVALID_REQUEST","message":"요청 값을 확인해 주세요.",
				 "errors":[{"field":"%s","message":"%s"}]}
				""".formatted(field, message), 400);
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.contentType(MediaType.APPLICATION_JSON).content(body);
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
