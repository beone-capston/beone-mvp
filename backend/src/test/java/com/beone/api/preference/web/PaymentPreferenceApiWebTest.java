package com.beone.api.preference.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import com.beone.api.card.application.UserCardNotFoundException;
import com.beone.api.preference.application.PaymentPreferenceChange;
import com.beone.api.preference.application.PaymentPreferenceService;
import com.beone.api.preference.application.PaymentPreferenceView;
import com.beone.api.preference.application.PaymentSelectionMode;
import com.beone.api.preference.application.RepresentativeCardNotEligibleException;
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
 * 결제 전 설정 API 2개의 응답 형식·요청 검증·실패 응답을 MockMvc로 확인한다.
 *
 * <p>기대 JSON은 docs/api/preferences.md 응답 예시와 실패 목록, docs/api.md 공통 실패 응답에서 옮겼다.
 */
@WebSliceTest(controllers = PaymentPreferenceController.class)
@ActiveProfiles("local")
class PaymentPreferenceApiWebTest {

	private static final String PATH = "/api/v1/me/payment-preference";

	private static final String HEADER = "X-BeONE-Demo-User";

	private static final String INVALID_REQUEST_EMPTY = "{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 값을 확인해 주세요.\",\"errors\":[]}";

	private static final CurrentUser MY_CARDS = new CurrentUser(1L, DemoUser.MY_CARDS);

	private static final CurrentUser PORTFOLIO = new CurrentUser(2L, DemoUser.VIRTUAL_PORTFOLIO);

	private static final LocalDateTime SAVED = LocalDateTime.of(2026, 11, 3, 10, 30, 0);

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	PaymentPreferenceService paymentPreferenceService;

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
		given(this.paymentPreferenceService.get(MY_CARDS))
			.willReturn(new PaymentPreferenceView(PaymentSelectionMode.AUTO_RECOMMEND, "uc-1001", SAVED));

		assertJson(perform(get(PATH).header(HEADER, "MY_CARDS")), """
				{"data":{"selectionMode":"AUTO_RECOMMEND","representativeUserCardId":"uc-1001",
				 "updatedAt":"2026-11-03T10:30:00+09:00"}}
				""", 200);
	}

	@Test
	void getNeverSavedShowsAutoRecommendWithNullsKept() throws Exception {
		given(this.paymentPreferenceService.get(PORTFOLIO)).willReturn(PaymentPreferenceView.NOT_SET);

		assertJson(perform(get(PATH).header(HEADER, "VIRTUAL_PORTFOLIO")), """
				{"data":{"selectionMode":"AUTO_RECOMMEND","representativeUserCardId":null,"updatedAt":null}}
				""", 200);
	}

	// PUT

	@Test
	void putReturnsContractBody() throws Exception {
		given(this.paymentPreferenceService.change(MY_CARDS,
				new PaymentPreferenceChange(PaymentSelectionMode.FIXED_REPRESENTATIVE, "uc-1001")))
			.willReturn(new PaymentPreferenceView(PaymentSelectionMode.FIXED_REPRESENTATIVE, "uc-1001", SAVED));

		assertJson(perform(json(put(PATH), """
				{"selectionMode":"FIXED_REPRESENTATIVE","representativeUserCardId":"uc-1001"}
				""").header(HEADER, "MY_CARDS")), """
				{"data":{"selectionMode":"FIXED_REPRESENTATIVE","representativeUserCardId":"uc-1001",
				 "updatedAt":"2026-11-03T10:30:00+09:00"}}
				""", 200);
	}

	@Test
	void putAutoRecommendWithExplicitNullClearsRepresentative() throws Exception {
		PaymentPreferenceChange change = new PaymentPreferenceChange(PaymentSelectionMode.AUTO_RECOMMEND, null);
		given(this.paymentPreferenceService.change(PORTFOLIO, change))
			.willReturn(new PaymentPreferenceView(PaymentSelectionMode.AUTO_RECOMMEND, null, SAVED));

		assertJson(perform(json(put(PATH), "{\"selectionMode\":\"AUTO_RECOMMEND\",\"representativeUserCardId\":null}")
			.header(HEADER, "VIRTUAL_PORTFOLIO")), """
				{"data":{"selectionMode":"AUTO_RECOMMEND","representativeUserCardId":null,
				 "updatedAt":"2026-11-03T10:30:00+09:00"}}
				""", 200);
		verify(this.paymentPreferenceService).change(PORTFOLIO, change);
	}

	@Test
	void putRejectsMissingOrDisallowedSelectionMode() throws Exception {
		assertFieldError("{\"representativeUserCardId\":null}", "selectionMode", "필수 값입니다.");
		assertFieldError("{\"selectionMode\":null,\"representativeUserCardId\":null}", "selectionMode", "필수 값입니다.");
		assertFieldError("{\"selectionMode\":\"auto_recommend\",\"representativeUserCardId\":null}", "selectionMode",
				"허용되지 않는 값입니다.");
		assertFieldError("{\"selectionMode\":\"MANUAL\",\"representativeUserCardId\":\"uc-1\"}", "selectionMode",
				"허용되지 않는 값입니다.");
		verifyNoInteractions(this.paymentPreferenceService);
	}

	@Test
	void putRejectsMissingRepresentativeFieldInEitherMode() throws Exception {
		assertFieldError("{\"selectionMode\":\"AUTO_RECOMMEND\"}", "representativeUserCardId", "필수 값입니다.");
		assertFieldError("{\"selectionMode\":\"FIXED_REPRESENTATIVE\"}", "representativeUserCardId", "필수 값입니다.");
		verifyNoInteractions(this.paymentPreferenceService);
	}

	@Test
	void putRejectsFixedModeWithNullRepresentative() throws Exception {
		assertFieldError("{\"selectionMode\":\"FIXED_REPRESENTATIVE\",\"representativeUserCardId\":null}",
				"representativeUserCardId", "대표 카드 고정 모드에는 대표 카드가 필요합니다.");
		verifyNoInteractions(this.paymentPreferenceService);
	}

	@ParameterizedTest
	@ValueSource(strings = { "{\"selectionMode\":0,\"representativeUserCardId\":null}",
			"{\"selectionMode\":\"AUTO_RECOMMEND\",\"representativeUserCardId\":1001}",
			"{\"selectionMode\":\"AUTO_RECOMMEND\",\"representativeUserCardId\":true}",
			"{\"selectionMode\":[\"AUTO_RECOMMEND\"],\"representativeUserCardId\":null}", "[]", "{", "" })
	void putRejectsNonStringValuesAndMalformedBodies(String body) throws Exception {
		assertJson(perform(json(put(PATH), body).header(HEADER, "MY_CARDS")), INVALID_REQUEST_EMPTY, 400);
		verifyNoInteractions(this.paymentPreferenceService);
	}

	@Test
	void putMapsMissingOrForeignCardTo404() throws Exception {
		given(this.paymentPreferenceService.change(eq(MY_CARDS), any())).willThrow(new UserCardNotFoundException());

		assertJson(perform(json(put(PATH), "{\"selectionMode\":\"AUTO_RECOMMEND\",\"representativeUserCardId\":\"uc-9\"}")
			.header(HEADER, "MY_CARDS")),
				"{\"code\":\"USER_CARD_NOT_FOUND\",\"message\":\"보유 카드를 찾을 수 없습니다.\",\"errors\":[]}", 404);
	}

	@Test
	void putMapsIneligibleCardTo409() throws Exception {
		given(this.paymentPreferenceService.change(eq(MY_CARDS), any()))
			.willThrow(new RepresentativeCardNotEligibleException());

		assertJson(perform(json(put(PATH),
				"{\"selectionMode\":\"FIXED_REPRESENTATIVE\",\"representativeUserCardId\":\"uc-2\"}")
			.header(HEADER, "MY_CARDS")), """
				{"code":"REPRESENTATIVE_CARD_NOT_ELIGIBLE","message":"추천 비교 대상인 카드만 대표 카드로 고를 수 있습니다.",
				 "errors":[]}
				""", 409);
	}

	@Test
	void unexpectedFailureIs500WithoutDetails() throws Exception {
		given(this.paymentPreferenceService.get(MY_CARDS)).willThrow(new IllegalStateException("secret detail"));

		MvcResult result = perform(get(PATH).header(HEADER, "MY_CARDS"));

		assertJson(result, "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"요청 처리 중 오류가 발생했습니다.\",\"errors\":[]}",
				500);
		assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain("secret");
	}

	@Test
	void rejectsMissingOrInvalidDemoUserHeader() throws Exception {
		String body = "{\"selectionMode\":\"AUTO_RECOMMEND\",\"representativeUserCardId\":null}";
		assertJson(perform(get(PATH)), INVALID_REQUEST_EMPTY, 400);
		assertJson(perform(json(put(PATH), body)), INVALID_REQUEST_EMPTY, 400);
		for (String invalid : List.of("my_cards", "UNKNOWN", "")) {
			assertJson(perform(get(PATH).header(HEADER, invalid)), INVALID_REQUEST_EMPTY, 400);
			assertJson(perform(json(put(PATH), body).header(HEADER, invalid)), INVALID_REQUEST_EMPTY, 400);
		}
		verifyNoInteractions(this.paymentPreferenceService);
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
