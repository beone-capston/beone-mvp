package com.beone.api.order.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

import com.beone.api.order.application.MerchantClassCatalog;
import com.beone.api.order.application.NewOrder;
import com.beone.api.order.application.NewPlannedSpend;
import com.beone.api.order.application.OrderClock;
import com.beone.api.order.application.OrderDetail;
import com.beone.api.order.application.OrderNotFoundException;
import com.beone.api.order.application.OrderService;
import com.beone.api.order.application.OrderStatus;
import com.beone.api.order.application.PaymentMethod;
import com.beone.api.order.application.PlannedDateOutOfMonthException;
import com.beone.api.order.application.PlannedSpendDetail;
import com.beone.api.order.application.PlannedSpendService;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 주문·예정 소비 API 4개의 요청 검증·응답 형식·실패 응답을 MockMvc로 확인한다.
 *
 * <p>기대 JSON은 docs/api/orders.md 응답 예시, 실패 응답은 같은 문서의 실패 목록과 docs/api.md 공통 실패 응답에서 옮겼다.
 * "이번 달"은 고정 시계 2026-11-17(Asia/Seoul)이다.
 */
@WebSliceTest(controllers = { OrderController.class, PlannedSpendController.class })
@ActiveProfiles("local")
@Import({ MerchantClassCatalog.class, OrderApiWebTest.FixedClockConfiguration.class })
class OrderApiWebTest {

	private static final String ORDERS = "/api/v1/me/orders";

	private static final String PLANNED = "/api/v1/me/planned-spends";

	private static final String HEADER = "X-BeONE-Demo-User";

	private static final String INVALID_REQUEST_EMPTY = "{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 값을 확인해 주세요.\",\"errors\":[]}";

	private static final CurrentUser MY_CARDS = new CurrentUser(1L, DemoUser.MY_CARDS);

	private static final CurrentUser PORTFOLIO = new CurrentUser(2L, DemoUser.VIRTUAL_PORTFOLIO);

	private static final String CONTRACT_ORDER_REQUEST = """
			{
			  "amountWon": 10000,
			  "orderDate": "2026-11-03",
			  "orderTime": null,
			  "merchantName": "스타벅스 송도점",
			  "merchantClass": "STARBUCKS_STORE",
			  "paymentMethod": "DIRECT"
			}
			""";

	private static final OrderDetail CONTRACT_ORDER = new OrderDetail("ord-5001", 10000, LocalDate.of(2026, 11, 3),
			null, "스타벅스 송도점", "STARBUCKS_STORE", PaymentMethod.DIRECT, OrderStatus.CREATED,
			LocalDateTime.of(2026, 11, 3, 10, 40, 0));

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	OrderService orderService;

	@MockitoBean
	PlannedSpendService plannedSpendService;

	@MockitoBean
	DemoUserService demoUserService;

	@BeforeEach
	void resolveDemoUsers() {
		given(this.demoUserService.currentUserOf(DemoUser.MY_CARDS)).willReturn(MY_CARDS);
		given(this.demoUserService.currentUserOf(DemoUser.VIRTUAL_PORTFOLIO)).willReturn(PORTFOLIO);
	}

	// POST /api/v1/me/orders

	@Test
	void createOrderReturns201WithContractBody() throws Exception {
		given(this.orderService.create(any(), any())).willReturn(CONTRACT_ORDER);

		MvcResult result = perform(postJson(ORDERS, CONTRACT_ORDER_REQUEST).header(HEADER, "MY_CARDS"));

		assertThat(result.getResponse().getStatus()).isEqualTo(201);
		assertJson(result, """
				{
				  "data": {
				    "orderId": "ord-5001",
				    "amountWon": 10000,
				    "orderDate": "2026-11-03",
				    "orderTime": null,
				    "merchantName": "스타벅스 송도점",
				    "merchantClass": "STARBUCKS_STORE",
				    "paymentMethod": "DIRECT",
				    "status": "CREATED",
				    "createdAt": "2026-11-03T10:40:00+09:00"
				  }
				}
				""");
		ArgumentCaptor<NewOrder> captor = ArgumentCaptor.forClass(NewOrder.class);
		verify(this.orderService).create(org.mockito.ArgumentMatchers.eq(MY_CARDS), captor.capture());
		assertThat(captor.getValue()).isEqualTo(new NewOrder(10000, LocalDate.of(2026, 11, 3), null, "스타벅스 송도점",
				"STARBUCKS_STORE", PaymentMethod.DIRECT));
	}

	@Test
	void createOrderKeepsOmittedOptionalFieldsNull() throws Exception {
		given(this.orderService.create(any(), any())).willReturn(CONTRACT_ORDER);

		MvcResult result = perform(postJson(ORDERS,
				"{\"amountWon\":1,\"orderDate\":\"2026-11-03\",\"merchantClass\":\"TAX\",\"paymentMethod\":\"AUTOPAY\"}")
			.header(HEADER, "MY_CARDS"));

		assertThat(result.getResponse().getStatus()).isEqualTo(201);
		ArgumentCaptor<NewOrder> captor = ArgumentCaptor.forClass(NewOrder.class);
		verify(this.orderService).create(any(), captor.capture());
		assertThat(captor.getValue()).isEqualTo(
				new NewOrder(1, LocalDate.of(2026, 11, 3), null, null, "TAX", PaymentMethod.AUTOPAY));
	}

	@Test
	void createOrderPassesTimeAndMaxLengthName() throws Exception {
		given(this.orderService.create(any(), any())).willReturn(CONTRACT_ORDER);
		String name = "가".repeat(50);

		MvcResult result = perform(postJson(ORDERS, "{\"amountWon\":10000,\"orderDate\":\"2026-11-03\","
				+ "\"orderTime\":\"10:40:00\",\"merchantName\":\"" + name + "\","
				+ "\"merchantClass\":\"STARBUCKS_STORE\",\"paymentMethod\":\"ELIGIBLE_SIMPLE_PAY\"}")
			.header(HEADER, "MY_CARDS"));

		assertThat(result.getResponse().getStatus()).isEqualTo(201);
		ArgumentCaptor<NewOrder> captor = ArgumentCaptor.forClass(NewOrder.class);
		verify(this.orderService).create(any(), captor.capture());
		assertThat(captor.getValue().orderTime()).isEqualTo(LocalTime.of(10, 40, 0));
		assertThat(captor.getValue().merchantName()).isEqualTo(name);
	}

	@Test
	void virtualPortfolioCanCreateOrder() throws Exception {
		given(this.orderService.create(any(), any())).willReturn(CONTRACT_ORDER);

		MvcResult result = perform(postJson(ORDERS, CONTRACT_ORDER_REQUEST).header(HEADER, "VIRTUAL_PORTFOLIO"));

		assertThat(result.getResponse().getStatus()).isEqualTo(201);
		verify(this.orderService).create(org.mockito.ArgumentMatchers.eq(PORTFOLIO), any());
	}

	@ParameterizedTest
	@ValueSource(strings = { "amountWon", "orderDate", "merchantClass", "paymentMethod" })
	void createOrderMissingRequiredFieldIsInvalidRequest(String field) throws Exception {
		String body = CONTRACT_ORDER_REQUEST.replaceFirst("\"" + field + "\": [^,\\n]+,?", "")
			.replaceAll(",(\\s*})", "$1");

		MvcResult result = perform(postJson(ORDERS, body).header(HEADER, "MY_CARDS"));

		assertFieldError(result, field, "필수 값입니다.");
		verify(this.orderService, never()).create(any(), any());
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "-1" })
	void createOrderNonPositiveAmountIsInvalidRequest(String amount) throws Exception {
		MvcResult result = perform(
				postJson(ORDERS, CONTRACT_ORDER_REQUEST.replace("10000", amount)).header(HEADER, "MY_CARDS"));

		assertFieldError(result, "amountWon", "1 이상이어야 합니다.");
	}

	@ParameterizedTest
	@ValueSource(strings = { "10000.5", "10000.0", "\"10000\"" })
	void createOrderDecimalOrStringAmountIsInvalidRequest(String amount) throws Exception {
		MvcResult result = perform(
				postJson(ORDERS, CONTRACT_ORDER_REQUEST.replace("10000", amount)).header(HEADER, "MY_CARDS"));

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertJson(result, INVALID_REQUEST_EMPTY);
		verify(this.orderService, never()).create(any(), any());
	}

	@ParameterizedTest
	@ValueSource(strings = { "2026/11/03", "2026-11-3", "2026-02-30", "2026-11-03T10:40:00" })
	void createOrderDateFormatErrorIsInvalidRequest(String date) throws Exception {
		MvcResult result = perform(
				postJson(ORDERS, CONTRACT_ORDER_REQUEST.replace("2026-11-03", date)).header(HEADER, "MY_CARDS"));

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertJson(result, INVALID_REQUEST_EMPTY);
	}

	@ParameterizedTest
	@ValueSource(strings = { "\"10:40\"", "\"25:00:00\"" })
	void createOrderTimeFormatErrorIsInvalidRequest(String time) throws Exception {
		MvcResult result = perform(postJson(ORDERS, CONTRACT_ORDER_REQUEST.replace("\"orderTime\": null",
				"\"orderTime\": " + time)).header(HEADER, "MY_CARDS"));

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertJson(result, INVALID_REQUEST_EMPTY);
	}

	@ParameterizedTest
	@ValueSource(strings = { "\"CARD\"", "\"direct\"", "\"\"", "0", "2" })
	void createOrderUnknownPaymentMethodIsInvalidRequest(String method) throws Exception {
		MvcResult result = perform(
				postJson(ORDERS, CONTRACT_ORDER_REQUEST.replace("\"DIRECT\"", method))
					.header(HEADER, "MY_CARDS"));

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertJson(result, INVALID_REQUEST_EMPTY);
	}

	@ParameterizedTest
	@ValueSource(strings = { "STARBUCKS", "starbucks_store", "" })
	void createOrderMerchantClassOutsideAllowlistIsInvalidRequest(String merchantClass) throws Exception {
		MvcResult result = perform(postJson(ORDERS,
				CONTRACT_ORDER_REQUEST.replace("\"STARBUCKS_STORE\"", "\"" + merchantClass + "\""))
			.header(HEADER, "MY_CARDS"));

		assertFieldError(result, "merchantClass", "허용된 가맹점 분류가 아닙니다.");
	}

	@Test
	void createOrderMerchantNameOver50IsInvalidRequest() throws Exception {
		MvcResult result = perform(postJson(ORDERS,
				CONTRACT_ORDER_REQUEST.replace("스타벅스 송도점", "가".repeat(51))).header(HEADER, "MY_CARDS"));

		assertFieldError(result, "merchantName", "50자 이하여야 합니다.");
	}

	@Test
	void createOrderMalformedJsonIsInvalidRequest() throws Exception {
		MvcResult result = perform(postJson(ORDERS, "{\"amountWon\":").header(HEADER, "MY_CARDS"));

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertJson(result, INVALID_REQUEST_EMPTY);
	}

	// GET /api/v1/me/orders/{orderId}

	@Test
	void getOrderReturnsContractFields() throws Exception {
		given(this.orderService.get(MY_CARDS, "ord-5001")).willReturn(CONTRACT_ORDER);

		MvcResult result = perform(get(ORDERS + "/ord-5001").header(HEADER, "MY_CARDS"));

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertJson(result, """
				{
				  "data": {
				    "orderId": "ord-5001",
				    "amountWon": 10000,
				    "orderDate": "2026-11-03",
				    "orderTime": null,
				    "merchantName": "스타벅스 송도점",
				    "merchantClass": "STARBUCKS_STORE",
				    "paymentMethod": "DIRECT",
				    "status": "CREATED",
				    "createdAt": "2026-11-03T10:40:00+09:00",
				    "simulatedPaymentId": null,
				    "latestRecommendationId": null
				  }
				}
				""");
	}

	@Test
	void getPaidOrderShowsTheSimulatedPaymentId() throws Exception {
		// 계약 예시 "simulatedPaymentId": "pay-7001", status PAID(모의 승인됨). latestRecommendationId는 B2f 전이라 null
		given(this.orderService.get(MY_CARDS, "ord-5001")).willReturn(new OrderDetail("ord-5001", 10000,
				LocalDate.of(2026, 11, 3), null, "스타벅스 송도점", "STARBUCKS_STORE", PaymentMethod.DIRECT, OrderStatus.PAID,
				LocalDateTime.of(2026, 11, 3, 10, 40, 0), "pay-7001"));

		MvcResult result = perform(get(ORDERS + "/ord-5001").header(HEADER, "MY_CARDS"));

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertJson(result, """
				{
				  "data": {
				    "orderId": "ord-5001",
				    "amountWon": 10000,
				    "orderDate": "2026-11-03",
				    "orderTime": null,
				    "merchantName": "스타벅스 송도점",
				    "merchantClass": "STARBUCKS_STORE",
				    "paymentMethod": "DIRECT",
				    "status": "PAID",
				    "createdAt": "2026-11-03T10:40:00+09:00",
				    "simulatedPaymentId": "pay-7001",
				    "latestRecommendationId": null
				  }
				}
				""");
	}

	@Test
	void getMissingOrOtherUsersOrderIsOrderNotFound() throws Exception {
		given(this.orderService.get(any(), anyString())).willThrow(new OrderNotFoundException());

		MvcResult result = perform(get(ORDERS + "/ord-SECRET-77").header(HEADER, "MY_CARDS"));

		assertThat(result.getResponse().getStatus()).isEqualTo(404);
		assertJson(result,
				"{\"code\":\"ORDER_NOT_FOUND\",\"message\":\"주문을 찾을 수 없습니다.\",\"errors\":[]}");
		assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain("SECRET");
	}

	// GET·PUT /api/v1/me/planned-spends

	@Test
	void listPlannedSpendsReturnsContractBody() throws Exception {
		given(this.plannedSpendService.list(MY_CARDS)).willReturn(List.of(contractPlannedSpend()));

		MvcResult result = perform(get(PLANNED).header(HEADER, "MY_CARDS"));

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertJson(result, CONTRACT_PLANNED_RESPONSE);
	}

	@Test
	void listPlannedSpendsEmptyStillReportsNotUsed() throws Exception {
		given(this.plannedSpendService.list(PORTFOLIO)).willReturn(List.of());

		MvcResult result = perform(get(PLANNED).header(HEADER, "VIRTUAL_PORTFOLIO"));

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertJson(result, "{\"data\":{\"items\":[],\"usedInRecommendation\":false}}");
	}

	@Test
	void replacePlannedSpendsReturnsContractBody() throws Exception {
		given(this.plannedSpendService.replace(any(), any())).willReturn(List.of(contractPlannedSpend()));

		MvcResult result = perform(putJson(PLANNED, """
				{
				  "items": [
				    {
				      "plannedDate": "2026-11-20",
				      "amountWon": 30000,
				      "merchantClass": "STARBUCKS_STORE",
				      "paymentMethod": "DIRECT",
				      "merchantName": "스타벅스"
				    }
				  ]
				}
				""").header(HEADER, "MY_CARDS"));

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertJson(result, CONTRACT_PLANNED_RESPONSE);
		verify(this.plannedSpendService).replace(MY_CARDS, List.of(new NewPlannedSpend(LocalDate.of(2026, 11, 20),
				null, 30000, "STARBUCKS_STORE", PaymentMethod.DIRECT, "스타벅스")));
	}

	@Test
	void replacePlannedSpendsAcceptsFiveItemsInOrderWithTimes() throws Exception {
		given(this.plannedSpendService.replace(any(), any())).willReturn(List.of());

		MvcResult result = perform(putJson(PLANNED, itemsJson(5, "2026-11-01", "\"plannedTime\":\"09:00:00\""))
			.header(HEADER, "VIRTUAL_PORTFOLIO"));

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		ArgumentCaptor<List<NewPlannedSpend>> captor = listCaptor();
		verify(this.plannedSpendService).replace(org.mockito.ArgumentMatchers.eq(PORTFOLIO), captor.capture());
		assertThat(captor.getValue()).extracting(NewPlannedSpend::amountWon).containsExactly(1L, 2L, 3L, 4L, 5L);
		assertThat(captor.getValue()).extracting(NewPlannedSpend::plannedTime).containsOnly(LocalTime.of(9, 0));
	}

	@Test
	void replacePlannedSpendsWithEmptyItemsClearsAll() throws Exception {
		given(this.plannedSpendService.replace(any(), any())).willReturn(List.of());

		MvcResult result = perform(putJson(PLANNED, "{\"items\":[]}").header(HEADER, "MY_CARDS"));

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertJson(result, "{\"data\":{\"items\":[],\"usedInRecommendation\":false}}");
		verify(this.plannedSpendService).replace(MY_CARDS, List.of());
	}

	@Test
	void replacePlannedSpendsBoundaryDaysOfMonthAreAccepted() throws Exception {
		given(this.plannedSpendService.replace(any(), any())).willReturn(List.of());

		MvcResult result = perform(putJson(PLANNED, "{\"items\":[" + item("2026-11-01", 1, "") + ","
				+ item("2026-11-30", 2, "") + "]}").header(HEADER, "MY_CARDS"));

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	void replacePlannedSpendsOverFiveIsInvalidRequest() throws Exception {
		MvcResult result = perform(putJson(PLANNED, itemsJson(6, "2026-11-01", null)).header(HEADER, "MY_CARDS"));

		assertFieldError(result, "items", "5건 이하여야 합니다.");
		verify(this.plannedSpendService, never()).replace(any(), any());
	}

	@Test
	void replacePlannedSpendsMissingItemsIsInvalidRequest() throws Exception {
		MvcResult result = perform(putJson(PLANNED, "{}").header(HEADER, "MY_CARDS"));

		assertFieldError(result, "items", "필수 값입니다.");
	}

	@ParameterizedTest
	@ValueSource(strings = { "plannedDate", "amountWon", "merchantClass", "paymentMethod" })
	void replacePlannedSpendsMissingItemFieldIsInvalidRequest(String field) throws Exception {
		String body = "{\"items\":[" + item("2026-11-20", 30000, "").replaceFirst("\"" + field + "\":[^,}]+,?", "")
			.replaceAll(",(\\s*})", "$1") + "]}";

		MvcResult result = perform(putJson(PLANNED, body).header(HEADER, "MY_CARDS"));

		assertFieldError(result, "items[0]." + field, "필수 값입니다.");
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "-5" })
	void replacePlannedSpendsNonPositiveAmountIsInvalidRequest(String amount) throws Exception {
		String body = "{\"items\":[" + item("2026-11-20", 30000, "").replace("30000", amount) + "]}";

		MvcResult result = perform(putJson(PLANNED, body).header(HEADER, "MY_CARDS"));

		assertFieldError(result, "items[0].amountWon", "1 이상이어야 합니다.");
	}

	@ParameterizedTest
	@ValueSource(strings = { "2026-11-20T00:00:00", "2026/11/20", "2026-11-31" })
	void replacePlannedSpendsDateFormatErrorIsInvalidRequest(String date) throws Exception {
		MvcResult result = perform(putJson(PLANNED, "{\"items\":[" + item(date, 30000, "") + "]}")
			.header(HEADER, "MY_CARDS"));

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertJson(result, INVALID_REQUEST_EMPTY);
	}

	@ParameterizedTest
	@ValueSource(strings = { "2026-10-31", "2026-12-01", "2025-11-20" })
	void replacePlannedSpendsOutsideCurrentMonthIsInvalidRequest(String date) throws Exception {
		MvcResult result = perform(putJson(PLANNED, "{\"items\":[" + item("2026-11-20", 1, "") + ","
				+ item(date, 2, "") + "]}").header(HEADER, "MY_CARDS"));

		assertFieldError(result, "items[1].plannedDate", "이번 달 날짜여야 합니다.");
		verify(this.plannedSpendService, never()).replace(any(), any());
	}

	@Test
	void replacePlannedSpendsInvalidOptionalFieldsAreInvalidRequest() throws Exception {
		MvcResult nameResult = perform(putJson(PLANNED,
				"{\"items\":[" + item("2026-11-20", 1, ",\"merchantName\":\"" + "가".repeat(51) + "\"") + "]}")
			.header(HEADER, "MY_CARDS"));
		MvcResult classResult = perform(putJson(PLANNED,
				"{\"items\":[" + item("2026-11-20", 1, "").replace("STARBUCKS_STORE", "NOPE") + "]}")
			.header(HEADER, "MY_CARDS"));
		MvcResult timeResult = perform(putJson(PLANNED,
				"{\"items\":[" + item("2026-11-20", 1, ",\"plannedTime\":\"9:00\"") + "]}")
			.header(HEADER, "MY_CARDS"));

		assertFieldError(nameResult, "items[0].merchantName", "50자 이하여야 합니다.");
		assertFieldError(classResult, "items[0].merchantClass", "허용된 가맹점 분류가 아닙니다.");
		assertThat(timeResult.getResponse().getStatus()).isEqualTo(400);
		assertJson(timeResult, INVALID_REQUEST_EMPTY);
	}

	@Test
	void replacePlannedSpendsNumericPaymentMethodIsInvalidRequest() throws Exception {
		MvcResult result = perform(putJson(PLANNED,
				"{\"items\":[" + item("2026-11-20", 1, "").replace("\"DIRECT\"", "0") + "]}")
			.header(HEADER, "MY_CARDS"));

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertJson(result, INVALID_REQUEST_EMPTY);
		verify(this.plannedSpendService, never()).replace(any(), any());
	}

	@Test
	void monthRolloverInsideServiceIsInvalidRequest() throws Exception {
		given(this.plannedSpendService.replace(any(), any())).willThrow(new PlannedDateOutOfMonthException());

		MvcResult result = perform(putJson(PLANNED, "{\"items\":[" + item("2026-11-30", 1, "") + "]}")
			.header(HEADER, "MY_CARDS"));

		assertFieldError(result, "items", "이번 달 날짜여야 합니다.");
	}

	@Test
	void replacePlannedSpendsNullItemIsInvalidRequest() throws Exception {
		MvcResult result = perform(putJson(PLANNED, "{\"items\":[null]}").header(HEADER, "MY_CARDS"));

		assertFieldError(result, "items[0]", "필수 값입니다.");
	}

	// 시연 사용자 헤더(docs/api.md 공통 실패 응답)

	@Test
	void missingDemoUserHeaderIsInvalidRequestOnAllApis() throws Exception {
		List<MockHttpServletRequestBuilder> requests = List.of(postJson(ORDERS, CONTRACT_ORDER_REQUEST),
				get(ORDERS + "/ord-5001"), get(PLANNED), putJson(PLANNED, "{\"items\":[]}"));

		for (MockHttpServletRequestBuilder request : requests) {
			MvcResult result = perform(request);
			assertThat(result.getResponse().getStatus()).isEqualTo(400);
			assertJson(result, INVALID_REQUEST_EMPTY);
		}
		verify(this.orderService, never()).create(any(), any());
		verify(this.orderService, never()).get(any(), any());
		verify(this.plannedSpendService, never()).list(any());
		verify(this.plannedSpendService, never()).replace(any(), any());
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "my_cards", "ADMIN" })
	void invalidDemoUserHeaderIsInvalidRequestOnAllApis(String value) throws Exception {
		List<MockHttpServletRequestBuilder> requests = List.of(postJson(ORDERS, CONTRACT_ORDER_REQUEST),
				get(ORDERS + "/ord-5001"), get(PLANNED), putJson(PLANNED, "{\"items\":[]}"));

		for (MockHttpServletRequestBuilder request : requests) {
			MvcResult result = perform(request.header(HEADER, value));
			assertThat(result.getResponse().getStatus()).isEqualTo(400);
			assertJson(result, INVALID_REQUEST_EMPTY);
		}
	}

	private static final String CONTRACT_PLANNED_RESPONSE = """
			{
			  "data": {
			    "items": [
			      {
			        "plannedSpendId": "ps-1",
			        "plannedDate": "2026-11-20",
			        "amountWon": 30000,
			        "merchantClass": "STARBUCKS_STORE",
			        "paymentMethod": "DIRECT",
			        "merchantName": "스타벅스",
			        "plannedTime": null
			      }
			    ],
			    "usedInRecommendation": false
			  }
			}
			""";

	private static PlannedSpendDetail contractPlannedSpend() {
		return new PlannedSpendDetail("ps-1", LocalDate.of(2026, 11, 20), 30000, "STARBUCKS_STORE",
				PaymentMethod.DIRECT, "스타벅스", null);
	}

	private static String item(String date, long amount, String extra) {
		return "{\"plannedDate\":\"" + date + "\",\"amountWon\":" + amount
				+ ",\"merchantClass\":\"STARBUCKS_STORE\",\"paymentMethod\":\"DIRECT\"" + extra + "}";
	}

	private static String itemsJson(int count, String date, String extraField) {
		StringBuilder builder = new StringBuilder("{\"items\":[");
		for (int i = 1; i <= count; i++) {
			builder.append(i > 1 ? "," : "").append(item(date, i, (extraField == null) ? "" : "," + extraField));
		}
		return builder.append("]}").toString();
	}

	@SuppressWarnings("unchecked")
	private static ArgumentCaptor<List<NewPlannedSpend>> listCaptor() {
		return ArgumentCaptor.forClass((Class<List<NewPlannedSpend>>) (Class<?>) List.class);
	}

	private MvcResult perform(MockHttpServletRequestBuilder request) throws Exception {
		return this.mockMvc.perform(request).andReturn();
	}

	private static MockHttpServletRequestBuilder postJson(String path, String body) {
		return post(path).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static MockHttpServletRequestBuilder putJson(String path, String body) {
		return put(path).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static void assertFieldError(MvcResult result, String field, String message) throws Exception {
		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertJson(result, "{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 값을 확인해 주세요.\",\"errors\":[{\"field\":\""
				+ field + "\",\"message\":\"" + message + "\"}]}");
	}

	private static void assertJson(MvcResult result, String expected) throws Exception {
		assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
		JsonAssert.comparator(JsonCompareMode.STRICT)
			.assertIsMatch(expected, result.getResponse().getContentAsString(StandardCharsets.UTF_8));
	}

	/** "이번 달"을 2026-11(서울 2026-11-17 12:00)로 고정한다. */
	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClockConfiguration {

		@Bean
		OrderClock orderClock() {
			return new OrderClock(Clock.fixed(Instant.parse("2026-11-17T03:00:00Z"), ZoneOffset.UTC));
		}

	}

}
