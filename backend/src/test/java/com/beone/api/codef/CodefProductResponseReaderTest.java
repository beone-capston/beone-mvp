package com.beone.api.codef;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 합성 응답으로 CODEF 상품 응답의 전송 단계 해석을 확인한다. 모든 값은 가상이다. 기대값은 공식 SDK {@code ResponseHandler}(커밋
 * {@code 721f566})의 동작에서 먼저 정했다: 200이면 본문을 UTF-8로 URL 디코딩해 해석하고, 401은 인증 오류, 그 밖의 상태는 서버 오류다.
 */
class CodefProductResponseReaderTest {

	private static final String APPROVAL = """
			{"result":{"code":"CF-00000","transactionId":"SYN-TX-1"},
			"data":{"resUsedDate":"20261015","resCardName":"SYN CARD","resMemberStoreName":"SYN CAFE","resUsedAmount":"10000",
			"resPaymentType":"1","resHomeForeignType":"1","resCancelYN":"0","resApprovalNo":"SYN-APPROVAL-1"}}
			""";

	private static String encoded(String json) {
		return URLEncoder.encode(json, StandardCharsets.UTF_8);
	}

	private static <T> CodefResponse<T> interpreted(CodefProductResponse<T> response) {
		assertThat(response).isInstanceOf(CodefProductResponse.Interpreted.class);
		return ((CodefProductResponse.Interpreted<T>) response).response();
	}

	private static CodefApproval firstApproval(CodefProductResponse<CodefApproval> response) {
		CodefList<CodefApproval> data = interpreted(response).data();
		assertThat(data).isInstanceOf(CodefList.Items.class);
		return ((CodefList.Items<CodefApproval>) data).items().get(0);
	}

	@Nested
	class Decoding {

		@Test
		void encodedSingleItemMatchesTheParser() {
			assertThat(interpreted(CodefProductResponseReader.readApprovals(200, encoded(APPROVAL))))
				.isEqualTo(CodefCardResponseParser.parseApprovals(APPROVAL));
		}

		@Test
		void encodedManyAndEmptyItemsMatchTheParser() {
			String many = "{\"result\":{\"code\":\"CF-00000\"},\"data\":[{\"resCardName\":\"SYN A\"},{\"resCardName\":\"SYN B\"}]}";
			String empty = "{\"result\":{\"code\":\"CF-00000\"},\"data\":[]}";

			assertThat(interpreted(CodefProductResponseReader.readHeldCards(200, encoded(many))))
				.isEqualTo(CodefCardResponseParser.parseHeldCards(many));
			assertThat(interpreted(CodefProductResponseReader.readHeldCards(200, encoded(empty))).data())
				.isEqualTo(new CodefList.Items<CodefHeldCard>(CodefList.Shape.ARRAY, List.of()));
		}

		@Test
		void encodedPerformanceMatchesTheParser() {
			String performance = """
					{"result":{"code":"CF-00000"},"data":{"resCardName":"SYN CARD","resCardBenefitList":[{"resType":"SYN",
					"resCardBenefitDetailList":[],"resCardPerformanceList":[{"resCurrentUseAmount":"1000"}]}]}}
					""";

			assertThat(interpreted(CodefProductResponseReader.readCardPerformances(200, encoded(performance))))
				.isEqualTo(CodefCardResponseParser.parseCardPerformances(performance));
		}

		@Test
		void decodesKoreanPercentSequences() {
			String body = "{\"result\":{\"code\":\"CF-00000\"},\"data\":{\"resMemberStoreName\":\"%EC%B9%B4%ED%8E%98\"}}";

			assertThat(firstApproval(CodefProductResponseReader.readApprovals(200, body)).merchantName())
				.contains("카페");
		}

		@Test
		void plusBecomesSpaceAndPercent2BBecomesPlusLikeTheSdk() {
			String body = "{\"result\":{\"code\":\"CF-00000\"},\"data\":{\"resMemberStoreName\":\"SYN+CAFE\","
					+ "\"resMemberStoreType\":\"SYN%2BTYPE\"}}";

			CodefApproval approval = firstApproval(CodefProductResponseReader.readApprovals(200, body));

			assertThat(approval.merchantName()).contains("SYN CAFE");
			assertThat(approval.merchantType()).contains("SYN+TYPE");
		}

		@Test
		void plusInAnAmountIsDecodedBeforeTheAmountRule() {
			// 디코딩이 먼저라 "+100"은 " 100"이 되고, 금액 규칙은 앞뒤 공백을 제거한 뒤 정수로 읽는다.
			String body = "{\"result\":{\"code\":\"CF-00000\"},\"data\":{\"resUsedAmount\":\"+100\"}}";

			assertThat(firstApproval(CodefProductResponseReader.readApprovals(200, body)).usedAmount())
				.isEqualTo(new CodefAmount.Parsed(100));
		}

		@Test
		void invalidUtf8BytesBecomeReplacementCharacters() {
			// URLDecoder는 UTF-8로 해석할 수 없는 바이트를 대체 문자로 바꾸고 예외를 던지지 않는다(SDK와 같은 디코더).
			String body = "{\"result\":{\"code\":\"CF-00000\"},\"data\":{\"resMemberStoreName\":\"%FF\"}}";

			assertThat(firstApproval(CodefProductResponseReader.readApprovals(200, body)).merchantName())
				.contains("�");
		}

		@ParameterizedTest
		@ValueSource(strings = { "%", "%7", "%G1", "{\"result\":{\"code\":\"CF-00000\"},\"data\":{\"resCardName\":\"%ZZ\"}}" })
		void invalidPercentEncodingIsMalformed(String body) {
			CodefResponse<CodefApproval> response = interpreted(CodefProductResponseReader.readApprovals(200, body));

			assertThat(response).isEqualTo(new CodefResponse<CodefApproval>(
					new CodefResult(Optional.empty(), Optional.empty(), CodefResultCategory.UNCLASSIFIED),
					new CodefList.Malformed<>("response body is not valid URL encoding")));
		}

		@Test
		void successCodeDoesNotRepairMalformedDataAfterDecoding() {
			CodefResponse<CodefApproval> response = interpreted(CodefProductResponseReader.readApprovals(200,
					encoded("{\"result\":{\"code\":\"CF-00000\"},\"data\":null}")));

			assertThat(response.result().category()).isEqualTo(CodefResultCategory.SUCCESS);
			assertThat(response.data()).isInstanceOf(CodefList.Malformed.class);
		}

		@Test
		void decodedBodyThatIsNotJsonIsMalformed() {
			CodefResponse<CodefApproval> response = interpreted(
					CodefProductResponseReader.readApprovals(200, encoded("not json")));

			assertThat(response.result().category()).isEqualTo(CodefResultCategory.UNCLASSIFIED);
			assertThat(response.data()).isEqualTo(new CodefList.Malformed<CodefApproval>("response body is not valid JSON"));
		}

	}

	@Nested
	class HttpStatus {

		@Test
		void unauthorizedDoesNotReadTheBody() {
			assertThat(CodefProductResponseReader.readApprovals(401, encoded(APPROVAL)))
				.isEqualTo(new CodefProductResponse.Unauthorized<CodefApproval>());
			assertThat(CodefProductResponseReader.readApprovals(401, "%"))
				.isEqualTo(new CodefProductResponse.Unauthorized<CodefApproval>());
		}

		@ParameterizedTest
		@ValueSource(ints = { 100, 201, 204, 302, 400, 403, 404, 429, 500, 502, 503, 599 })
		void otherStatusesAreHttpFailuresWithoutReadingTheBody(int status) {
			assertThat(CodefProductResponseReader.readApprovals(status, encoded(APPROVAL)))
				.isEqualTo(new CodefProductResponse.HttpFailure<CodefApproval>(status));
			assertThat(CodefProductResponseReader.readHeldCards(status, "%"))
				.isEqualTo(new CodefProductResponse.HttpFailure<CodefHeldCard>(status));
		}

		@ParameterizedTest
		@ValueSource(ints = { -1, 0, 99, 600, 1000 })
		void rejectsStatusOutsideTheHttpRange(int status) {
			assertThatThrownBy(() -> CodefProductResponseReader.readApprovals(status, "{}"))
				.isInstanceOf(IllegalArgumentException.class);
		}

		@ParameterizedTest
		@ValueSource(ints = { 200, 401 })
		void httpFailureRejectsStatusesWithTheirOwnResult(int status) {
			assertThatThrownBy(() -> new CodefProductResponse.HttpFailure<CodefApproval>(status))
				.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		void rejectsNullBody() {
			assertThatThrownBy(() -> CodefProductResponseReader.readApprovals(200, null))
				.isInstanceOf(NullPointerException.class);
		}

	}

	@Nested
	class SensitiveValues {

		private static final List<String> SECRETS = List.of("SYN-SECRET-CARD", "SYN-SECRET-CORP", "SYN-SECRET-TEL",
				"SYN-SECRET-ADDR", "SYN-SECRET-SESSION", "SYN-SECRET-MESSAGE");

		private static final String SENSITIVE = """
				{"result":{"code":"CF-00000","message":"SYN-SECRET-MESSAGE","extraMessage":"SYN-SECRET-MESSAGE"},
				"data":{"resCardNo":"SYN-SECRET-CARD","resMemberStoreCorpNo":"SYN-SECRET-CORP",
				"resMemberStoreTelNo":"SYN-SECRET-TEL","resMemberStoreAddr":"SYN-SECRET-ADDR","commSession":"SYN-SECRET-SESSION"}}
				""";

		private static void assertNoSecret(Object value) {
			String text = String.valueOf(value);
			for (String secret : SECRETS) {
				assertThat(text).doesNotContain(secret);
			}
		}

		@Test
		void decodedPathDoesNotCarrySensitiveFields() {
			CodefProductResponse<CodefApproval> response = CodefProductResponseReader.readApprovals(200, encoded(SENSITIVE));

			assertThat(interpreted(response).result().category()).isEqualTo(CodefResultCategory.SUCCESS);
			assertNoSecret(response);
		}

		@ParameterizedTest
		@ValueSource(ints = { 401, 500 })
		void nonOkStatusesDoNotCarryTheBody(int status) {
			assertNoSecret(CodefProductResponseReader.readApprovals(status, encoded(SENSITIVE)));
		}

		@Test
		void invalidEncodingDoesNotEchoTheBody() {
			assertNoSecret(CodefProductResponseReader.readApprovals(200, SENSITIVE.replace("SYN-SECRET-CARD", "SYN-SECRET-CARD%ZZ")));
			assertNoSecret(CodefProductResponseReader.readApprovals(200, "%" + encoded("SYN-SECRET-SESSION")));
		}

	}

}
