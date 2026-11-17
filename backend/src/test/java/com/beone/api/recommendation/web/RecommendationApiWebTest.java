package com.beone.api.recommendation.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.beone.api.order.application.OrderNotFoundException;
import com.beone.api.recommendation.application.InvalidationReason;
import com.beone.api.recommendation.application.OrderAlreadyPaidException;
import com.beone.api.recommendation.application.RecommendationDocument;
import com.beone.api.recommendation.application.RecommendationNotFoundException;
import com.beone.api.recommendation.application.RecommendationNotes;
import com.beone.api.recommendation.application.RecommendationService;
import com.beone.api.recommendation.application.RecommendationView;
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
 * 추천 API 2개의 응답 형식·실패 응답을 MockMvc로 확인한다.
 *
 * <p>기대 JSON은 docs/api/recommendations.md의 계산 응답 예시(SELECTED·NOT_DETERMINED), 조회 응답의 무효화 필드, 실패 목록과
 * docs/api.md 공통 실패 응답에서 옮겼다. 계약 예시에 없는 {@code services[]}의 소수점 처리·미평가 제외 필드와 두 번째 제외 카드의 세 필드는
 * 결정 27 B2 승인 목록대로 항상 내보낸다.
 */
@WebSliceTest(controllers = RecommendationController.class)
@ActiveProfiles("local")
class RecommendationApiWebTest {

	private static final String HEADER = "X-BeONE-Demo-User";

	private static final String INVALID_REQUEST_EMPTY = "{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 값을 확인해 주세요.\",\"errors\":[]}";

	private static final CurrentUser MY_CARDS = new CurrentUser(1L, DemoUser.MY_CARDS);

	// 계약 SELECTED 예시의 본문(문자열 부분은 그대로 옮김)
	private static final String SELECTED_BODY = """
			"recommendationId":"rec-3001","orderId":"ord-5001","calculatedAt":"2026-11-03T10:41:00+09:00",
			"decisionDate":"2026-11-03","selectionMode":"AUTO_RECOMMEND","outcome":"SELECTED","conditional":true,
			"selected":{"userCardId":"uc-1001","cardProductId":"09174",
			 "basis":{"decidedBy":null,"arbitraryTieBreak":false,"tiedUserCardIds":[],"gapToRunnerUpWon":null}},
			"comparedCards":[{"userCardId":"uc-1001","cardProductId":"09174","displayName":"청춘대로 톡톡",
			 "mapping":{"status":"USER_CONFIRMED","note":"사용자 확인 매핑, 공식 SKU·약관 버전 미검증"},
			 "applicationStatus":"APPLIED_CAP_REMAINING",
			 "appliedRule":{"ruleVersion":"09174-v1","verificationStatus":"VERIFIED",
			  "officialSources":[{"uri":"https://img2.kbcard.com/obj/card/download/09174__prdctOpmn_20260714.pdf","locator":"p.3 §2"}]},
			 "currentOrderBenefit":{"amountWon":2100,"status":"CONFIRMED"},
			 "monthlyTotalBenefit":{"amountWon":2100,"status":"CONFIRMED"},
			 "services":[{"serviceId":"SVC-GREAT","applicationStatus":"APPLIED_CAP_REMAINING","candidateWon":5000,
			  "contribution":{"amountWon":2100,"status":"CONFIRMED"},"adoption":"ADOPTED",
			  "limits":[{"limitBucketId":"LB-GREAT","parentLimitBucketId":null,"limitWon":10000,
			   "usedBeforeWon":{"value":7900,"source":"MANUAL"},"remainingAfterWon":0}],
			  "limitReason":null,"usageBasis":"USER_ENTERED","mayDifferFromActual":true,
			  "usageNote":"입력한 사용액 기준, 미매입 전표에 따라 달라질 수 있음",
			  "fractionalWonTruncated":false,"fractionalWonBasis":null,"unevaluatedExclusions":[]}],
			 "noBenefitReason":null,"policyNotes":[],"pendingReasons":[]}],
			"excludedCards":[{"userCardId":"uc-1003","cardProductId":"01664","reason":"NEEDS_CONFIRMATION",
			  "detail":"전월 실적이 입력되지 않아 실적 조건을 판정할 수 없습니다.","candidateCardProductId":null,
			  "monthlyTotalBenefit":{"amountWon":null,"status":"NEEDS_CONFIRMATION"},
			  "applicationStatus":null,"appliedRule":null,"pendingReasons":[]},
			 {"userCardId":"uc-1002","cardProductId":null,"reason":"NO_SUPPORTED_RULE","detail":"검증된 규칙 없음(지원 범위 밖)",
			  "candidateCardProductId":null,"monthlyTotalBenefit":{"amountWon":null,"status":"NEEDS_CONFIRMATION"},
			  "applicationStatus":null,"appliedRule":null,"pendingReasons":[]}],
			"fixedCardComparison":null,
			"inputSnapshot":{"order":{"amountWon":10000,"orderDate":"2026-11-03","merchantClass":"STARBUCKS_STORE",
			  "paymentMethod":"DIRECT"},"representativeUserCardId":null,
			 "monthlyStateSnapshotAt":{"uc-1001":"2026-11-03T10:20:00+09:00"}},
			"notes":["예정 소비는 이번 버전에서 월간 합계에 반영하지 않습니다(D-18 K4).","비교에서 빠진 카드가 더 나을 수 있어 조건부 추천입니다."]
			""";

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	RecommendationService recommendationService;

	@MockitoBean
	DemoUserService demoUserService;

	@BeforeEach
	void resolveDemoUser() {
		given(this.demoUserService.currentUserOf(DemoUser.MY_CARDS)).willReturn(MY_CARDS);
	}

	@Test
	void postReturns201WithContractBody() throws Exception {
		given(this.recommendationService.calculate(MY_CARDS, "ord-5001")).willReturn(selectedDocument());

		assertJson(perform(post("/api/v1/me/orders/ord-5001/recommendations").header(HEADER, "MY_CARDS")),
				"{\"data\":{" + SELECTED_BODY + "}}", 201);
	}

	@Test
	void postNotDeterminedKeepsNullsAndEmptyLists() throws Exception {
		given(this.recommendationService.calculate(MY_CARDS, "ord-5001")).willReturn(new RecommendationDocument(
				"rec-3003", "ord-5001", "2026-11-03T10:41:00+09:00", LocalDate.of(2026, 11, 3), "AUTO_RECOMMEND",
				"NOT_DETERMINED", false, null, List.of(),
				List.of(new RecommendationDocument.ExcludedCard("uc-1001", "09174", "NEEDS_CONFIRMATION",
						"전월 실적이 입력되지 않아 실적 조건을 판정할 수 없습니다.", null,
						new RecommendationDocument.Amount(null, "NEEDS_CONFIRMATION"), null, null, List.of())),
				null, null,
				List.of(RecommendationNotes.PLANNED_SPENDING_EXCLUDED, RecommendationNotes.NO_COMPARABLE_CARD)));

		assertJson(perform(post("/api/v1/me/orders/ord-5001/recommendations").header(HEADER, "MY_CARDS")), """
				{"data":{"recommendationId":"rec-3003","orderId":"ord-5001","calculatedAt":"2026-11-03T10:41:00+09:00",
				 "decisionDate":"2026-11-03","selectionMode":"AUTO_RECOMMEND","outcome":"NOT_DETERMINED","conditional":false,
				 "selected":null,"comparedCards":[],
				 "excludedCards":[{"userCardId":"uc-1001","cardProductId":"09174","reason":"NEEDS_CONFIRMATION",
				  "detail":"전월 실적이 입력되지 않아 실적 조건을 판정할 수 없습니다.","candidateCardProductId":null,
				  "monthlyTotalBenefit":{"amountWon":null,"status":"NEEDS_CONFIRMATION"},
				  "applicationStatus":null,"appliedRule":null,"pendingReasons":[]}],
				 "fixedCardComparison":null,"inputSnapshot":null,
				 "notes":["예정 소비는 이번 버전에서 월간 합계에 반영하지 않습니다(D-18 K4).","비교할 수 있는 카드가 없어 추천을 확정할 수 없습니다."]}}
				""", 201);
	}

	@Test
	void postFailures() throws Exception {
		given(this.recommendationService.calculate(MY_CARDS, "ord-9")).willThrow(new OrderNotFoundException());
		given(this.recommendationService.calculate(MY_CARDS, "ord-5001")).willThrow(new OrderAlreadyPaidException());

		assertJson(perform(post("/api/v1/me/orders/ord-9/recommendations").header(HEADER, "MY_CARDS")),
				"{\"code\":\"ORDER_NOT_FOUND\",\"message\":\"주문을 찾을 수 없습니다.\",\"errors\":[]}", 404);
		assertJson(perform(post("/api/v1/me/orders/ord-5001/recommendations").header(HEADER, "MY_CARDS")),
				"{\"code\":\"ORDER_ALREADY_PAID\",\"message\":\"이미 모의 승인된 주문입니다.\",\"errors\":[]}", 409);
	}

	@Test
	void getAddsInvalidationFields() throws Exception {
		given(this.recommendationService.get(MY_CARDS, "rec-3001"))
			.willReturn(new RecommendationView(selectedDocument(), false, null));
		given(this.recommendationService.get(MY_CARDS, "rec-3002"))
			.willReturn(new RecommendationView(selectedDocument(), true, InvalidationReason.MONTHLY_STATE_CHANGED));

		assertJson(perform(get("/api/v1/me/recommendations/rec-3001").header(HEADER, "MY_CARDS")),
				"{\"data\":{" + SELECTED_BODY + ",\"invalidated\":false,\"invalidatedReason\":null}}", 200);
		assertJson(perform(get("/api/v1/me/recommendations/rec-3002").header(HEADER, "MY_CARDS")),
				"{\"data\":{" + SELECTED_BODY + ",\"invalidated\":true,\"invalidatedReason\":\"MONTHLY_STATE_CHANGED\"}}",
				200);
	}

	@Test
	void getNotFound() throws Exception {
		given(this.recommendationService.get(MY_CARDS, "rec-x")).willThrow(new RecommendationNotFoundException());

		assertJson(perform(get("/api/v1/me/recommendations/rec-x").header(HEADER, "MY_CARDS")),
				"{\"code\":\"RECOMMENDATION_NOT_FOUND\",\"message\":\"추천 결과를 찾을 수 없습니다.\",\"errors\":[]}", 404);
	}

	@Test
	void rejectsMissingOrInvalidDemoUserHeader() throws Exception {
		assertJson(perform(post("/api/v1/me/orders/ord-5001/recommendations")), INVALID_REQUEST_EMPTY, 400);
		assertJson(perform(get("/api/v1/me/recommendations/rec-3001")), INVALID_REQUEST_EMPTY, 400);
		assertJson(perform(get("/api/v1/me/recommendations/rec-3001").header(HEADER, "UNKNOWN")),
				INVALID_REQUEST_EMPTY, 400);
		verifyNoInteractions(this.recommendationService);
	}

	private static RecommendationDocument selectedDocument() {
		RecommendationDocument.Amount confirmed2100 = new RecommendationDocument.Amount(2100L, "CONFIRMED");
		RecommendationDocument.Amount needsConfirmation = new RecommendationDocument.Amount(null, "NEEDS_CONFIRMATION");
		return new RecommendationDocument("rec-3001", "ord-5001", "2026-11-03T10:41:00+09:00",
				LocalDate.of(2026, 11, 3), "AUTO_RECOMMEND", "SELECTED", true,
				new RecommendationDocument.Selected("uc-1001", "09174",
						new RecommendationDocument.Basis(null, false, List.of(), null)),
				List.of(new RecommendationDocument.ComparedCard("uc-1001", "09174", "청춘대로 톡톡",
						new RecommendationDocument.Mapping("USER_CONFIRMED", "사용자 확인 매핑, 공식 SKU·약관 버전 미검증"),
						"APPLIED_CAP_REMAINING",
						new RecommendationDocument.AppliedRule("09174-v1", "VERIFIED",
								List.of(new RecommendationDocument.OfficialSource(
										"https://img2.kbcard.com/obj/card/download/09174__prdctOpmn_20260714.pdf",
										"p.3 §2"))),
						confirmed2100, confirmed2100,
						List.of(new RecommendationDocument.Service("SVC-GREAT", "APPLIED_CAP_REMAINING", 5000L,
								confirmed2100, "ADOPTED",
								List.of(new RecommendationDocument.Limit("LB-GREAT", null, 10000L,
										new RecommendationDocument.UsedBefore(7900L, "MANUAL"), 0L)),
								null, "USER_ENTERED", true, "입력한 사용액 기준, 미매입 전표에 따라 달라질 수 있음", false, null,
								List.of())),
						null, List.of(), List.of())),
				List.of(new RecommendationDocument.ExcludedCard("uc-1003", "01664", "NEEDS_CONFIRMATION",
						"전월 실적이 입력되지 않아 실적 조건을 판정할 수 없습니다.", null, needsConfirmation, null, null, List.of()),
						new RecommendationDocument.ExcludedCard("uc-1002", null, "NO_SUPPORTED_RULE", "검증된 규칙 없음(지원 범위 밖)",
								null, needsConfirmation, null, null, List.of())),
				null,
				new RecommendationDocument.InputSnapshot(
						new RecommendationDocument.OrderSnapshot(10000, LocalDate.of(2026, 11, 3), "STARBUCKS_STORE",
								"DIRECT"),
						null, Map.of("uc-1001", "2026-11-03T10:20:00+09:00")),
				List.of(RecommendationNotes.PLANNED_SPENDING_EXCLUDED, RecommendationNotes.CONDITIONAL_RECOMMENDATION));
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
