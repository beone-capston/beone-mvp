package com.beone.api.codef;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.beone.api.codef.CodefApproval.CancelStatus;
import com.beone.api.codef.CodefApproval.DomesticForeign;
import com.beone.api.codef.CodefApproval.PaymentType;
import com.beone.api.codef.CodefCardPerformance.Benefit;
import com.beone.api.codef.CodefCardPerformance.PerformanceMet;
import com.beone.api.codef.CodefCardPerformance.PerformanceStatus;
import com.beone.api.codef.CodefHeldCard.Dormancy;

/**
 * 합성 응답으로 CODEF 카드 응답 해석을 확인한다. 모든 값은 가상이며 실제 응답·카드·계정이 아니다. 기대값은 CODEF 공식 문서 사본의 규칙
 * ("단건의 경우 객체형, 다건의 경우 리스트형", "옵션 및 미제공시 빈칸", 코드 값 목록, 공식 예시 구조)에서 먼저 정했다.
 */
class CodefCardResponseParserTest {

	/** 응답 본문 하나. {@code data}에는 JSON 조각을 그대로 넣는다. */
	private static String body(String code, String data) {
		return """
				{"result":{"code":"%s","message":"synthetic message","extraMessage":"","transactionId":"SYN-TX-1"},"data":%s}
				""".formatted(code, data);
	}

	private static final String APPROVAL = """
			{"resUsedDate":"20261015","resUsedTime":"123000","resCardName":"SYN CARD","resMemberStoreName":"SYN CAFE",
			"resMemberStoreType":"SYN TYPE","resUsedAmount":"10000","resKRWAmt":"10000","resAccountCurrency":"KRW",
			"resPaymentType":"1","resHomeForeignType":"1","resCancelYN":"0","resCancelAmount":"","resApprovalNo":"SYN-APPROVAL-1"}
			""";

	private static CodefApproval approval() {
		return new CodefApproval(Optional.of("20261015"), Optional.of("123000"), Optional.of("SYN CARD"),
				Optional.of("SYN CAFE"), Optional.of("SYN TYPE"), new CodefAmount.Parsed(10_000),
				new CodefAmount.Parsed(10_000), Optional.of("KRW"), PaymentType.LUMP_SUM, DomesticForeign.DOMESTIC,
				CancelStatus.NORMAL, new CodefAmount.Absent(), Optional.of("SYN-APPROVAL-1"));
	}

	private static CodefList.Items<CodefApproval> items(CodefResponse<CodefApproval> response) {
		assertThat(response.data()).isInstanceOf(CodefList.Items.class);
		return (CodefList.Items<CodefApproval>) response.data();
	}

	@Nested
	class Envelope {

		@Test
		void keepsCodeAndTransactionIdIndependentOfData() {
			CodefResponse<CodefApproval> response = CodefCardResponseParser.parseApprovals(body("CF-12202", APPROVAL));

			assertThat(response.result()).isEqualTo(new CodefResult(Optional.of("CF-12202"), Optional.of("SYN-TX-1"),
					CodefResultCategory.COMMUNICATION_FAILURE));
			// 결과 코드가 오류여도 data 해석은 따로 남는다.
			assertThat(items(response).items()).containsExactly(approval());
		}

		@Test
		void missingResultIsUnclassified() {
			CodefResponse<CodefApproval> response = CodefCardResponseParser.parseApprovals("{\"data\":[]}");

			assertThat(response.result()).isEqualTo(
					new CodefResult(Optional.empty(), Optional.empty(), CodefResultCategory.UNCLASSIFIED));
			assertThat(items(response).items()).isEmpty();
		}

		@Test
		void nonTextCodeIsMissing() {
			CodefResponse<CodefApproval> response = CodefCardResponseParser
				.parseApprovals("{\"result\":{\"code\":12202},\"data\":[]}");

			assertThat(response.result().code()).isEmpty();
			assertThat(response.result().category()).isEqualTo(CodefResultCategory.UNCLASSIFIED);
		}

		@ParameterizedTest
		@ValueSource(strings = { "", "not json", "[]", "\"text\"", "{\"result\":" })
		void unreadableBodyIsMalformedWithoutException(String body) {
			CodefResponse<CodefApproval> response = CodefCardResponseParser.parseApprovals(body);

			assertThat(response.result().category()).isEqualTo(CodefResultCategory.UNCLASSIFIED);
			assertThat(response.data()).isInstanceOf(CodefList.Malformed.class);
		}

		@Test
		void rejectsNullBody() {
			assertThatThrownBy(() -> CodefCardResponseParser.parseApprovals(null)).isInstanceOf(NullPointerException.class);
		}

	}

	@Nested
	class AdditionalAuthentication {

		private static final String TWO_WAY = """
				{"continue2Way":%s,"method":"SMS","jobIndex":0,"threadIndex":0,"jti":"SYN-JTI-SECRET",
				"twoWayTimestamp":1,"extraInfo":{"reqSMSAuthNo":""}}
				""";

		@Test
		void continue2WayTrueIsAdditionalAuthentication() {
			CodefResponse<CodefApproval> response = CodefCardResponseParser
				.parseApprovals(body("CF-03002", TWO_WAY.formatted("true")));

			assertThat(response.result().category()).isEqualTo(CodefResultCategory.ADDITIONAL_AUTHENTICATION_REQUIRED);
		}

		@ParameterizedTest
		@ValueSource(strings = { "false", "\"true\"", "null" })
		void continue2WayOtherThanBooleanTrueIsUnclassified(String value) {
			CodefResponse<CodefApproval> response = CodefCardResponseParser
				.parseApprovals(body("CF-03002", TWO_WAY.formatted(value)));

			assertThat(response.result().category()).isEqualTo(CodefResultCategory.UNCLASSIFIED);
		}

		@Test
		void continue2WayMissingIsUnclassified() {
			CodefResponse<CodefApproval> response = CodefCardResponseParser.parseApprovals(body("CF-03002", "{}"));

			assertThat(response.result().category()).isEqualTo(CodefResultCategory.UNCLASSIFIED);
		}

		@Test
		void continue2WayOutsideCf03002DoesNotChangeTheCategory() {
			CodefResponse<CodefApproval> response = CodefCardResponseParser
				.parseApprovals(body("CF-12202", TWO_WAY.formatted("true")));

			assertThat(response.result().category()).isEqualTo(CodefResultCategory.COMMUNICATION_FAILURE);
		}

	}

	@Nested
	class DataShape {

		@Test
		void objectIsOneItem() {
			CodefList.Items<CodefApproval> items = items(CodefCardResponseParser.parseApprovals(body("CF-X", APPROVAL)));

			assertThat(items.shape()).isEqualTo(CodefList.Shape.SINGLE_OBJECT);
			assertThat(items.items()).containsExactly(approval());
		}

		@Test
		void arrayIsManyItems() {
			CodefList.Items<CodefApproval> items = items(
					CodefCardResponseParser.parseApprovals(body("CF-X", "[" + APPROVAL + "," + APPROVAL + "]")));

			assertThat(items.shape()).isEqualTo(CodefList.Shape.ARRAY);
			assertThat(items.items()).containsExactly(approval(), approval());
		}

		@Test
		void emptyArrayIsZeroItems() {
			CodefList.Items<CodefApproval> items = items(CodefCardResponseParser.parseApprovals(body("CF-X", "[]")));

			assertThat(items.shape()).isEqualTo(CodefList.Shape.ARRAY);
			assertThat(items.items()).isEmpty();
		}

		@Test
		void missingDataIsNotZeroItems() {
			CodefResponse<CodefApproval> response = CodefCardResponseParser
				.parseApprovals("{\"result\":{\"code\":\"CF-X\"}}");

			assertThat(response.data()).isEqualTo(new CodefList.Missing<>());
		}

		@ParameterizedTest
		@ValueSource(strings = { "null", "\"\"", "\"text\"", "1", "true", "[1]", "[{}, \"text\"]", "[[]]" })
		void otherShapesAreMalformed(String data) {
			CodefResponse<CodefApproval> response = CodefCardResponseParser.parseApprovals(body("CF-X", data));

			assertThat(response.data()).isInstanceOf(CodefList.Malformed.class);
		}

	}

	@Nested
	class Values {

		@Test
		void blankAndMissingFieldsStayUnknown() {
			CodefList.Items<CodefApproval> items = items(CodefCardResponseParser.parseApprovals(body("CF-X", """
					{"resUsedDate":"","resUsedTime":"  ","resUsedAmount":"","resPaymentType":"","resCancelYN":null,
					"resCardName":1}
					""")));

			assertThat(items.items()).containsExactly(new CodefApproval(Optional.empty(), Optional.empty(), Optional.empty(),
					Optional.empty(), Optional.empty(), new CodefAmount.Absent(), new CodefAmount.Absent(), Optional.empty(),
					PaymentType.UNKNOWN, DomesticForeign.UNKNOWN, CancelStatus.UNKNOWN, new CodefAmount.Absent(),
					Optional.empty()));
		}

		@ParameterizedTest
		@ValueSource(strings = { "1,000", "10.5", "abc", "+100", "--1", "99999999999999999999", "1 000" })
		void nonIntegerAmountsAreUnparsed(String amount) {
			CodefApproval approval = items(CodefCardResponseParser
				.parseApprovals(body("CF-X", "{\"resUsedAmount\":\"" + amount + "\"}"))).items().get(0);

			assertThat(approval.usedAmount()).isEqualTo(new CodefAmount.Unparsed());
		}

		@Test
		void negativeAmountIsParsedWithoutCancellationMeaning() {
			CodefApproval approval = items(CodefCardResponseParser
				.parseApprovals(body("CF-X", "{\"resUsedAmount\":\"-5000\",\"resCancelYN\":\"0\"}"))).items().get(0);

			assertThat(approval.usedAmount()).isEqualTo(new CodefAmount.Parsed(-5_000));
			assertThat(approval.cancelStatus()).isEqualTo(CancelStatus.NORMAL);
		}

		@Test
		void zeroAmountIsAConfirmedZeroOnlyWhenWritten() {
			CodefApproval approval = items(
					CodefCardResponseParser.parseApprovals(body("CF-X", "{\"resUsedAmount\":\"0\"}"))).items().get(0);

			assertThat(approval.usedAmount()).isEqualTo(new CodefAmount.Parsed(0));
			assertThat(approval.cancelAmount()).isEqualTo(new CodefAmount.Absent());
		}

		@Test
		void documentedCodesAreMapped() {
			assertThat(codes("2", "2", "1")).containsExactly(PaymentType.INSTALLMENT, DomesticForeign.FOREIGN,
					CancelStatus.CANCELED);
			assertThat(codes("3", "1", "2")).containsExactly(PaymentType.OTHER, DomesticForeign.DOMESTIC,
					CancelStatus.PARTIALLY_CANCELED);
			assertThat(codes("1", "2", "3")).containsExactly(PaymentType.LUMP_SUM, DomesticForeign.FOREIGN,
					CancelStatus.REJECTED);
		}

		@Test
		void codesOutsideTheDocumentedListAreUnknown() {
			assertThat(codes("4", "3", "4")).containsExactly(PaymentType.UNKNOWN, DomesticForeign.UNKNOWN,
					CancelStatus.UNKNOWN);
			assertThat(codes("01", " 1", "Y")).containsExactly(PaymentType.UNKNOWN, DomesticForeign.UNKNOWN,
					CancelStatus.UNKNOWN);
		}

		private List<Object> codes(String paymentType, String domesticForeign, String cancel) {
			CodefApproval approval = items(CodefCardResponseParser.parseApprovals(body("CF-X", """
					{"resPaymentType":"%s","resHomeForeignType":"%s","resCancelYN":"%s"}
					""".formatted(paymentType, domesticForeign, cancel)))).items().get(0);
			return List.of(approval.paymentType(), approval.domesticForeign(), approval.cancelStatus());
		}

	}

	@Nested
	class UnclassifiedOfficialCodes {

		@ParameterizedTest
		@ValueSource(strings = { "CF-12401", "CF-12834" })
		void classificationStaysIndependentOfData(String code) {
			// 공식 목록 270·315행 코드는 미분류이며(#47), data 해석은 결과 코드와 관계없이 같은 규칙을 따른다.
			CodefResponse<CodefApproval> single = CodefCardResponseParser.parseApprovals(body(code, APPROVAL));
			CodefResponse<CodefApproval> empty = CodefCardResponseParser.parseApprovals(body(code, "[]"));
			CodefResponse<CodefApproval> blank = CodefCardResponseParser
				.parseApprovals(body(code, "{\"resUsedAmount\":\"\",\"resCancelAmount\":\"\"}"));

			assertThat(single.result()).isEqualTo(new CodefResult(Optional.of(code), Optional.of("SYN-TX-1"),
					CodefResultCategory.UNCLASSIFIED));
			assertThat(single.data()).isEqualTo(new CodefList.Items<>(CodefList.Shape.SINGLE_OBJECT, List.of(approval())));
			assertThat(empty.data()).isEqualTo(new CodefList.Items<CodefApproval>(CodefList.Shape.ARRAY, List.of()));
			CodefApproval blankItem = items(blank).items().get(0);
			assertThat(blankItem.usedAmount()).isEqualTo(new CodefAmount.Absent());
			assertThat(blankItem.cancelAmount()).isEqualTo(new CodefAmount.Absent());
			assertThat(CodefCardResponseParser.parseApprovals(body(code, "null")).data())
				.isInstanceOf(CodefList.Malformed.class);
		}

	}

	@Nested
	class SuccessCode {

		private static final CodefResult SUCCESS = new CodefResult(Optional.of("CF-00000"), Optional.of("SYN-TX-1"),
				CodefResultCategory.SUCCESS);

		@ParameterizedTest
		@ValueSource(strings = { "null", "1", "\"text\"", "true", "\"\"", "[1]", "[{}, \"text\"]" })
		void successDoesNotRepairMalformedData(String data) {
			CodefResponse<CodefApproval> response = CodefCardResponseParser.parseApprovals(body("CF-00000", data));

			assertThat(response.result()).isEqualTo(SUCCESS);
			assertThat(response.data()).isInstanceOf(CodefList.Malformed.class);
		}

		@Test
		void successWithoutDataIsStillMissing() {
			CodefResponse<CodefApproval> response = CodefCardResponseParser
				.parseApprovals("{\"result\":{\"code\":\"CF-00000\",\"transactionId\":\"SYN-TX-1\"}}");

			assertThat(response.result()).isEqualTo(SUCCESS);
			assertThat(response.data()).isEqualTo(new CodefList.Missing<CodefApproval>());
		}

		@Test
		void successWithEmptyArrayIsZeroItems() {
			CodefResponse<CodefApproval> response = CodefCardResponseParser.parseApprovals(body("CF-00000", "[]"));

			assertThat(response.result()).isEqualTo(SUCCESS);
			assertThat(response.data()).isEqualTo(new CodefList.Items<CodefApproval>(CodefList.Shape.ARRAY, List.of()));
		}

		@Test
		void successDoesNotFillUnknownValues() {
			CodefApproval approval = items(CodefCardResponseParser.parseApprovals(body("CF-00000", """
					{"resUsedDate":"","resUsedAmount":"","resKRWAmt":"1,000","resPaymentType":"9","resCancelYN":"Y"}
					"""))).items().get(0);

			assertThat(approval.usedDate()).isEmpty();
			assertThat(approval.usedAmount()).isEqualTo(new CodefAmount.Absent());
			assertThat(approval.krwAmount()).isEqualTo(new CodefAmount.Unparsed());
			assertThat(approval.cancelAmount()).isEqualTo(new CodefAmount.Absent());
			assertThat(approval.paymentType()).isEqualTo(PaymentType.UNKNOWN);
			assertThat(approval.cancelStatus()).isEqualTo(CancelStatus.UNKNOWN);
		}

		@Test
		void successWithSingleAndManyItemsIsReadNormally() {
			CodefResponse<CodefApproval> single = CodefCardResponseParser.parseApprovals(body("CF-00000", APPROVAL));
			CodefResponse<CodefApproval> many = CodefCardResponseParser
				.parseApprovals(body("CF-00000", "[" + APPROVAL + "," + APPROVAL + "]"));

			assertThat(single.result()).isEqualTo(SUCCESS);
			assertThat(single.data())
				.isEqualTo(new CodefList.Items<>(CodefList.Shape.SINGLE_OBJECT, List.of(approval())));
			assertThat(many.result()).isEqualTo(SUCCESS);
			assertThat(many.data()).isEqualTo(new CodefList.Items<>(CodefList.Shape.ARRAY, List.of(approval(), approval())));
		}

		@ParameterizedTest
		@ValueSource(strings = { "\" CF-00000\"", "\"CF-00000 \"", "\"cf-00000\"", "\"CF-0000\"", "\"CF-000000\"",
				"\"\"", "null", "0" })
		void nearSuccessCodesInTheBodyAreUnclassified(String code) {
			String body = "{\"result\":{\"code\":" + code + ",\"message\":\"성공\"},\"data\":" + APPROVAL + "}";

			CodefResponse<CodefApproval> response = CodefCardResponseParser.parseApprovals(body);

			assertThat(response.result().category()).isEqualTo(CodefResultCategory.UNCLASSIFIED);
			assertThat(items(response).items()).containsExactly(approval());
		}

		@Test
		void successMessageWithoutCodeIsUnclassified() {
			String body = "{\"result\":{\"message\":\"성공\"},\"data\":" + APPROVAL + "}";

			assertThat(CodefCardResponseParser.parseApprovals(body).result())
				.isEqualTo(new CodefResult(Optional.empty(), Optional.empty(), CodefResultCategory.UNCLASSIFIED));
		}

	}

	@Nested
	class HeldCards {

		@Test
		void readsNonSensitiveFields() {
			CodefResponse<CodefHeldCard> response = CodefCardResponseParser.parseHeldCards(body("CF-X", """
					[{"resCardNo":"SYN-SECRET-CARD","resSleepYN":"N","resCardName":"SYN CARD|SYN ISSUER","resCardType":"본인",
					"resTrafficYN":"Y","resImageLink":"https://example.invalid/card.png","resIssueDate":"20260101",
					"resValidPeriod":"SYN-SECRET-VALID","resState":"정상","commSession":"SYN-SECRET-SESSION"},
					{"resSleepYN":"Y"},{"resSleepYN":"y"}]
					"""));

			assertThat(((CodefList.Items<CodefHeldCard>) response.data()).items()).containsExactly(
					new CodefHeldCard(Optional.of("SYN CARD|SYN ISSUER"), Optional.of("본인"), Dormancy.NOT_DORMANT,
							Optional.of("20260101"), Optional.of("정상")),
					new CodefHeldCard(Optional.empty(), Optional.empty(), Dormancy.DORMANT, Optional.empty(),
							Optional.empty()),
					new CodefHeldCard(Optional.empty(), Optional.empty(), Dormancy.UNKNOWN, Optional.empty(),
							Optional.empty()));
		}

	}

	@Nested
	class Performances {

		private static final String PERFORMANCE = """
				{"resCardName":"SYN CARD","resCardNo":"SYN-SECRET-CARD","resCardType":"본인","resCardCompany":"SYN ISSUER",
				"resLnkUrl":"https://example.invalid/benefit","commSession":"SYN-SECRET-SESSION",
				"resCardBenefitList":[
				  {"resType":"할인","resCardBenefitName":"SYN BENEFIT","resBusinessTypes":"SYN BIZ",
				   "resDetailList":[{"resCardBenefitName":"SYN DETAIL","resPerformanceConditions":"SYN CONDITION",
				     "resBusinessTypes":"SYN BIZ"}],
				   "resCardPerformanceList":{"resCurrentUseAmt":"200000","resRequiredPerformanceAmt":"100000",
				     "commStartDate":"20261001","commEndDate":"20261031","resCoverageCriteria":"SYN CRITERIA",
				     "resMeetPerformanceYN":"0","resStandardUseAmt":"300000"}},
				  {"resType":"","resDetailList":[],"resCardPerformanceList":""}
				]}
				""";

		@Test
		void readsNestedListsWithTheSameRule() {
			CodefResponse<CodefCardPerformance> response = CodefCardResponseParser
				.parseCardPerformances(body("CF-X", PERFORMANCE));

			CodefCardPerformance card = ((CodefList.Items<CodefCardPerformance>) response.data()).items().get(0);
			assertThat(card.cardName()).contains("SYN CARD");
			assertThat(card.cardCompany()).contains("SYN ISSUER");
			List<Benefit> benefits = ((CodefList.Items<Benefit>) card.benefits()).items();
			assertThat(benefits).hasSize(2);

			Benefit first = benefits.get(0);
			assertThat(first.details()).isEqualTo(new CodefList.Items<>(CodefList.Shape.ARRAY, List.of(
					new CodefCardPerformance.BenefitDetail(Optional.of("SYN DETAIL"), Optional.of("SYN CONDITION"),
							Optional.of("SYN BIZ")))));
			assertThat(first.performances()).isEqualTo(new CodefList.Items<>(CodefList.Shape.SINGLE_OBJECT,
					List.of(new PerformanceStatus(new CodefAmount.Parsed(200_000), new CodefAmount.Parsed(100_000),
							new CodefAmount.Parsed(300_000), Optional.of("20261001"), Optional.of("20261031"),
							Optional.of("SYN CRITERIA"), PerformanceMet.SHORT))));

			Benefit second = benefits.get(1);
			assertThat(second.type()).isEmpty();
			assertThat(second.details()).isEqualTo(new CodefList.Items<>(CodefList.Shape.ARRAY, List.of()));
			// 중첩 목록의 빈 문자열은 "미제공시 빈칸"으로 보고 0건이 아닌 값 없음으로 둔다.
			assertThat(second.performances()).isEqualTo(new CodefList.Missing<>());
		}

		@Test
		void missingAndMalformedNestedListsAreNotZero() {
			CodefResponse<CodefCardPerformance> response = CodefCardResponseParser.parseCardPerformances(body("CF-X", """
					[{"resCardName":"SYN CARD"},
					 {"resCardBenefitList":[{"resDetailList":null,"resCardPerformanceList":[{"resMeetPerformanceYN":"1"},"x"]}]},
					 {"resCardBenefitList":3}]
					"""));

			List<CodefCardPerformance> cards = ((CodefList.Items<CodefCardPerformance>) response.data()).items();
			assertThat(cards.get(0).benefits()).isEqualTo(new CodefList.Missing<>());
			Benefit benefit = ((CodefList.Items<Benefit>) cards.get(1).benefits()).items().get(0);
			assertThat(benefit.details()).isInstanceOf(CodefList.Malformed.class);
			assertThat(benefit.performances()).isInstanceOf(CodefList.Malformed.class);
			assertThat(cards.get(2).benefits()).isInstanceOf(CodefList.Malformed.class);
		}

		@Test
		void performanceCodesAndAmounts() {
			CodefResponse<CodefCardPerformance> response = CodefCardResponseParser.parseCardPerformances(body("CF-X", """
					{"resCardBenefitList":{"resCardPerformanceList":[{"resMeetPerformanceYN":"1","resCurrentUseAmt":""},
					{"resMeetPerformanceYN":"Y","resCurrentUseAmt":"1,000"}]}}
					"""));

			Benefit benefit = ((CodefList.Items<Benefit>) ((CodefList.Items<CodefCardPerformance>) response.data()).items()
				.get(0)
				.benefits()).items().get(0);
			List<PerformanceStatus> statuses = ((CodefList.Items<PerformanceStatus>) benefit.performances()).items();
			assertThat(statuses).extracting(PerformanceStatus::met).containsExactly(PerformanceMet.MET, PerformanceMet.UNKNOWN);
			assertThat(statuses).extracting(PerformanceStatus::currentUse)
				.containsExactly(new CodefAmount.Absent(), new CodefAmount.Unparsed());
		}

	}

	@Nested
	class SensitiveValues {

		private static final List<String> SECRETS = List.of("SYN-SECRET-CARD", "SYN-SECRET-CARD1", "SYN-SECRET-CORP",
				"SYN-SECRET-TEL", "SYN-SECRET-ADDR", "SYN-SECRET-SESSION", "SYN-SECRET-JTI", "SYN-SECRET-VALID",
				"SYN-SECRET-MESSAGE");

		private static void assertNoSecret(Object value) {
			String text = String.valueOf(value);
			for (String secret : SECRETS) {
				assertThat(text).doesNotContain(secret);
			}
		}

		@Test
		void approvalResultDoesNotCarrySensitiveFields() {
			String body = """
					{"result":{"code":"CF-03002","message":"SYN-SECRET-MESSAGE","extraMessage":"SYN-SECRET-MESSAGE",
					"transactionId":"SYN-TX-1"},
					"data":[{"resCardNo":"SYN-SECRET-CARD","resCardNo1":"SYN-SECRET-CARD1",
					"resMemberStoreCorpNo":"SYN-SECRET-CORP","resMemberStoreTelNo":"SYN-SECRET-TEL",
					"resMemberStoreAddr":"SYN-SECRET-ADDR","commSession":"SYN-SECRET-SESSION","resUsedAmount":"1000"},
					{"continue2Way":true,"jti":"SYN-SECRET-JTI","jobIndex":1,"threadIndex":1,"twoWayTimestamp":1,
					"extraInfo":{"reqSMSAuthNo":"SYN-SECRET-SESSION"}}]}
					""";

			CodefResponse<CodefApproval> response = CodefCardResponseParser.parseApprovals(body);

			assertNoSecret(response);
		}

		@Test
		void heldCardAndPerformanceResultsDoNotCarrySensitiveFields() {
			String held = body("CF-X", "{\"resCardNo\":\"SYN-SECRET-CARD\",\"resValidPeriod\":\"SYN-SECRET-VALID\","
					+ "\"commSession\":\"SYN-SECRET-SESSION\"}");
			String performance = body("CF-X", "{\"resCardNo\":\"SYN-SECRET-CARD\",\"commSession\":\"SYN-SECRET-SESSION\"}");

			assertNoSecret(CodefCardResponseParser.parseHeldCards(held));
			assertNoSecret(CodefCardResponseParser.parseCardPerformances(performance));
		}

		@Test
		void successResultDoesNotCarrySensitiveFields() {
			String body = """
					{"result":{"code":"CF-00000","message":"SYN-SECRET-MESSAGE","extraMessage":"SYN-SECRET-MESSAGE",
					"transactionId":"SYN-TX-1"},
					"data":{"resCardNo":"SYN-SECRET-CARD","resCardNo1":"SYN-SECRET-CARD1",
					"resMemberStoreCorpNo":"SYN-SECRET-CORP","resMemberStoreTelNo":"SYN-SECRET-TEL",
					"resMemberStoreAddr":"SYN-SECRET-ADDR","commSession":"SYN-SECRET-SESSION","resUsedAmount":"1000"}}
					""";

			CodefResponse<CodefApproval> response = CodefCardResponseParser.parseApprovals(body);

			assertThat(response.result().category()).isEqualTo(CodefResultCategory.SUCCESS);
			assertNoSecret(response);
			String held = body("CF-00000", "{\"resCardNo\":\"SYN-SECRET-CARD\",\"resValidPeriod\":\"SYN-SECRET-VALID\"}");
			assertNoSecret(CodefCardResponseParser.parseHeldCards(held));
			assertNoSecret(CodefCardResponseParser
				.parseCardPerformances(body("CF-00000", "{\"resCardNo\":\"SYN-SECRET-CARD\"}")));
		}

		@Test
		void unreadableBodyDoesNotEchoItsContent() {
			String broken = "{\"data\":{\"resCardNo\":\"SYN-SECRET-CARD\",";

			assertNoSecret(CodefCardResponseParser.parseApprovals(broken));
			assertNoSecret(CodefCardResponseParser.parseApprovals("\"SYN-SECRET-SESSION\""));
		}

	}

}
