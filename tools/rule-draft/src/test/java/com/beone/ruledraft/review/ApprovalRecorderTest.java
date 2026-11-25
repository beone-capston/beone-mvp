package com.beone.ruledraft.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;

/** 승인 기록 검사. 합성 비교 결과만 쓴다. */
class ApprovalRecorderTest {

	private final ObjectMapper mapper = new ObjectMapper();

	private JsonNode compare(String rows) throws Exception {
		return mapper.readTree(("{'cardId': '99999', 'draftSha256': 'd', 'manual': {'fileName': 'm.json', 'sha256': 'm'},"
				+ " 'summary': {}, 'rows': [" + rows + "]}").replace('\'', '"'));
	}

	private static final String OK_ROWS = "{'path': '/a', 'status': 'MISMATCH', 'comparison': 'MISMATCH', 'draft': 1, 'manual': 2},"
			+ "{'path': '/b', 'status': 'MISSING_IN_DRAFT', 'comparison': 'MISSING_IN_DRAFT', 'manual': 'x'},"
			+ "{'path': '/n', 'status': 'MATCH', 'comparison': 'MATCH', 'draft': null, 'manual': null}";

	@Test
	void 올바른_수정_항목은_통과하고_기록에_승인자_시각_전후_사유를_남긴다() throws Exception {
		JsonNode c = compare(OK_ROWS);
		List<ReviewEdit> edits = List.of(new ReviewEdit("/a", IntNode.valueOf(1), IntNode.valueOf(2), "원문 2쪽 표"),
				new ReviewEdit("/b", null, TextNode.valueOf("x"), "초안 누락"));

		assertEquals(List.of(), ApprovalRecorder.problems(c, "검토자A", edits));
		JsonNode record = ApprovalRecorder.record(mapper, c, "99999.compare.json", "s", "검토자A",
				Instant.parse("2026-11-25T01:02:03Z"), edits);
		assertEquals("검토자A", record.path("reviewer").asText());
		assertEquals("2026-11-25T01:02:03Z", record.path("approvedAt").asText());
		assertEquals("x", record.at("/edits/1/after").asText());
		assertTrue(record.at("/edits/1").has("before") && record.at("/edits/1/before").isNull());
		assertEquals("원문 2쪽 표", record.at("/edits/0/reason").asText());
		assertTrue(record.path("note").asText().contains("별도 커밋"));
	}

	@Test
	void 근거_없음_항목이_남으면_거부한다() throws Exception {
		JsonNode c = compare(OK_ROWS + ",{'path': '/z', 'status': 'NO_EVIDENCE', 'comparison': 'MATCH', 'draft': 1, 'manual': 1}");

		List<String> problems = ApprovalRecorder.problems(c, "검토자A", List.of());

		assertEquals(List.of("근거 없음: /z"), problems);
	}

	@Test
	void 형식이_틀린_수정_항목을_모두_알린다() throws Exception {
		JsonNode c = compare(OK_ROWS);
		List<ReviewEdit> edits = List.of(
				new ReviewEdit("/a", IntNode.valueOf(9), IntNode.valueOf(2), "전 값 틀림"),
				new ReviewEdit("/a", IntNode.valueOf(1), IntNode.valueOf(1), "중복이고 전후 같음"),
				new ReviewEdit("/b", null, TextNode.valueOf("x"), " "),
				new ReviewEdit("/nope", null, IntNode.valueOf(1), "없는 경로"),
				new ReviewEdit("a", null, null, "상대 경로"),
				new ReviewEdit("/n", null, IntNode.valueOf(1), "null 값과 없음은 다르다"));

		List<String> problems = ApprovalRecorder.problems(c, " ", edits);

		assertTrue(problems.contains("승인자가 비어 있다"));
		assertTrue(problems.contains("수정 전 값이 초안 값과 다르다: /a"));
		assertTrue(problems.contains("같은 경로를 두 번 고쳤다: /a"));
		assertTrue(problems.contains("수정 전후 값이 같다: /a"));
		assertTrue(problems.contains("수정 사유가 없다: /b"));
		assertTrue(problems.contains("비교 결과에 없는 경로: /nope"));
		assertTrue(problems.contains("수정 경로는 /로 시작해야 한다: a"));
		assertTrue(problems.contains("수정 전 값이 초안 값과 다르다: /n"), problems.toString());
		assertEquals(List.of(), ApprovalRecorder.problems(c, "검토자A",
				List.of(new ReviewEdit("/n", NullNode.getInstance(), IntNode.valueOf(1), "null을 값으로"))));
	}

}
