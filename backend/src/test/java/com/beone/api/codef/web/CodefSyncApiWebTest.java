package com.beone.api.codef.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.beone.api.codef.application.CodefNotConnectedException;
import com.beone.api.codef.application.CodefPortfolioReadOnlyException;
import com.beone.api.codef.application.CodefReconnectRequiredException;
import com.beone.api.codef.application.CodefSyncPeriodException;
import com.beone.api.codef.application.CodefSyncResult;
import com.beone.api.codef.application.CodefSyncService;
import com.beone.api.codef.application.CodefSyncTarget;
import com.beone.api.codef.application.CodefUnavailableException;
import com.beone.api.codef.sync.CodefIncompleteReason;
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
 * CODEF 동기화 POST의 응답 형식·요청 검증·실패 응답을 MockMvc로 확인한다.
 *
 * <p>기대 JSON은 docs/api/codef.md 응답 예시(syncId, syncedAt, heldCards, approvals, performances, partialFailures)와 실패 목록(409
 * CODEF_NOT_CONNECTED·CODEF_RECONNECT_REQUIRED, 502 CODEF_UNAVAILABLE, 400 INVALID_REQUEST), 관리자 판단 K1(UNMAPPED,
 * candidateCardProductId=null)·Q3~Q5·Q7, docs/api.md 공통 실패 응답에서 옮겼다.
 */
@WebSliceTest(controllers = CodefSyncController.class)
@ActiveProfiles("local")
class CodefSyncApiWebTest {

	private static final String PATH = "/api/v1/me/codef/syncs";

	private static final String HEADER = "X-BeONE-Demo-User";

	private static final String INVALID_REQUEST_EMPTY = "{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 값을 확인해 주세요.\",\"errors\":[]}";

	private static final CurrentUser MY_CARDS = new CurrentUser(1L, DemoUser.MY_CARDS);

	private static final CurrentUser PORTFOLIO = new CurrentUser(2L, DemoUser.VIRTUAL_PORTFOLIO);

	private static final LocalDateTime SYNCED_AT = LocalDateTime.of(2026, 11, 4, 9, 1);

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	CodefSyncService codefSyncService;

	@MockitoBean
	DemoUserService demoUserService;

	@BeforeEach
	void resolveDemoUsers() {
		given(this.demoUserService.currentUserOf(DemoUser.MY_CARDS)).willReturn(MY_CARDS);
		given(this.demoUserService.currentUserOf(DemoUser.VIRTUAL_PORTFOLIO)).willReturn(PORTFOLIO);
	}

	@Test
	void successReturnsTheContractBodyWithUnmappedCandidates() throws Exception {
		given(this.codefSyncService.sync(MY_CARDS, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 3)))
			.willReturn(new CodefSyncResult(1, SYNCED_AT,
					new CodefSyncResult.HeldCards(3, List.of(new CodefSyncResult.NewCandidate(2001, "가짜 카드 A", null))),
					new CodefSyncResult.Approvals(41, 12,
							List.of(new CodefSyncResult.IncompleteRow(2001L, true,
									CodefIncompleteReason.PARTIAL_CANCELLATION_SUSPECTED))),
					List.of(new CodefSyncResult.PerformanceItem(2001L, LocalDate.of(2026, 10, 1),
							LocalDate.of(2026, 10, 31), 518000L, SYNCED_AT)),
					List.of()));

		assertJson(perform(json("{\"from\":\"2026-10-01\",\"to\":\"2026-11-03\"}").header(HEADER, "MY_CARDS")), """
				{"data":{"syncId":"sync-1","syncedAt":"2026-11-04T09:01:00+09:00",
				 "heldCards":{"fetched":3,"newCandidates":[{"userCardId":"uc-2001","cardName":"가짜 카드 A",
				  "candidateCardProductId":null,"mappingStatus":"UNMAPPED"}]},
				 "approvals":{"imported":41,"skippedDuplicates":12,"incompleteRows":[{"userCardId":"uc-2001",
				  "approvalNoPresent":true,"reason":"PARTIAL_CANCELLATION_SUSPECTED"}]},
				 "performances":[{"userCardId":"uc-2001","label":"카드사 조회 실적(참고)",
				  "period":{"from":"2026-10-01","to":"2026-10-31"},"currentUseWon":518000,
				  "fetchedAt":"2026-11-04T09:01:00+09:00","usedInCalculation":false}],
				 "partialFailures":[]}}
				""", 200);
	}

	@Test
	void dictionaryCandidatesAreReportedAsCandidateWithTheProduct() throws Exception {
		given(this.codefSyncService.sync(MY_CARDS, null, null)).willReturn(new CodefSyncResult(9, SYNCED_AT,
				new CodefSyncResult.HeldCards(2,
						List.of(new CodefSyncResult.NewCandidate(2001, "올라운드 티타늄카드", "09271"),
								new CodefSyncResult.NewCandidate(2002, "가짜 카드 B", null))),
				new CodefSyncResult.Approvals(0, 0, List.of()), List.of(), List.of()));

		assertJson(perform(json("{}").header(HEADER, "MY_CARDS")), """
				{"data":{"syncId":"sync-9","syncedAt":"2026-11-04T09:01:00+09:00",
				 "heldCards":{"fetched":2,"newCandidates":[
				  {"userCardId":"uc-2001","cardName":"올라운드 티타늄카드","candidateCardProductId":"09271",
				   "mappingStatus":"CANDIDATE"},
				  {"userCardId":"uc-2002","cardName":"가짜 카드 B","candidateCardProductId":null,
				   "mappingStatus":"UNMAPPED"}]},
				 "approvals":{"imported":0,"skippedDuplicates":0,"incompleteRows":[]},
				 "performances":[],"partialFailures":[]}}
				""", 200);
	}

	@Test
	void partialFailuresKeepNullCountsAndUnknownValues() throws Exception {
		given(this.codefSyncService.sync(MY_CARDS, null, null)).willReturn(new CodefSyncResult(7, SYNCED_AT,
				new CodefSyncResult.HeldCards(null, List.of()),
				new CodefSyncResult.Approvals(null, null, List.of()),
				List.of(new CodefSyncResult.PerformanceItem(null, null, null, null, SYNCED_AT)),
				List.of(CodefSyncTarget.HELD_CARDS, CodefSyncTarget.APPROVALS)));

		assertJson(perform(post(PATH).header(HEADER, "MY_CARDS")), """
				{"data":{"syncId":"sync-7","syncedAt":"2026-11-04T09:01:00+09:00",
				 "heldCards":{"fetched":null,"newCandidates":[]},
				 "approvals":{"imported":null,"skippedDuplicates":null,"incompleteRows":[]},
				 "performances":[{"userCardId":null,"label":"카드사 조회 실적(참고)","period":{"from":null,"to":null},
				  "currentUseWon":null,"fetchedAt":"2026-11-04T09:01:00+09:00","usedInCalculation":false}],
				 "partialFailures":[
				  {"target":"HELD_CARDS","code":"CODEF_UNAVAILABLE","message":"CODEF 응답을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요."},
				  {"target":"APPROVALS","code":"CODEF_UNAVAILABLE","message":"CODEF 응답을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요."}]}}
				""", 200);
	}

	@Test
	void unlinkedIncompleteRowsHaveANullCard() throws Exception {
		given(this.codefSyncService.sync(MY_CARDS, null, null)).willReturn(new CodefSyncResult(8, SYNCED_AT,
				new CodefSyncResult.HeldCards(1, List.of()),
				new CodefSyncResult.Approvals(0, 0,
						List.of(new CodefSyncResult.IncompleteRow(null, false,
								CodefIncompleteReason.UNDETERMINED_ROW))),
				List.of(), List.of()));

		MvcResult result = perform(json("{}").header(HEADER, "MY_CARDS"));

		assertJson(result, """
				{"data":{"syncId":"sync-8","syncedAt":"2026-11-04T09:01:00+09:00",
				 "heldCards":{"fetched":1,"newCandidates":[]},
				 "approvals":{"imported":0,"skippedDuplicates":0,"incompleteRows":[{"userCardId":null,
				  "approvalNoPresent":false,"reason":"UNDETERMINED_ROW"}]},
				 "performances":[],"partialFailures":[]}}
				""", 200);
	}

	@Test
	void omittedOrEmptyBodyPassesNoPeriod() throws Exception {
		given(this.codefSyncService.sync(any(), any(), any())).willReturn(new CodefSyncResult(9, SYNCED_AT,
				new CodefSyncResult.HeldCards(0, List.of()), new CodefSyncResult.Approvals(0, 0, List.of()), List.of(),
				List.of()));

		assertThat(perform(post(PATH).header(HEADER, "MY_CARDS")).getResponse().getStatus()).isEqualTo(200);
		assertThat(perform(json("{}").header(HEADER, "MY_CARDS")).getResponse().getStatus()).isEqualTo(200);
		assertThat(perform(json("{\"to\":\"2026-11-03\"}").header(HEADER, "MY_CARDS")).getResponse().getStatus())
			.isEqualTo(200);
		verify(this.codefSyncService, org.mockito.Mockito.times(2)).sync(MY_CARDS, null, null);
		verify(this.codefSyncService).sync(MY_CARDS, null, LocalDate.of(2026, 11, 3));
	}

	@ParameterizedTest
	@ValueSource(strings = { "{\"from\":\"2026-13-01\"}", "{\"from\":\"2026-02-30\"}", "{\"to\":\"2026/11/03\"}",
			"{\"to\":\"20261103\"}", "{\"from\":20261101}", "{\"from\":\"2026-11-1\"}", "[]", "{" })
	void malformedDatesAndBodiesAreRejectedWithoutDetails(String body) throws Exception {
		assertJson(perform(json(body).header(HEADER, "MY_CARDS")), INVALID_REQUEST_EMPTY, 400);
		verifyNoInteractions(this.codefSyncService);
	}

	@Test
	void periodRuleViolationsAre400WithFixedMessages() throws Exception {
		given(this.codefSyncService.sync(eq(MY_CARDS), any(), eq(LocalDate.of(2026, 12, 1))))
			.willThrow(new CodefSyncPeriodException("to", "오늘보다 늦을 수 없습니다."));
		given(this.codefSyncService.sync(eq(MY_CARDS), eq(LocalDate.of(2026, 11, 10)), eq(LocalDate.of(2026, 11, 9))))
			.willThrow(new CodefSyncPeriodException(null, "시작일이 종료일보다 늦습니다."));

		assertJson(perform(json("{\"to\":\"2026-12-01\"}").header(HEADER, "MY_CARDS")), """
				{"code":"INVALID_REQUEST","message":"요청 값을 확인해 주세요.",
				 "errors":[{"field":"to","message":"오늘보다 늦을 수 없습니다."}]}
				""", 400);
		assertJson(perform(json("{\"from\":\"2026-11-10\",\"to\":\"2026-11-09\"}").header(HEADER, "MY_CARDS")), """
				{"code":"INVALID_REQUEST","message":"요청 값을 확인해 주세요.",
				 "errors":[{"field":null,"message":"시작일이 종료일보다 늦습니다."}]}
				""", 400);
	}

	@Test
	void notConnectedIs409() throws Exception {
		assertFailure(new CodefNotConnectedException(),
				"{\"code\":\"CODEF_NOT_CONNECTED\",\"message\":\"CODEF가 연결되어 있지 않습니다.\",\"errors\":[]}", 409);
	}

	@Test
	void reconnectRequiredIs409() throws Exception {
		assertFailure(new CodefReconnectRequiredException(),
				"{\"code\":\"CODEF_RECONNECT_REQUIRED\",\"message\":\"CODEF 재연결이 필요합니다.\",\"errors\":[]}", 409);
	}

	@Test
	void unavailableIs502() throws Exception {
		assertFailure(new CodefUnavailableException(), """
				{"code":"CODEF_UNAVAILABLE","message":"CODEF 응답을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.","errors":[]}
				""", 502);
	}

	@Test
	void portfolioUserIs409ReadOnly() throws Exception {
		given(this.codefSyncService.sync(eq(PORTFOLIO), any(), any())).willThrow(new CodefPortfolioReadOnlyException());

		assertJson(perform(post(PATH).header(HEADER, "VIRTUAL_PORTFOLIO")), """
				{"code":"PORTFOLIO_READ_ONLY","message":"가상 포트폴리오에서는 CODEF 연결을 바꿀 수 없습니다.","errors":[]}
				""", 409);
	}

	@Test
	void unexpectedFailureIs500WithoutDetails() throws Exception {
		given(this.codefSyncService.sync(eq(MY_CARDS), any(), any()))
			.willThrow(new IllegalStateException("secret detail"));

		MvcResult result = perform(post(PATH).header(HEADER, "MY_CARDS"));

		assertJson(result, "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"요청 처리 중 오류가 발생했습니다.\",\"errors\":[]}",
				500);
		assertThat(body(result)).doesNotContain("secret");
	}

	@Test
	void rejectsMissingOrInvalidDemoUserHeader() throws Exception {
		assertJson(perform(post(PATH)), INVALID_REQUEST_EMPTY, 400);
		for (String invalid : List.of("my_cards", "UNKNOWN", "")) {
			assertJson(perform(post(PATH).header(HEADER, invalid)), INVALID_REQUEST_EMPTY, 400);
		}
		verifyNoInteractions(this.codefSyncService);
	}

	private void assertFailure(RuntimeException exception, String expected, int status) throws Exception {
		given(this.codefSyncService.sync(eq(MY_CARDS), any(), any())).willThrow(exception);

		assertJson(perform(post(PATH).header(HEADER, "MY_CARDS")), expected, status);
	}

	private static MockHttpServletRequestBuilder json(String body) {
		return post(PATH).contentType(MediaType.APPLICATION_JSON).content(body);
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
