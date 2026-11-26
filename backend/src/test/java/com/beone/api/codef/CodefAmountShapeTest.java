package com.beone.api.codef;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 금액 원문의 형태 분류(#274). 금액 문자열은 합성 값이다. 형태 키는 고정 어휘만 담고 원문 문자를 담지 않는다.
 */
class CodefAmountShapeTest {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private static CodefAmountShape shapeOf(String itemJson) {
		JsonNode item = MAPPER.readTree(itemJson);
		return CodefAmountShape.of(item, "resUsedAmount");
	}

	private static CodefAmountShape shapeOfText(String amount) {
		return CodefAmountShape.of(MAPPER.createObjectNode().put("resUsedAmount", amount), "resUsedAmount");
	}

	@ParameterizedTest(name = "[{index}] {2}")
	@CsvSource(delimiter = '|', quoteCharacter = '\'', value = {
			// 원문 | 형태 | 설명
			"'12345'|digits|숫자만", "'12,345'|digits+comma|쉼표", "'-500'|digits+minus|음수 부호",
			"'10.5'|digits+dot|소수점", "'₩5000'|digits+currency|원화 기호", "'￦5000'|digits+currency|전각 원화 기호",
			"'5000원'|digits+currency|원", "'KRW 5000'|digits+currency+space|통화 코드와 공백",
			"'krw5000'|digits+currency|소문자 통화 코드", "'$12'|digits+currency|달러 기호",
			"'1 000'|digits+space|안쪽 공백", "'1\u00A0000'|digits+space|NBSP", "'+100'|digits+other|더하기 부호",
			"'１２３'|other|전각 숫자", "'abc'|other|그 밖의 글자", "'(500)'|digits+other|괄호",
			"'−500'|digits+other|유니코드 빼기", "'-1,234.50원'|digits+comma+minus+dot+currency|조합",
			"'   '|space|공백뿐" })
	void classifiesEachCharacterFamily(String amount, String form, String description) {
		assertThat(shapeOfText(amount).form()).as(description).isEqualTo(form);
	}

	@Test
	void emptyMissingNullAndNonStringAreDistinguished() {
		assertThat(shapeOfText("")).isEqualTo(new CodefAmountShape("empty", "0"));
		assertThat(shapeOf("{}")).isEqualTo(new CodefAmountShape("missing", "none"));
		assertThat(shapeOf("{\"resUsedAmount\":null}")).isEqualTo(new CodefAmountShape("null", "none"));
		assertThat(shapeOf("{\"resUsedAmount\":true}")).isEqualTo(new CodefAmountShape("nonString", "none"));
		assertThat(shapeOf("{\"resUsedAmount\":{\"v\":\"1\"}}")).isEqualTo(new CodefAmountShape("nonString", "none"));
		assertThat(shapeOf("{\"resUsedAmount\":[\"1\"]}")).isEqualTo(new CodefAmountShape("nonString", "none"));
	}

	@Test
	void jsonNumbersAreMarkedSeparately() {
		// 문자열이 아닌 숫자는 금액 파서에서 값 없음이 되므로 따로 표시한다
		assertThat(shapeOf("{\"resUsedAmount\":15000}")).isEqualTo(new CodefAmountShape("jsonNumber+digits", "4-6"));
		assertThat(shapeOf("{\"resUsedAmount\":-2.5}"))
			.isEqualTo(new CodefAmountShape("jsonNumber+digits+minus+dot", "4-6"));
	}

	@ParameterizedTest
	@CsvSource({ "'',0", "1,1-3", "123,1-3", "1234,4-6", "123456,4-6", "1234567,7-9", "123456789,7-9",
			"1234567890,10+", "가나다라,4-6" })
	void lengthBucketsCountCodePoints(String amount, String length) {
		assertThat(shapeOfText(amount).length()).isEqualTo(length);
	}

	@Test
	void lengthCountsSupplementaryCharactersOnce() {
		assertThat(shapeOfText("💰".repeat(3))).isEqualTo(new CodefAmountShape("other", "1-3"));
	}

	@Test
	void shapeCarriesNoInputCharacters() {
		CodefAmountShape shape = shapeOfText("SYN-원9,876.5zq");

		assertThat(shape.toString()).doesNotContain("SYN").doesNotContain("9").doesNotContain("876")
			.doesNotContain("zq").doesNotContain("원");
		assertThat(shape).isEqualTo(new CodefAmountShape("digits+comma+minus+dot+currency+other", "10+"));
	}

	@Test
	void onlyFixedVocabularyIsAccepted() {
		assertThatThrownBy(() -> new CodefAmountShape("digits+12,345", "4-6"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageNotContaining("12,345");
		assertThatThrownBy(() -> new CodefAmountShape("digits", "5")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void parsedApprovalCarriesTheShapesOfItsAmountFields() {
		CodefApproval approval = ((CodefList.Items<CodefApproval>) CodefCardResponseParser
			.parseApprovals("""
					{"result":{"code":"CF-00000","transactionId":"SYN-TX"},"data":{"resUsedAmount":"1,000","resCancelAmount":""}}
					""")
			.data()).items().get(0);

		assertThat(approval.usedAmount()).isEqualTo(new CodefAmount.Unparsed());
		assertThat(approval.usedAmountShape()).isEqualTo(new CodefAmountShape("digits+comma", "4-6"));
		assertThat(approval.cancelAmountShape()).isEqualTo(new CodefAmountShape("empty", "0"));
		assertThat(approval.approvalNo()).isEqualTo(Optional.empty());
	}

}
