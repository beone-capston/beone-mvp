package com.beone.api.card.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import com.beone.api.card.application.CandidateMismatchException;
import com.beone.api.card.application.CardProductNotFoundException;
import com.beone.api.card.application.ConfirmationMethod;
import com.beone.api.card.application.CurrentUserSummary;
import com.beone.api.card.application.HoldingSource;
import com.beone.api.card.application.MappingChange;
import com.beone.api.card.application.MappingStatus;
import com.beone.api.card.application.NewUserCard;
import com.beone.api.card.application.PortfolioReadOnlyException;
import com.beone.api.card.application.RecommendationEligibility;
import com.beone.api.card.application.RepresentativeCardInUseException;
import com.beone.api.card.application.UserCardChange;
import com.beone.api.card.application.UserCardChangeResult;
import com.beone.api.card.application.UserCardDuplicatedException;
import com.beone.api.card.application.UserCardMapping;
import com.beone.api.card.application.UserCardNotFoundException;
import com.beone.api.card.application.UserCardService;
import com.beone.api.card.application.UserCardView;
import com.beone.api.support.WebSliceTest;
import com.beone.api.user.application.CurrentUser;
import com.beone.api.user.application.DemoUser;
import com.beone.api.user.application.DemoUserService;
import com.beone.api.user.application.PortfolioType;
import com.beone.api.user.application.UserProfile;
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
 * 현재 사용자·보유 카드 API 6개의 요청 검증·응답 형식·실패 응답을 MockMvc로 확인한다.
 *
 * <p>기대 JSON은 docs/api/cards.md 응답 예시, 실패 응답은 같은 문서의 실패 목록과 docs/api.md 공통 실패 응답에서 옮겼다. 계약에
 * 없던 값은 결정 27 A2절을 따른다.
 */
@WebSliceTest(controllers = { UserCardController.class, CurrentUserController.class })
@ActiveProfiles("local")
class UserCardApiWebTest {

	private static final String CARDS = "/api/v1/me/cards";

	private static final String HEADER = "X-BeONE-Demo-User";

	private static final String INVALID_REQUEST_EMPTY = "{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 값을 확인해 주세요.\",\"errors\":[]}";

	private static final String USER_CARD_NOT_FOUND = "{\"code\":\"USER_CARD_NOT_FOUND\",\"message\":\"보유 카드를 찾을 수 없습니다.\",\"errors\":[]}";

	private static final String CARD_PRODUCT_NOT_FOUND = "{\"code\":\"CARD_PRODUCT_NOT_FOUND\",\"message\":\"카드 상품을 찾을 수 없습니다.\",\"errors\":[]}";

	private static final String READ_ONLY = "{\"code\":\"PORTFOLIO_READ_ONLY\",\"message\":\"가상 포트폴리오의 보유 카드는 바꿀 수 없습니다.\",\"errors\":[]}";

	private static final String DUPLICATED = "{\"code\":\"USER_CARD_DUPLICATED\",\"message\":\"같은 상품의 카드가 이미 등록되어 있습니다.\",\"errors\":[]}";

	private static final String REPRESENTATIVE_IN_USE = "{\"code\":\"REPRESENTATIVE_CARD_IN_USE\",\"message\":\"결제 전 설정의 대표 카드입니다. 먼저 설정을 바꿔 주세요.\",\"errors\":[]}";

	private static final CurrentUser MY_CARDS = new CurrentUser(1L, DemoUser.MY_CARDS);

	private static final CurrentUser PORTFOLIO = new CurrentUser(2L, DemoUser.VIRTUAL_PORTFOLIO);

	private static final UserCardView CONFIRMED_CARD = new UserCardView("uc-1001", "청춘대로 톡톡", HoldingSource.MANUAL,
			null,
			new UserCardMapping(MappingStatus.USER_CONFIRMED, "09174", ConfirmationMethod.MANUAL_SELECTION,
					LocalDateTime.of(2026, 11, 3, 10, 12, 0)),
			false, RecommendationEligibility.of(HoldingSource.MANUAL, MappingStatus.USER_CONFIRMED, false),
			LocalDateTime.of(2026, 11, 3, 10, 12, 0));

	private static final UserCardView NOT_SUPPORTED_CARD = new UserCardView("uc-1002", "회사 법인카드",
			HoldingSource.MANUAL, null,
			new UserCardMapping(MappingStatus.NOT_SUPPORTED, null, ConfirmationMethod.MANUAL_SELECTION,
					LocalDateTime.of(2026, 11, 3, 10, 13, 0)),
			false, RecommendationEligibility.of(HoldingSource.MANUAL, MappingStatus.NOT_SUPPORTED, false),
			LocalDateTime.of(2026, 11, 3, 10, 13, 0));

	private static final String CONFIRMED_ITEM = """
			{
			  "userCardId": "uc-1001",
			  "displayName": "청춘대로 톡톡",
			  "holdingSource": "MANUAL",
			  "mapping": {
			    "status": "USER_CONFIRMED",
			    "cardProductId": "09174",
			    "confirmationMethod": "MANUAL_SELECTION",
			    "confirmedAt": "2026-11-03T10:12:00+09:00",
			    "candidateCardProductId": null,
			    "candidateBasis": null,
			    "note": "사용자 확인 매핑, 공식 SKU·약관 버전 미검증"
			  },
			  "excludedFromRecommendation": false,
			  "recommendationEligibility": {
			    "eligible": true,
			    "reason": null
			  },
			  "registeredAt": "2026-11-03T10:12:00+09:00"
			}
			""";

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	UserCardService userCardService;

	@MockitoBean
	DemoUserService demoUserService;

	@BeforeEach
	void resolveDemoUsers() {
		given(this.demoUserService.currentUserOf(DemoUser.MY_CARDS)).willReturn(MY_CARDS);
		given(this.demoUserService.currentUserOf(DemoUser.VIRTUAL_PORTFOLIO)).willReturn(PORTFOLIO);
	}

	// GET /api/v1/me

	@Test
	void currentUserReturnsContractBody() throws Exception {
		given(this.userCardService.summarize(MY_CARDS)).willReturn(new CurrentUserSummary(
				new UserProfile(DemoUser.MY_CARDS, "내 카드", PortfolioType.REAL, false), 2, false));

		MvcResult result = perform(get("/api/v1/me").header(HEADER, "MY_CARDS"));

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertJson(result, """
				{
				  "data": {
				    "demoUser": "MY_CARDS",
				    "displayName": "내 카드",
				    "portfolioType": "REAL",
				    "readOnly": false,
				    "cardCount": 2,
				    "codefConnected": false
				  }
				}
				""");
	}

	// GET /api/v1/me/cards

	@Test
	void listCardsReturnsContractBody() throws Exception {
		given(this.userCardService.list(MY_CARDS)).willReturn(List.of(CONFIRMED_CARD, NOT_SUPPORTED_CARD));

		MvcResult result = perform(get(CARDS).header(HEADER, "MY_CARDS"));

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertJson(result, """
				{
				  "data": {
				    "items": [
				      %s,
				      {
				        "userCardId": "uc-1002",
				        "displayName": "회사 법인카드",
				        "holdingSource": "MANUAL",
				        "mapping": {
				          "status": "NOT_SUPPORTED",
				          "cardProductId": null,
				          "confirmationMethod": "MANUAL_SELECTION",
				          "confirmedAt": "2026-11-03T10:13:00+09:00",
				          "candidateCardProductId": null,
				          "candidateBasis": null,
				          "note": null
				        },
				        "excludedFromRecommendation": false,
				        "recommendationEligibility": {
				          "eligible": false,
				          "reason": "NO_SUPPORTED_RULE"
				        },
				        "registeredAt": "2026-11-03T10:13:00+09:00"
				      }
				    ]
				  }
				}
				""".formatted(CONFIRMED_ITEM));
	}

	@Test
	void syntheticCardHasNoMappingAndCarriesCardProductIdDirectly() throws Exception {
		UserCardView synthetic = new UserCardView("uc-3", "청춘대로 톡톡카드", HoldingSource.SYNTHETIC, "09174", null,
				false, RecommendationEligibility.of(HoldingSource.SYNTHETIC, null, false),
				LocalDateTime.of(2026, 11, 17, 9, 0, 0));
		given(this.userCardService.list(PORTFOLIO)).willReturn(List.of(synthetic));

		MvcResult result = perform(get(CARDS).header(HEADER, "VIRTUAL_PORTFOLIO"));

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertJson(result, """
				{"data":{"items":[{
				  "userCardId": "uc-3",
				  "displayName": "청춘대로 톡톡카드",
				  "holdingSource": "SYNTHETIC",
				  "cardProductId": "09174",
				  "mapping": null,
				  "excludedFromRecommendation": false,
				  "recommendationEligibility": {"eligible": true, "reason": null},
				  "registeredAt": "2026-11-17T09:00:00+09:00"
				}]}}
				""");
	}

	@Test
	void unmappedAndExcludedCardsShowTheirReasons() throws Exception {
		UserCardView unmapped = new UserCardView("uc-7", "모르는 카드", HoldingSource.MANUAL, null,
				new UserCardMapping(MappingStatus.UNMAPPED, null, null, null), false,
				RecommendationEligibility.of(HoldingSource.MANUAL, MappingStatus.UNMAPPED, false),
				LocalDateTime.of(2026, 11, 3, 10, 14, 0));
		UserCardView excluded = new UserCardView("uc-8", "뺀 카드", HoldingSource.MANUAL, null,
				new UserCardMapping(MappingStatus.UNMAPPED, null, null, null), true,
				RecommendationEligibility.of(HoldingSource.MANUAL, MappingStatus.UNMAPPED, true),
				LocalDateTime.of(2026, 11, 3, 10, 15, 0));
		given(this.userCardService.list(MY_CARDS)).willReturn(List.of(unmapped, excluded));

		MvcResult result = perform(get(CARDS).header(HEADER, "MY_CARDS"));

		assertJson(result, """
				{"data":{"items":[
				  {"userCardId":"uc-7","displayName":"모르는 카드","holdingSource":"MANUAL",
				   "mapping":{"status":"UNMAPPED","cardProductId":null,"confirmationMethod":null,"confirmedAt":null,
				              "candidateCardProductId":null,"candidateBasis":null,"note":null},
				   "excludedFromRecommendation":false,
				   "recommendationEligibility":{"eligible":false,"reason":"MAPPING_UNCONFIRMED"},
				   "registeredAt":"2026-11-03T10:14:00+09:00"},
				  {"userCardId":"uc-8","displayName":"뺀 카드","holdingSource":"MANUAL",
				   "mapping":{"status":"UNMAPPED","cardProductId":null,"confirmationMethod":null,"confirmedAt":null,
				              "candidateCardProductId":null,"candidateBasis":null,"note":null},
				   "excludedFromRecommendation":true,
				   "recommendationEligibility":{"eligible":false,"reason":"USER_EXCLUDED"},
				   "registeredAt":"2026-11-03T10:15:00+09:00"}
				]}}
				""");
	}

	@Test
	void userResourceApisRejectMissingOrInvalidDemoUserHeader() throws Exception {
		List<MockHttpServletRequestBuilder> requests = List.of(get("/api/v1/me"), get(CARDS),
				json(post(CARDS), "{\"displayName\":\"카드\"}"),
				json(patch(CARDS + "/uc-1/mapping"), "{\"status\":\"UNMAPPED\"}"),
				json(patch(CARDS + "/uc-1"), "{\"displayName\":\"카드\"}"), delete(CARDS + "/uc-1"));
		for (MockHttpServletRequestBuilder request : requests) {
			assertJson(perform(request), INVALID_REQUEST_EMPTY, 400);
		}
		for (String invalid : List.of("my_cards", "UNKNOWN", "")) {
			assertJson(perform(get(CARDS).header(HEADER, invalid)), INVALID_REQUEST_EMPTY, 400);
			assertJson(perform(get("/api/v1/me").header(HEADER, invalid)), INVALID_REQUEST_EMPTY, 400);
		}
		verifyNoInteractions(this.userCardService);
	}

	// POST /api/v1/me/cards

	@Test
	void registerCardReturns201WithContractBody() throws Exception {
		given(this.userCardService.register(any(), any())).willReturn(CONFIRMED_CARD);

		MvcResult result = perform(json(post(CARDS), """
				{"displayName": "청춘대로 톡톡", "cardProductId": "09174", "notSupported": false}
				""").header(HEADER, "MY_CARDS"));

		assertThat(result.getResponse().getStatus()).isEqualTo(201);
		assertJson(result, "{\"data\":" + CONFIRMED_ITEM + "}");
		verify(this.userCardService).register(MY_CARDS, new NewUserCard("청춘대로 톡톡", "09174", false));
	}

	@Test
	void registerCardTrimsDisplayNameAndMapsOptionalChoices() throws Exception {
		given(this.userCardService.register(any(), any())).willReturn(NOT_SUPPORTED_CARD);

		perform(json(post(CARDS), "{\"displayName\":\"  회사 법인카드 \",\"notSupported\":true}").header(HEADER,
				"MY_CARDS"));
		perform(json(post(CARDS), "{\"displayName\":\"모르는 카드\"}").header(HEADER, "MY_CARDS"));

		verify(this.userCardService).register(MY_CARDS, new NewUserCard("회사 법인카드", null, true));
		verify(this.userCardService).register(MY_CARDS, new NewUserCard("모르는 카드", null, false));
	}

	@Test
	void registerCardAcceptsThirtyCharacterName() throws Exception {
		given(this.userCardService.register(any(), any())).willReturn(CONFIRMED_CARD);
		String thirty = "가".repeat(29) + "😀";

		MvcResult result = perform(
				json(post(CARDS), "{\"displayName\":\"" + thirty + "\"}").header(HEADER, "MY_CARDS"));

		assertThat(result.getResponse().getStatus()).isEqualTo(201);
	}

	@Test
	void registerCardRejectsInvalidDisplayName() throws Exception {
		assertFieldError(post(CARDS), "{}", "displayName", "필수 값입니다.");
		assertFieldError(post(CARDS), "{\"displayName\":null}", "displayName", "필수 값입니다.");
		String lengthMessage = "앞뒤 공백을 빼고 1~30자여야 합니다.";
		assertFieldError(post(CARDS), "{\"displayName\":\"   \"}", "displayName", lengthMessage);
		assertFieldError(post(CARDS), "{\"displayName\":\"\"}", "displayName", lengthMessage);
		assertFieldError(post(CARDS), "{\"displayName\":\"" + "가".repeat(31) + "\"}", "displayName", lengthMessage);
		verifyNoInteractions(this.userCardService);
	}

	@Test
	void registerCardRejectsProductWithNotSupported() throws Exception {
		MvcResult result = perform(json(post(CARDS),
				"{\"displayName\":\"카드\",\"cardProductId\":\"09174\",\"notSupported\":true}")
			.header(HEADER, "MY_CARDS"));

		assertJson(result, """
				{"code":"INVALID_REQUEST","message":"요청 값을 확인해 주세요.",
				 "errors":[{"field":null,"message":"cardProductId와 notSupported=true를 함께 보낼 수 없습니다."}]}
				""", 400);
		verifyNoInteractions(this.userCardService);
	}

	@ParameterizedTest
	@ValueSource(strings = { "{\"displayName\":\"카드\",\"cardNumber\":\"1234567812345678\"}",
			"{\"displayName\":\"카드\",\"last4\":\"5678\"}", "{\"displayName\":\"카드\",\"cvv\":\"123\"}",
			"{\"displayName\":\"카드\",\"expiry\":\"12/30\"}", "{\"displayName\":\"카드\",\"cardPassword\":\"12\"}",
			"{\"displayName\":\"카드\",\"notSupported\":\"true\"}", "{\"displayName\":\"카드\",\"notSupported\":1}",
			"{\"displayName\":", "[]" })
	void registerCardRejectsUnknownFieldsAndMalformedBodies(String body) throws Exception {
		MvcResult result = perform(json(post(CARDS), body).header(HEADER, "MY_CARDS"));

		assertJson(result, INVALID_REQUEST_EMPTY, 400);
		assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain("1234", "5678",
				"cardNumber", "cvv");
		verifyNoInteractions(this.userCardService);
	}

	@Test
	void registerCardMapsServiceFailures() throws Exception {
		given(this.userCardService.register(eq(MY_CARDS), any())).willThrow(new CardProductNotFoundException());
		assertJson(perform(json(post(CARDS), "{\"displayName\":\"카드\",\"cardProductId\":\"9174\"}").header(HEADER,
				"MY_CARDS")), CARD_PRODUCT_NOT_FOUND, 404);

		given(this.userCardService.register(eq(MY_CARDS), any())).willThrow(new UserCardDuplicatedException());
		assertJson(perform(json(post(CARDS), "{\"displayName\":\"카드\",\"cardProductId\":\"09174\"}").header(HEADER,
				"MY_CARDS")), DUPLICATED, 409);

		given(this.userCardService.register(eq(PORTFOLIO), any())).willThrow(new PortfolioReadOnlyException());
		assertJson(perform(json(post(CARDS), "{\"displayName\":\"카드\"}").header(HEADER, "VIRTUAL_PORTFOLIO")),
				READ_ONLY, 409);
	}

	// PATCH /api/v1/me/cards/{userCardId}/mapping

	@Test
	void changeMappingReturnsContractShapeWithoutRegisteredAt() throws Exception {
		given(this.userCardService.changeMapping(any(), anyString(), any()))
			.willReturn(new UserCardChangeResult(CONFIRMED_CARD, List.of()));

		MvcResult result = perform(json(patch(CARDS + "/uc-1001/mapping"), """
				{"status": "USER_CONFIRMED", "cardProductId": "09174", "confirmationMethod": "MANUAL_SELECTION"}
				""").header(HEADER, "MY_CARDS"));

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertJson(result, """
				{
				  "data": {
				    "userCardId": "uc-1001",
				    "displayName": "청춘대로 톡톡",
				    "holdingSource": "MANUAL",
				    "mapping": {
				      "status": "USER_CONFIRMED",
				      "cardProductId": "09174",
				      "confirmationMethod": "MANUAL_SELECTION",
				      "confirmedAt": "2026-11-03T10:12:00+09:00",
				      "candidateCardProductId": null,
				      "candidateBasis": null,
				      "note": "사용자 확인 매핑, 공식 SKU·약관 버전 미검증"
				    },
				    "excludedFromRecommendation": false,
				    "recommendationEligibility": {
				      "eligible": true,
				      "reason": null
				    },
				    "invalidatedRecommendationIds": []
				  }
				}
				""");
		verify(this.userCardService).changeMapping(MY_CARDS, "uc-1001",
				new MappingChange(MappingStatus.USER_CONFIRMED, "09174", ConfirmationMethod.MANUAL_SELECTION));
	}

	@Test
	void changeMappingConvertsNotSupportedAndUnmapped() throws Exception {
		given(this.userCardService.changeMapping(any(), anyString(), any()))
			.willReturn(new UserCardChangeResult(NOT_SUPPORTED_CARD, List.of()));

		perform(json(patch(CARDS + "/uc-1/mapping"), "{\"status\":\"NOT_SUPPORTED\"}").header(HEADER, "MY_CARDS"));
		perform(json(patch(CARDS + "/uc-2/mapping"),
				"{\"status\":\"NOT_SUPPORTED\",\"confirmationMethod\":\"MANUAL_SELECTION\"}")
			.header(HEADER, "MY_CARDS"));
		perform(json(patch(CARDS + "/uc-3/mapping"), "{\"status\":\"UNMAPPED\"}").header(HEADER, "MY_CARDS"));

		MappingChange notSupported = new MappingChange(MappingStatus.NOT_SUPPORTED, null,
				ConfirmationMethod.MANUAL_SELECTION);
		verify(this.userCardService).changeMapping(MY_CARDS, "uc-1", notSupported);
		verify(this.userCardService).changeMapping(MY_CARDS, "uc-2", notSupported);
		verify(this.userCardService).changeMapping(MY_CARDS, "uc-3",
				new MappingChange(MappingStatus.UNMAPPED, null, null));
	}

	@Test
	void changeMappingRejectsInvalidRequests() throws Exception {
		String path = CARDS + "/uc-1/mapping";
		assertFieldError(patch(path), "{}", "status", "필수 값입니다.");
		assertFieldError(patch(path), "{\"status\":\"CANDIDATE\"}", "status", "허용되지 않는 값입니다.");
		assertFieldError(patch(path), "{\"status\":\"OFFICIALLY_VERIFIED\"}", "status", "허용되지 않는 값입니다.");
		assertFieldError(patch(path), "{\"status\":\"user_confirmed\"}", "status", "허용되지 않는 값입니다.");
		assertFieldError(patch(path), "{\"status\":\"USER_CONFIRMED\",\"confirmationMethod\":\"MANUAL_SELECTION\"}",
				"cardProductId", "필수 값입니다.");
		assertFieldError(patch(path), "{\"status\":\"USER_CONFIRMED\",\"cardProductId\":\"09174\"}",
				"confirmationMethod", "필수 값입니다.");
		assertFieldError(patch(path),
				"{\"status\":\"USER_CONFIRMED\",\"cardProductId\":\"09174\",\"confirmationMethod\":\"AUTO\"}",
				"confirmationMethod", "허용되지 않는 값입니다.");
		assertFieldError(patch(path), "{\"status\":\"NOT_SUPPORTED\",\"cardProductId\":\"09174\"}", "cardProductId",
				"이 상태에서는 보낼 수 없습니다.");
		assertFieldError(patch(path), "{\"status\":\"NOT_SUPPORTED\",\"confirmationMethod\":\"CODEF_CANDIDATE\"}",
				"confirmationMethod", "이 상태에서는 보낼 수 없습니다.");
		assertFieldError(patch(path), "{\"status\":\"UNMAPPED\",\"cardProductId\":\"09174\"}", "cardProductId",
				"이 상태에서는 보낼 수 없습니다.");
		assertFieldError(patch(path), "{\"status\":\"UNMAPPED\",\"confirmationMethod\":\"MANUAL_SELECTION\"}",
				"confirmationMethod", "이 상태에서는 보낼 수 없습니다.");
		assertJson(perform(json(patch(path), "{\"status\":\"UNMAPPED\",\"note\":\"x\"}").header(HEADER, "MY_CARDS")),
				INVALID_REQUEST_EMPTY, 400);
		verifyNoInteractions(this.userCardService);
	}

	@Test
	void changeMappingMapsServiceFailures() throws Exception {
		String body = "{\"status\":\"USER_CONFIRMED\",\"cardProductId\":\"09174\",\"confirmationMethod\":\"CODEF_CANDIDATE\"}";
		given(this.userCardService.changeMapping(eq(MY_CARDS), eq("uc-1"), any()))
			.willThrow(new CandidateMismatchException());
		assertJson(perform(json(patch(CARDS + "/uc-1/mapping"), body).header(HEADER, "MY_CARDS")), """
				{"code":"INVALID_REQUEST","message":"요청 값을 확인해 주세요.",
				 "errors":[{"field":"confirmationMethod","message":"현재 후보와 일치하지 않습니다."}]}
				""", 400);

		given(this.userCardService.changeMapping(eq(MY_CARDS), eq("uc-2"), any()))
			.willThrow(new UserCardNotFoundException());
		assertJson(perform(json(patch(CARDS + "/uc-2/mapping"), body).header(HEADER, "MY_CARDS")),
				USER_CARD_NOT_FOUND, 404);

		given(this.userCardService.changeMapping(eq(MY_CARDS), eq("uc-3"), any()))
			.willThrow(new CardProductNotFoundException());
		assertJson(perform(json(patch(CARDS + "/uc-3/mapping"), body).header(HEADER, "MY_CARDS")),
				CARD_PRODUCT_NOT_FOUND, 404);

		given(this.userCardService.changeMapping(eq(MY_CARDS), eq("uc-4"), any()))
			.willThrow(new UserCardDuplicatedException());
		assertJson(perform(json(patch(CARDS + "/uc-4/mapping"), body).header(HEADER, "MY_CARDS")), DUPLICATED, 409);

		given(this.userCardService.changeMapping(eq(PORTFOLIO), anyString(), any()))
			.willThrow(new PortfolioReadOnlyException());
		assertJson(perform(json(patch(CARDS + "/uc-1/mapping"), body).header(HEADER, "VIRTUAL_PORTFOLIO")),
				READ_ONLY, 409);
	}

	// PATCH /api/v1/me/cards/{userCardId}

	@Test
	void updateCardReturnsListItemAndInvalidatedIdsOnlyWhenExclusionChanged() throws Exception {
		UserCardView excluded = new UserCardView("uc-1001", "청춘대로 톡톡", HoldingSource.MANUAL, null,
				CONFIRMED_CARD.mapping(), true,
				RecommendationEligibility.of(HoldingSource.MANUAL, MappingStatus.USER_CONFIRMED, true),
				CONFIRMED_CARD.registeredAt());
		given(this.userCardService.update(eq(MY_CARDS), eq("uc-1001"), any()))
			.willReturn(new UserCardChangeResult(excluded, List.of()));
		given(this.userCardService.update(eq(MY_CARDS), eq("uc-1002"), any()))
			.willReturn(new UserCardChangeResult(CONFIRMED_CARD, null));

		MvcResult exclusion = perform(json(patch(CARDS + "/uc-1001"), """
				{"displayName": "청춘대로 톡톡", "excludedFromRecommendation": true}
				""").header(HEADER, "MY_CARDS"));
		MvcResult rename = perform(
				json(patch(CARDS + "/uc-1002"), "{\"displayName\":\" 청춘대로 톡톡 \"}").header(HEADER, "MY_CARDS"));

		assertThat(exclusion.getResponse().getStatus()).isEqualTo(200);
		assertJson(exclusion, """
				{"data":{
				  "userCardId":"uc-1001","displayName":"청춘대로 톡톡","holdingSource":"MANUAL",
				  "mapping":{"status":"USER_CONFIRMED","cardProductId":"09174","confirmationMethod":"MANUAL_SELECTION",
				             "confirmedAt":"2026-11-03T10:12:00+09:00","candidateCardProductId":null,"candidateBasis":null,
				             "note":"사용자 확인 매핑, 공식 SKU·약관 버전 미검증"},
				  "excludedFromRecommendation":true,
				  "recommendationEligibility":{"eligible":false,"reason":"USER_EXCLUDED"},
				  "registeredAt":"2026-11-03T10:12:00+09:00",
				  "invalidatedRecommendationIds":[]
				}}
				""");
		assertThat(rename.getResponse().getStatus()).isEqualTo(200);
		assertJson(rename, "{\"data\":" + CONFIRMED_ITEM + "}");
		verify(this.userCardService).update(MY_CARDS, "uc-1001", new UserCardChange("청춘대로 톡톡", true));
		verify(this.userCardService).update(MY_CARDS, "uc-1002", new UserCardChange("청춘대로 톡톡", null));
	}

	@Test
	void updateCardRejectsInvalidRequests() throws Exception {
		String path = CARDS + "/uc-1";
		MvcResult empty = perform(json(patch(path), "{}").header(HEADER, "MY_CARDS"));
		assertJson(empty, """
				{"code":"INVALID_REQUEST","message":"요청 값을 확인해 주세요.",
				 "errors":[{"field":null,"message":"변경할 값이 없습니다."}]}
				""", 400);
		assertFieldError(patch(path), "{\"displayName\":\"" + "가".repeat(31) + "\"}", "displayName",
				"앞뒤 공백을 빼고 1~30자여야 합니다.");
		assertFieldError(patch(path), "{\"displayName\":\" \"}", "displayName", "앞뒤 공백을 빼고 1~30자여야 합니다.");
		for (String body : List.of("{\"excludedFromRecommendation\":\"true\"}",
				"{\"displayName\":\"카드\",\"mapping\":{\"status\":\"UNMAPPED\"}}", "{\"cardNumber\":\"1234\"}")) {
			assertJson(perform(json(patch(path), body).header(HEADER, "MY_CARDS")), INVALID_REQUEST_EMPTY, 400);
		}
		verifyNoInteractions(this.userCardService);
	}

	@Test
	void updateCardMapsServiceFailures() throws Exception {
		given(this.userCardService.update(eq(MY_CARDS), anyString(), any())).willThrow(new UserCardNotFoundException());
		assertJson(perform(json(patch(CARDS + "/uc-9"), "{\"excludedFromRecommendation\":true}").header(HEADER,
				"MY_CARDS")), USER_CARD_NOT_FOUND, 404);

		given(this.userCardService.update(eq(PORTFOLIO), anyString(), any()))
			.willThrow(new PortfolioReadOnlyException());
		assertJson(perform(json(patch(CARDS + "/uc-1"), "{\"displayName\":\"카드\"}").header(HEADER,
				"VIRTUAL_PORTFOLIO")), READ_ONLY, 409);
	}

	@Test
	void excludingFixedRepresentativeCardIs409() throws Exception {
		given(this.userCardService.update(eq(MY_CARDS), eq("uc-1001"), any()))
			.willThrow(new RepresentativeCardInUseException());

		assertJson(perform(json(patch(CARDS + "/uc-1001"), "{\"excludedFromRecommendation\":true}").header(HEADER,
				"MY_CARDS")), REPRESENTATIVE_IN_USE, 409);
	}

	// DELETE /api/v1/me/cards/{userCardId}

	@Test
	void releaseCardReturns204WithoutBody() throws Exception {
		MvcResult result = perform(delete(CARDS + "/uc-1001").header(HEADER, "MY_CARDS"));

		assertThat(result.getResponse().getStatus()).isEqualTo(204);
		assertThat(result.getResponse().getContentAsString()).isEmpty();
		verify(this.userCardService).release(MY_CARDS, "uc-1001");
	}

	@Test
	void releaseCardMapsServiceFailures() throws Exception {
		willThrow(new UserCardNotFoundException()).given(this.userCardService).release(MY_CARDS, "uc-1");
		assertJson(perform(delete(CARDS + "/uc-1").header(HEADER, "MY_CARDS")), USER_CARD_NOT_FOUND, 404);

		willThrow(new PortfolioReadOnlyException()).given(this.userCardService).release(PORTFOLIO, "uc-1");
		assertJson(perform(delete(CARDS + "/uc-1").header(HEADER, "VIRTUAL_PORTFOLIO")), READ_ONLY, 409);
	}

	@Test
	void releasingFixedRepresentativeCardIs409() throws Exception {
		willThrow(new RepresentativeCardInUseException()).given(this.userCardService).release(MY_CARDS, "uc-1001");

		assertJson(perform(delete(CARDS + "/uc-1001").header(HEADER, "MY_CARDS")), REPRESENTATIVE_IN_USE, 409);
	}

	private void assertFieldError(MockHttpServletRequestBuilder request, String body, String field, String message)
			throws Exception {
		MvcResult result = perform(json(request, body).header(HEADER, "MY_CARDS"));
		assertJson(result, """
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

	private static void assertJson(MvcResult result, String expected) throws Exception {
		JsonAssert.comparator(JsonCompareMode.STRICT)
			.assertIsMatch(expected, result.getResponse().getContentAsString(StandardCharsets.UTF_8));
	}

	private static void assertJson(MvcResult result, String expected, int status) throws Exception {
		assertThat(result.getResponse().getStatus()).isEqualTo(status);
		assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
		assertJson(result, expected);
	}

}
