package com.beone.api.card.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.beone.api.benefit.rule.loading.RuleCatalogConfiguration;
import com.beone.api.card.application.CardProductService;
import com.beone.api.support.WebSliceTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonAssert;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 프로필 없이(공개 프로필 가정) 카드 상품 API의 디스패치·직렬화·실패 응답을 MockMvc로 확인한다.
 *
 * <p>저장소의 실제 규칙 JSON을 읽은 {@code RuleCatalog}와 실제 서비스를 쓰고 시계만 고정한다. 기대 JSON의 필드 구성은
 * docs/api/products.md, 값(규칙 버전·출처·한도)은 backend/src/main/resources/rules의 JSON, 상품 이름·종류는
 * docs/card-benefit-research.md 1절, 판정 규칙은 결정 27 A1에서 옮겼다. 시연 사용자 헤더는 보내지 않는다.
 */
@WebSliceTest(controllers = CardProductController.class, properties = "logging.level.com.beone.api.card.web=DEBUG")
@Import({ RuleCatalogConfiguration.class, CardProductService.class, CardProductApiWebTest.FixedClock.class })
@ExtendWith(OutputCaptureExtension.class)
class CardProductApiWebTest {

	private static final String PRODUCTS = "/api/v1/card-products";

	private static final String INVALID_AS_OF_BODY = """
			{"code":"INVALID_REQUEST","message":"요청 값을 확인해 주세요.",
			 "errors":[{"field":"asOf","message":"형식이 올바르지 않습니다."}]}
			""";

	private static final String NOT_FOUND_BODY = """
			{"code":"CARD_PRODUCT_NOT_FOUND","message":"카드 상품을 찾을 수 없습니다.","errors":[]}
			""";

	@Autowired
	MockMvc mockMvc;

	@Test
	void listWithoutAsOfUsesSeoulTodayAndReturnsContractBody() throws Exception {
		MvcResult result = this.mockMvc.perform(get(PRODUCTS)).andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertJson(result, """
				{"data":{"asOf":"2026-11-17","items":[
				  {"cardProductId":"01664","cardName":"노리 체크카드","issuer":"KB국민카드","cardType":"CHECK",
				   "ruleStatus":"VERIFIED","ruleVersion":"01664-B-20101223-9162-00045-12","officialSources":[
				    {"uri":"https://img2.kbcard.com/obj/card/download/01664__prdctOpmn_20260818.pdf",
				     "locator":"상품설명서 문서번호 B-20101223-9162-00045-12, 전체 3쪽"},
				    {"uri":"https://card.kbcard.com/CRD/DVIEW/HCAMCXPRICAC0076?mainCC=a&cooperationcode=01664",
				     "locator":"KB 상품 페이지, 심의필 260522-01722-HPP(2026.05.22), 2026-11-03 조회. 내용이 바뀔 수 있음"}]},
				  {"cardProductId":"01914","cardName":"첵첵 체크카드","issuer":"KB국민카드","cardType":"CHECK",
				   "ruleStatus":"VERIFIED","ruleVersion":"01914-B-20200608-9162-00529-07","officialSources":[
				    {"uri":"https://img2.kbcard.com/obj/card/download/01914__prdctOpmn_20260818.pdf",
				     "locator":"상품설명서 문서번호 B-20200608-9162-00529-07, 전체 3쪽"},
				    {"uri":"https://card.kbcard.com/CXPRICAC0076.cms?categoryCode=L0086&cooperationcode=01914&mainCC=a",
				     "locator":"KB 상품 페이지(보조 근거), 심의필 260522-01722-HPP(2026.05.22), 2026-11-03 조회. 내용이 바뀔 수 있음"}]},
				  {"cardProductId":"09174","cardName":"청춘대로 톡톡카드","issuer":"KB국민카드","cardType":"CREDIT",
				   "ruleStatus":"VERIFIED","ruleVersion":"09174-A-20170126-9120-00109-07","officialSources":[
				    {"uri":"https://img2.kbcard.com/obj/card/download/09174__prdctOpmn_20260714.pdf",
				     "locator":"문서번호 A-20170126-9120-00109-07, 전체 3쪽"}]},
				  {"cardProductId":"09271","cardName":"올라운드 티타늄카드","issuer":"KB국민카드","cardType":"CREDIT",
				   "ruleStatus":"VERIFIED","ruleVersion":"09271-A-20181030-9120-00489-02","officialSources":[
				    {"uri":"https://img2.kbcard.com/obj/card/download/09271__prdctOpmn_20240417.pdf",
				     "locator":"상품설명서 문서번호 A-20181030-9120-00489-02, 전체 5쪽"},
				    {"uri":"https://img2.kbcard.com/obj/contents/download/Terms_sheet_persnal_MERGE_09271.pdf",
				     "locator":"KB국민 신용카드 설명서(개인), 표기 24.11개정·9104·04101195, 전체 8쪽. 상품 내용은 2~3쪽"}]}
				]}}
				""");
	}

	@Test
	void listBeforeRuleValidityShowsExpiredProductsWithNullVersionAndEmptySources() throws Exception {
		// 2026-08-17은 01664·01914(2026-08-18부터)의 적용 전, 09174(2026-07-14부터)·09271(2024-04-17부터)의 적용 중이다.
		MvcResult result = this.mockMvc.perform(get(PRODUCTS).param("asOf", "2026-08-17")).andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertJson(result, """
				{"data":{"asOf":"2026-08-17","items":[
				  {"cardProductId":"01664","ruleStatus":"RULE_EXPIRED","ruleVersion":null,"officialSources":[]},
				  {"cardProductId":"01914","ruleStatus":"RULE_EXPIRED","ruleVersion":null,"officialSources":[]},
				  {"cardProductId":"09174","ruleStatus":"VERIFIED","ruleVersion":"09174-A-20170126-9120-00109-07"},
				  {"cardProductId":"09271","ruleStatus":"VERIFIED","ruleVersion":"09271-A-20181030-9120-00489-02"}
				]}}
				""", JsonCompareMode.LENIENT);
	}

	@Test
	void detailReturnsRuleSummaryWithUnresolvedTermsAsNull() throws Exception {
		MvcResult result = this.mockMvc.perform(get(PRODUCTS + "/09174").param("asOf", "2026-11-03")).andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertJson(result, """
				{"data":{
				  "cardProductId":"09174","cardName":"청춘대로 톡톡카드","asOf":"2026-11-03","ruleStatus":"VERIFIED",
				  "rule":{"ruleVersion":"09174-A-20170126-9120-00109-07","verificationStatus":"VERIFIED",
				    "validFrom":"2026-07-14","validTo":null,"performanceWindow":"PREVIOUS_CALENDAR_MONTH",
				    "cancellationAttribution":null,"limitDeductionOrder":null},
				  "services":[
				    {"serviceId":"GREAT","name":"GREAT","benefitKind":"BILLING_DISCOUNT",
				     "supportStatus":"SUPPORTED_SINGLE_MONTHLY_LIMIT","limitBucketIds":["GREAT"],"requiresSelection":false},
				    {"serviceId":"ENJOY","name":"ENJOY","benefitKind":"BILLING_DISCOUNT",
				     "supportStatus":"SUPPORTED_SINGLE_MONTHLY_LIMIT","limitBucketIds":["ENJOY"],"requiresSelection":false},
				    {"serviceId":"CHECK","name":"CHECK","benefitKind":"BILLING_DISCOUNT",
				     "supportStatus":"SUPPORTED_SINGLE_MONTHLY_LIMIT","limitBucketIds":["CHECK"],"requiresSelection":false},
				    {"serviceId":"BASIC_TELECOM","name":"BASIC","benefitKind":"BILLING_DISCOUNT",
				     "supportStatus":"SUPPORTED_SINGLE_MONTHLY_LIMIT","limitBucketIds":["BASIC"],"requiresSelection":false},
				    {"serviceId":"BASIC_TRANSIT","name":"BASIC","benefitKind":"BILLING_DISCOUNT",
				     "supportStatus":"SUPPORTED_SINGLE_MONTHLY_LIMIT","limitBucketIds":["BASIC"],"requiresSelection":false}],
				  "limitBuckets":[
				    {"limitBucketId":"GREAT","tiers":[{"minimumPerformanceWon":300000,"monthlyCapWon":10000}],
				     "parentLimitBucketId":null,"period":"CALENDAR_MONTH","sharedByServiceIds":["GREAT"]},
				    {"limitBucketId":"ENJOY","tiers":[{"minimumPerformanceWon":300000,"monthlyCapWon":5000}],
				     "parentLimitBucketId":null,"period":"CALENDAR_MONTH","sharedByServiceIds":["ENJOY"]},
				    {"limitBucketId":"CHECK","tiers":[{"minimumPerformanceWon":300000,"monthlyCapWon":5000}],
				     "parentLimitBucketId":null,"period":"CALENDAR_MONTH","sharedByServiceIds":["CHECK"]},
				    {"limitBucketId":"BASIC","tiers":[{"minimumPerformanceWon":300000,"monthlyCapWon":5000}],
				     "parentLimitBucketId":null,"period":"CALENDAR_MONTH","sharedByServiceIds":["BASIC_TELECOM","BASIC_TRANSIT"]}]
				}}
				""");
	}

	@Test
	void detailShowsSelectionServicesAsUnsupportedAndUnlimitedServiceAsSupported() throws Exception {
		MvcResult result = this.mockMvc.perform(get(PRODUCTS + "/09271").param("asOf", "2026-11-03")).andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertJson(result, """
				{"data":{"cardProductId":"09271","ruleStatus":"VERIFIED",
				  "rule":{"cancellationAttribution":"RECEIPT_MONTH","limitDeductionOrder":null},
				  "services":[
				    {"serviceId":"BASE","benefitKind":"POINT_ACCRUAL","supportStatus":"SUPPORTED","limitBucketIds":[],
				     "requiresSelection":false},
				    {"serviceId":"FUEL","supportStatus":"UNSUPPORTED_SELECTION_RULE","requiresSelection":true},
				    {"serviceId":"STORE","supportStatus":"UNSUPPORTED_SELECTION_RULE","requiresSelection":true}]}}
				""", JsonCompareMode.LENIENT);
	}

	@Test
	void detailShowsParentBucketAndDirectSharingOnly() throws Exception {
		MvcResult result = this.mockMvc.perform(get(PRODUCTS + "/01664").param("asOf", "2026-11-03")).andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertJson(result, """
				{"data":{"rule":{"cancellationAttribution":"RECEIPT_MONTH","limitDeductionOrder":"PURCHASE_ORDER"},
				  "limitBuckets":[
				    {"limitBucketId":"COMMON","parentLimitBucketId":null,
				     "sharedByServiceIds":["CGV","OUTBACK_VIPS","STARBUCKS","THEME_PARK","GS25","KYOBO"],
				     "tiers":[{"minimumPerformanceWon":200000,"monthlyCapWon":10000},
				              {"minimumPerformanceWon":300000,"monthlyCapWon":20000},
				              {"minimumPerformanceWon":500000,"monthlyCapWon":30000},
				              {"minimumPerformanceWon":1000000,"monthlyCapWon":50000}]},
				    {"limitBucketId":"TRANSIT","parentLimitBucketId":"COMMON","sharedByServiceIds":["TRANSIT"]},
				    {"limitBucketId":"TELECOM","parentLimitBucketId":"COMMON","sharedByServiceIds":["TELECOM"]}]}}
				""", JsonCompareMode.LENIENT);
	}

	@Test
	void detailWithoutUsableRuleHasNullRuleAndEmptyLists() throws Exception {
		MvcResult result = this.mockMvc.perform(get(PRODUCTS + "/09174").param("asOf", "2026-07-13")).andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertJson(result, """
				{"data":{"cardProductId":"09174","cardName":"청춘대로 톡톡카드","asOf":"2026-07-13",
				  "ruleStatus":"RULE_EXPIRED","rule":null,"services":[],"limitBuckets":[]}}
				""");
	}

	@Test
	void detailWithoutAsOfUsesSeoulToday() throws Exception {
		MvcResult result = this.mockMvc.perform(get(PRODUCTS + "/09174")).andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertJson(result, "{\"data\":{\"asOf\":\"2026-11-17\",\"ruleStatus\":\"VERIFIED\"}}", JsonCompareMode.LENIENT);
	}

	@ParameterizedTest
	@ValueSource(strings = { "9174", "009174", "09175", "SECRET-PRODUCT-77" })
	void unknownProductIsNotFoundWithoutEchoingId(String id, CapturedOutput output) throws Exception {
		MvcResult result = this.mockMvc.perform(get(PRODUCTS + "/" + id)).andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(404);
		assertJson(result, NOT_FOUND_BODY);
		assertThat(output.getAll()).contains("API error code=CARD_PRODUCT_NOT_FOUND status=404");
		assertThat(output.getAll()).doesNotContain("SECRET-PRODUCT-77");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "2026-11-3", "20261103", "2026/11/03", "2026-02-30", "2026-13-01", "2026-11-03Z",
			"2026-11-03T00:00:00", " 2026-11-03", "+2026-11-03", "SECRET-DATE-55" })
	void malformedAsOfIsInvalidRequestOnBothEndpoints(String asOf, CapturedOutput output) throws Exception {
		for (String path : new String[] { PRODUCTS, PRODUCTS + "/09174" }) {
			MvcResult result = this.mockMvc.perform(get(path).param("asOf", asOf)).andReturn();

			assertThat(result.getResponse().getStatus()).isEqualTo(400);
			assertJson(result, INVALID_AS_OF_BODY);
		}
		assertThat(output.getAll()).contains("API error code=INVALID_REQUEST status=400");
		assertThat(output.getAll()).doesNotContain("SECRET-DATE-55");
	}

	@Test
	void malformedAsOfOnUnknownProductIsInvalidRequestFirst() throws Exception {
		MvcResult result = this.mockMvc.perform(get(PRODUCTS + "/99999").param("asOf", "bad")).andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertJson(result, INVALID_AS_OF_BODY);
	}

	private static void assertJson(MvcResult result, String expected) throws Exception {
		assertJson(result, expected, JsonCompareMode.STRICT);
	}

	private static void assertJson(MvcResult result, String expected, JsonCompareMode mode) throws Exception {
		assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
		JsonAssert.comparator(mode)
			.assertIsMatch(expected, result.getResponse().getContentAsString(StandardCharsets.UTF_8));
	}

	/** 2026-11-16T15:30Z(서울 2026-11-17 00:30)로 고정한 UTC 시계. 서울 날짜로 바꾸는지 확인한다. */
	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClock {

		@Bean
		Clock clock() {
			return Clock.fixed(Instant.parse("2026-11-16T15:30:00Z"), ZoneOffset.UTC);
		}

	}

}
