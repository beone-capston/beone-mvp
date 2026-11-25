package com.beone.ruledraft.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** compare·approve 공용 mapper의 소수 읽기. double로 읽으면 다른 값이 같아 보이는 회귀를 막는다. */
class ReviewJsonTest {

	private final ObjectMapper mapper = ReviewJson.mapper();

	private boolean same(String a, String b) throws Exception {
		JsonNode n = mapper.readTree("{\"a\": " + a + ", \"b\": " + b + "}");
		return RuleComparator.sameValue(n.get("a"), n.get("b"));
	}

	@Test
	void 소수는_double로_뭉개지지_않아_다른_값은_불일치다() throws Exception {
		assertFalse(same("0.10000000000000001", "0.1"));
		assertTrue(mapper.readTree("0.10000000000000001").isBigDecimal());
	}

	@Test
	void 정수와_같은_값의_소수는_일치다() throws Exception {
		assertTrue(same("10000", "10000.0"));
		assertTrue(same("0.1", "0.10"));
	}

	@Test
	void 승인_검사도_정밀한_소수로_수정_전_값을_대조한다() throws Exception {
		JsonNode compare = mapper.readTree("{\"cardId\": \"99999\", \"rows\": [{\"path\": \"/r\", \"status\": \"MISMATCH\","
				+ " \"draft\": 0.1, \"manual\": 0.2}]}");
		JsonNode wrong = mapper.readTree("0.10000000000000001");
		JsonNode right = mapper.readTree("0.10");
		JsonNode after = mapper.readTree("0.2");

		assertEquals(List.of("수정 전 값이 초안 값과 다르다: /r"),
				ApprovalRecorder.problems(compare, "검토자A", List.of(new ReviewEdit("/r", wrong, after, "사유"))));
		assertEquals(List.of(),
				ApprovalRecorder.problems(compare, "검토자A", List.of(new ReviewEdit("/r", right, after, "사유"))));
	}

}
