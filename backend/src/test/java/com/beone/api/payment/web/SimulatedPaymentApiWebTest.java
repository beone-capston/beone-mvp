package com.beone.api.payment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.beone.api.order.application.OrderNotFoundException;
import com.beone.api.payment.application.ApprovalCommand;
import com.beone.api.payment.application.ApprovalDocument;
import com.beone.api.payment.application.CancellationAmountExceededException;
import com.beone.api.payment.application.CancellationDocument;
import com.beone.api.payment.application.IdempotencyKeyReusedException;
import com.beone.api.payment.application.OrderNotPayableException;
import com.beone.api.payment.application.PaymentAlreadyCancelledException;
import com.beone.api.payment.application.PaymentDetail;
import com.beone.api.payment.application.RecommendationNotDeterminedException;
import com.beone.api.payment.application.RepresentativeCardNotEligibleException;
import com.beone.api.payment.application.SimulatedPaymentNotFoundException;
import com.beone.api.payment.application.SimulatedPaymentService;
import com.beone.api.recommendation.application.OrderAlreadyPaidException;
import com.beone.api.recommendation.application.RecommendationNotFoundException;
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
 * 모의 결제 API 3개의 응답 형식·실패 응답을 MockMvc로 확인한다.
 *
 * <p>기대 JSON은 docs/api/mock-payments.md의 승인·상세·취소 응답 예시와 각 실패 목록, docs/api.md 공통 실패 응답(헤더 오류 400,
 * 필드 오류 형식)에서 옮겼다.
 */
@WebSliceTest(controllers = SimulatedPaymentController.class)
@ActiveProfiles("local")
class SimulatedPaymentApiWebTest {

	private static final String HEADER = "X-BeONE-Demo-User";

	private static final String KEY_HEADER = "Idempotency-Key";

	private static final String KEY = "6f1c2a9e-3b7d-4c10-9e55-2d4b8a7f0c31";

	private static final String APPROVE = "/api/v1/me/orders/ord-5001/simulated-payments";

	private static final String CANCEL = "/api/v1/me/simulated-payments/pay-7001/cancellations";

	private static final String INVALID_REQUEST_EMPTY = "{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 값을 확인해 주세요.\",\"errors\":[]}";

	private static final CurrentUser MY_CARDS = new CurrentUser(1L, DemoUser.MY_CARDS);

	// 계약 승인 응답 예시(취소 전 공통 부분). 상세 예시는 status·cancellableAmountWon·cancellations만 다르다.
	private static final String APPROVAL_FIELDS = """
			"simulatedPaymentId":"pay-7001","orderId":"ord-5001","status":"%s","approvedAt":"2026-11-03T10:42:00+09:00",
			"amountWon":10000,"selectionMode":"AUTO_RECOMMEND","userCardId":"uc-1001","cardProductId":"09174",
			"conditional":true,"currentOrderBenefit":{"amountWon":2100,"status":"CONFIRMED"},
			"appliedRule":{"ruleVersion":"09174-v1","verificationStatus":"VERIFIED",
			 "officialSources":[{"uri":"https://img2.kbcard.com/obj/card/download/09174__prdctOpmn_20260714.pdf","locator":"p.1"}]},
			"selectionBasis":{"decidedBy":"MONTHLY_TOTAL","arbitraryTieBreak":false,"tiedUserCardIds":[]},
			"mappingNote":"사용자 확인 매핑, 공식 SKU·약관 버전 미검증",
			"recalculation":{"recommendationId":"rec-3002","seenRecommendationId":"rec-3001","changed":false,"differences":[]},
			"ledgerTransactionId":"lt-9001","cancellableAmountWon":%d
			""";

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	SimulatedPaymentService simulatedPaymentService;

	@MockitoBean
	DemoUserService demoUserService;

	@BeforeEach
	void resolveDemoUser() {
		given(this.demoUserService.currentUserOf(DemoUser.MY_CARDS)).willReturn(MY_CARDS);
	}

	@Test
	void approveReturns201WithTheContractBody() throws Exception {
		given(this.simulatedPaymentService.approve(MY_CARDS, "ord-5001", new ApprovalCommand("rec-3001", false), KEY))
			.willReturn(approval("APPROVED", 10000));

		assertJson(perform(approve("{\"seenRecommendationId\":\"rec-3001\",\"simulateFailure\":false}")),
				"{\"data\":{" + APPROVAL_FIELDS.formatted("APPROVED", 10000) + "}}", 201);
	}

	@Test
	void approveWithoutBodyOrWithEmptyBodyCompareNothingAndDoNotFail() throws Exception {
		ApprovalCommand none = new ApprovalCommand(null, false);
		given(this.simulatedPaymentService.approve(MY_CARDS, "ord-5001", none, KEY)).willReturn(approval("APPROVED", 10000));

		assertThat(perform(post(APPROVE).header(HEADER, "MY_CARDS").header(KEY_HEADER, KEY)).getResponse().getStatus())
			.isEqualTo(201);
		assertThat(perform(approve("{}")).getResponse().getStatus()).isEqualTo(201);
	}

	@Test
	void simulatedFailureIs201WithFailedStatus() throws Exception {
		given(this.simulatedPaymentService.approve(MY_CARDS, "ord-5001", new ApprovalCommand(null, true), KEY))
			.willReturn(new ApprovalDocument("pay-7002", "ord-5001", "FAILED", "2026-11-03T10:42:00+09:00", 10000,
					"AUTO_RECOMMEND", "uc-1001", "09174", false, new ApprovalDocument.Amount(null, "NEEDS_CONFIRMATION"),
					null, null, null, new ApprovalDocument.Recalculation("rec-3002", null, null, List.of()), null, 0));

		assertJson(perform(approve("{\"simulateFailure\":true}")), """
				{"data":{"simulatedPaymentId":"pay-7002","orderId":"ord-5001","status":"FAILED",
				 "approvedAt":"2026-11-03T10:42:00+09:00","amountWon":10000,"selectionMode":"AUTO_RECOMMEND",
				 "userCardId":"uc-1001","cardProductId":"09174","conditional":false,
				 "currentOrderBenefit":{"amountWon":null,"status":"NEEDS_CONFIRMATION"},"appliedRule":null,
				 "selectionBasis":null,"mappingNote":null,
				 "recalculation":{"recommendationId":"rec-3002","seenRecommendationId":null,"changed":null,"differences":[]},
				 "ledgerTransactionId":null,"cancellableAmountWon":0}}
				""", 201);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   " })
	void approveWithBlankKeyIsInvalidRequest(String key) throws Exception {
		assertJson(perform(post(APPROVE).header(HEADER, "MY_CARDS").header(KEY_HEADER, key)), INVALID_REQUEST_EMPTY, 400);
	}

	@Test
	void missingOrTooLongKeyIsInvalidRequestForBothWrites() throws Exception {
		assertJson(perform(post(APPROVE).header(HEADER, "MY_CARDS")), INVALID_REQUEST_EMPTY, 400);
		assertJson(perform(postJson(CANCEL, "{\"amountWon\":4000}").header(HEADER, "MY_CARDS")), INVALID_REQUEST_EMPTY,
				400);
		assertJson(perform(post(APPROVE).header(HEADER, "MY_CARDS").header(KEY_HEADER, "k".repeat(256))),
				INVALID_REQUEST_EMPTY, 400);
		verify(this.simulatedPaymentService, never()).approve(any(), anyString(), any(), anyString());
		verify(this.simulatedPaymentService, never()).cancel(any(), anyString(), anyLong(), anyString());
	}

	@Test
	void approveRejectsNonBooleanFlagAndNonStringRecommendation() throws Exception {
		assertJson(perform(approve("{\"simulateFailure\":\"true\"}")), INVALID_REQUEST_EMPTY, 400);
		assertJson(perform(approve("{\"seenRecommendationId\":3001}")), INVALID_REQUEST_EMPTY, 400);
	}

	@Test
	void approveFailures() throws Exception {
		failsWith(new OrderNotFoundException(), 404, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다.");
		failsWith(new RecommendationNotFoundException(), 404, "RECOMMENDATION_NOT_FOUND", "추천 결과를 찾을 수 없습니다.");
		failsWith(new OrderNotPayableException(), 409, "ORDER_ALREADY_PAID", "이미 모의 승인된 주문입니다.");
		failsWith(new OrderAlreadyPaidException(), 409, "ORDER_ALREADY_PAID", "이미 모의 승인된 주문입니다.");
		failsWith(new IdempotencyKeyReusedException(), 409, "IDEMPOTENCY_KEY_REUSED",
				"이 Idempotency-Key는 다른 요청에 이미 사용했습니다.");
		failsWith(new RecommendationNotDeterminedException(), 409, "RECOMMENDATION_NOT_DETERMINED",
				"추천을 확정할 수 없어 자동 추천으로 승인할 수 없습니다.");
		failsWith(new RepresentativeCardNotEligibleException(), 409, "REPRESENTATIVE_CARD_NOT_ELIGIBLE",
				"대표 카드를 승인에 쓸 수 없습니다.");
	}

	@Test
	void getReturnsTheContractDetail() throws Exception {
		ApprovalDocument approval = approval("APPROVED", 10000);
		given(this.simulatedPaymentService.get(MY_CARDS, "pay-7001")).willReturn(new PaymentDetail(
				approval.simulatedPaymentId(), approval.orderId(), "PARTIALLY_CANCELLED", approval.approvedAt(),
				approval.amountWon(), approval.selectionMode(), approval.userCardId(), approval.cardProductId(),
				approval.conditional(), approval.currentOrderBenefit(), approval.appliedRule(), approval.selectionBasis(),
				approval.mappingNote(), approval.recalculation(), approval.ledgerTransactionId(), 6000,
				List.of(new PaymentDetail.CancellationLine("cn-8001", 4000, "2026-11-03T11:00:00+09:00", "lt-9002"))));

		assertJson(perform(get("/api/v1/me/simulated-payments/pay-7001").header(HEADER, "MY_CARDS")),
				"{\"data\":{" + APPROVAL_FIELDS.formatted("PARTIALLY_CANCELLED", 6000)
						+ ",\"cancellations\":[{\"cancellationId\":\"cn-8001\",\"amountWon\":4000,"
						+ "\"cancelledAt\":\"2026-11-03T11:00:00+09:00\",\"ledgerTransactionId\":\"lt-9002\"}]}}",
				200);
	}

	@Test
	void getUnknownPaymentIs404() throws Exception {
		given(this.simulatedPaymentService.get(MY_CARDS, "pay-9")).willThrow(new SimulatedPaymentNotFoundException());

		assertJson(perform(get("/api/v1/me/simulated-payments/pay-9").header(HEADER, "MY_CARDS")),
				"{\"code\":\"SIMULATED_PAYMENT_NOT_FOUND\",\"message\":\"모의 결제를 찾을 수 없습니다.\",\"errors\":[]}", 404);
	}

	@Test
	void cancelReturns201WithTheContractBody() throws Exception {
		given(this.simulatedPaymentService.cancel(MY_CARDS, "pay-7001", 4000L, KEY)).willReturn(new CancellationDocument(
				"cn-8001", "pay-7001", 4000, "2026-11-03T11:00:00+09:00", "lt-9002", "PARTIALLY_CANCELLED", 6000,
				new CancellationDocument.Impact(List.of(), List.of(
						new CancellationDocument.ImpactItem("PERFORMANCE", "uc-1001", "2026-11", null, null, null, null,
								"취소 귀속월이 약관에서 확정되지 않았습니다."),
						new CancellationDocument.ImpactItem("BENEFIT", "uc-1001", null, "SVC-GREAT", null, null, null,
								"혜택 되돌림 규칙이 약관에서 확정되지 않았습니다."),
						new CancellationDocument.ImpactItem("LIMIT_USAGE", "uc-1001", null, null, "LB-GREAT", null, null,
								"한도 복원 시점 근거가 확정되지 않았습니다.")))));

		assertJson(perform(cancel("{\"amountWon\":4000}")), """
				{"data":{"cancellationId":"cn-8001","simulatedPaymentId":"pay-7001","amountWon":4000,
				 "cancelledAt":"2026-11-03T11:00:00+09:00","ledgerTransactionId":"lt-9002",
				 "paymentStatus":"PARTIALLY_CANCELLED","cancellableAmountWon":6000,
				 "impact":{"confirmed":[],"needsConfirmation":[
				  {"target":"PERFORMANCE","userCardId":"uc-1001","month":"2026-11","reason":"취소 귀속월이 약관에서 확정되지 않았습니다."},
				  {"target":"BENEFIT","userCardId":"uc-1001","serviceId":"SVC-GREAT","reason":"혜택 되돌림 규칙이 약관에서 확정되지 않았습니다."},
				  {"target":"LIMIT_USAGE","userCardId":"uc-1001","limitBucketId":"LB-GREAT","reason":"한도 복원 시점 근거가 확정되지 않았습니다."}]}}}
				""", 201);
	}

	@Test
	void confirmedImpactUsesTheContractShape() throws Exception {
		given(this.simulatedPaymentService.cancel(MY_CARDS, "pay-7001", 4000L, KEY)).willReturn(new CancellationDocument(
				"cn-8001", "pay-7001", 4000, "2026-11-03T11:00:00+09:00", "lt-9002", "PARTIALLY_CANCELLED", 6000,
				new CancellationDocument.Impact(List.of(new CancellationDocument.ImpactItem("PERFORMANCE", "uc-1002",
						"2026-11", null, null, -4000L, "RECEIPT_MONTH", null)), List.of())));

		MvcResult result = perform(cancel("{\"amountWon\":4000}"));

		JsonAssert.comparator(JsonCompareMode.LENIENT)
			.assertIsMatch("{\"data\":{\"impact\":{\"confirmed\":[{\"target\":\"PERFORMANCE\",\"userCardId\":\"uc-1002\","
					+ "\"month\":\"2026-11\",\"deltaWon\":-4000,\"basis\":\"RECEIPT_MONTH\"}],\"needsConfirmation\":[]}}}",
					result.getResponse().getContentAsString(StandardCharsets.UTF_8));
		assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain("\"reason\"");
	}

	@Test
	void cancelMissingOrNonPositiveAmountIsAFieldError() throws Exception {
		assertJson(perform(cancel("{}")),
				"{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 값을 확인해 주세요.\",\"errors\":[{\"field\":\"amountWon\",\"message\":\"필수 값입니다.\"}]}",
				400);
		for (String amount : List.of("0", "-1")) {
			assertJson(perform(cancel("{\"amountWon\":" + amount + "}")),
					"{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 값을 확인해 주세요.\",\"errors\":[{\"field\":\"amountWon\",\"message\":\"1 이상이어야 합니다.\"}]}",
					400);
		}
		for (String amount : List.of("\"4000\"", "4000.5")) {
			assertJson(perform(cancel("{\"amountWon\":" + amount + "}")), INVALID_REQUEST_EMPTY, 400);
		}
		verify(this.simulatedPaymentService, never()).cancel(any(), anyString(), anyLong(), anyString());
	}

	@Test
	void cancelFailures() throws Exception {
		cancelFailsWith(new SimulatedPaymentNotFoundException(), 404, "SIMULATED_PAYMENT_NOT_FOUND", "모의 결제를 찾을 수 없습니다.");
		cancelFailsWith(new CancellationAmountExceededException(), 409, "CANCELLATION_AMOUNT_EXCEEDED",
				"취소 금액이 남은 취소 가능 금액보다 큽니다.");
		cancelFailsWith(new PaymentAlreadyCancelledException(), 409, "PAYMENT_ALREADY_CANCELLED", "이미 전액 취소된 결제입니다.");
		cancelFailsWith(new IdempotencyKeyReusedException(), 409, "IDEMPOTENCY_KEY_REUSED",
				"이 Idempotency-Key는 다른 요청에 이미 사용했습니다.");
	}

	@Test
	void demoUserHeaderErrorsAre400ForAllThree() throws Exception {
		List<MockHttpServletRequestBuilder> requests = List.of(post(APPROVE).header(KEY_HEADER, KEY),
				get("/api/v1/me/simulated-payments/pay-7001"),
				postJson(CANCEL, "{\"amountWon\":4000}").header(KEY_HEADER, KEY));
		for (MockHttpServletRequestBuilder request : requests) {
			assertJson(perform(request), INVALID_REQUEST_EMPTY, 400);
		}
		assertJson(perform(get("/api/v1/me/simulated-payments/pay-7001").header(HEADER, "UNKNOWN")),
				INVALID_REQUEST_EMPTY, 400);
	}

	private void failsWith(RuntimeException exception, int status, String code, String message) throws Exception {
		willThrow(exception).given(this.simulatedPaymentService).approve(eq(MY_CARDS), eq("ord-5001"), any(), eq(KEY));
		assertJson(perform(approve("{}")),
				"{\"code\":\"%s\",\"message\":\"%s\",\"errors\":[]}".formatted(code, message), status);
	}

	private void cancelFailsWith(RuntimeException exception, int status, String code, String message) throws Exception {
		willThrow(exception).given(this.simulatedPaymentService).cancel(MY_CARDS, "pay-7001", 4000L, KEY);
		assertJson(perform(cancel("{\"amountWon\":4000}")),
				"{\"code\":\"%s\",\"message\":\"%s\",\"errors\":[]}".formatted(code, message), status);
	}

	private static ApprovalDocument approval(String status, long cancellable) {
		return new ApprovalDocument("pay-7001", "ord-5001", status, "2026-11-03T10:42:00+09:00", 10000,
				"AUTO_RECOMMEND", "uc-1001", "09174", true, new ApprovalDocument.Amount(2100L, "CONFIRMED"),
				new ApprovalDocument.AppliedRule("09174-v1", "VERIFIED",
						List.of(new ApprovalDocument.OfficialSource(
								"https://img2.kbcard.com/obj/card/download/09174__prdctOpmn_20260714.pdf", "p.1"))),
				new ApprovalDocument.SelectionBasis("MONTHLY_TOTAL", false, List.of()), "사용자 확인 매핑, 공식 SKU·약관 버전 미검증",
				new ApprovalDocument.Recalculation("rec-3002", "rec-3001", false, List.of()), "lt-9001", cancellable);
	}

	private static MockHttpServletRequestBuilder approve(String body) {
		return postJson(APPROVE, body).header(HEADER, "MY_CARDS").header(KEY_HEADER, KEY);
	}

	private static MockHttpServletRequestBuilder cancel(String body) {
		return postJson(CANCEL, body).header(HEADER, "MY_CARDS").header(KEY_HEADER, KEY);
	}

	private static MockHttpServletRequestBuilder postJson(String path, String body) {
		return post(path).contentType(MediaType.APPLICATION_JSON).content(body);
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
