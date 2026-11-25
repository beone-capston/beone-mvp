package com.beone.ruledraft.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.beone.ruledraft.check.Evidence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 필드별 분류. 합성 JSON만 쓴다. */
class RuleComparatorTest {

	private final ObjectMapper mapper = new ObjectMapper();

	private JsonNode json(String text) throws Exception {
		return mapper.readTree(text.replace('\'', '"'));
	}

	private static Map<String, CompareRow> byPath(List<CompareRow> rows) {
		return rows.stream().collect(Collectors.toMap(CompareRow::path, r -> r));
	}

	private static final Map<String, Evidence> ALL = Map.of("/a", new Evidence(1, "a"), "/b", new Evidence(1, "b"),
			"/c", new Evidence(2, "c"));

	@Test
	void 일치_불일치_양방향_누락을_분류한다() throws Exception {
		JsonNode draft = json("{'a': 1, 'b': {'x': 'p', 'y': 2}, 'c': [1, 2]}");
		JsonNode manual = json("{'a': 1, 'b': {'x': 'q', 'z': true}, 'c': [1]}");

		Map<String, CompareRow> rows = byPath(RuleComparator.compare(draft, manual, ALL));

		assertEquals(CompareStatus.MATCH, rows.get("/a").status());
		assertEquals(CompareStatus.MISMATCH, rows.get("/b/x").status());
		assertEquals(CompareStatus.MISSING_IN_MANUAL, rows.get("/b/y").status());
		assertEquals(CompareStatus.MISSING_IN_DRAFT, rows.get("/b/z").status());
		assertNull(rows.get("/b/z").draft());
		assertEquals(CompareStatus.MATCH, rows.get("/c/0").status());
		assertEquals(CompareStatus.MISSING_IN_MANUAL, rows.get("/c/1").status(), "배열은 번호끼리 비교한다");
		assertEquals("/b", rows.get("/b/x").evidencePath(), "가장 가까운 조상 근거를 붙인다");
	}

	@Test
	void 숫자는_표기와_상관없이_값으로_비교하고_종류가_다르면_불일치() throws Exception {
		Map<String, CompareRow> rows = byPath(RuleComparator.compare(json("{'a': 10000, 'b': '1', 'c': null}"),
				json("{'a': 10000.0, 'b': 1, 'c': 0}"), ALL));

		assertEquals(CompareStatus.MATCH, rows.get("/a").status());
		assertEquals(CompareStatus.MISMATCH, rows.get("/b").status());
		assertEquals(CompareStatus.MISMATCH, rows.get("/c").status(), "null과 0은 다르다");
	}

	@Test
	void 근거가_없으면_값_비교와_상관없이_근거_없음이고_원래_분류를_남긴다() throws Exception {
		Map<String, CompareRow> rows = byPath(RuleComparator.compare(json("{'a': 1, 'd': 5, 'e': 6}"),
				json("{'a': 1, 'd': 5, 'e': 7}"), ALL));

		assertEquals(CompareStatus.NO_EVIDENCE, rows.get("/d").status());
		assertEquals(CompareStatus.MATCH, rows.get("/d").comparison(), "값이 같아도 근거 없음");
		assertEquals(CompareStatus.NO_EVIDENCE, rows.get("/e").status());
		assertEquals(CompareStatus.MISMATCH, rows.get("/e").comparison());
	}

	@Test
	void 수동에만_있는_값과_관리_필드는_근거_없음이_아니다() throws Exception {
		Map<String, CompareRow> rows = byPath(RuleComparator.compare(
				json("{'evaluationOnly': true, 'sources': [{'uri': 'u'}], 'x': {'evidence': 'E1'}}"),
				json("{'evaluationOnly': true, 'only': 3}"), Map.of()));

		assertEquals(CompareStatus.MATCH, rows.get("/evaluationOnly").status());
		assertEquals(CompareStatus.MISSING_IN_MANUAL, rows.get("/sources/0/uri").status());
		assertEquals(CompareStatus.MISSING_IN_MANUAL, rows.get("/x/evidence").status());
		assertEquals(CompareStatus.MISSING_IN_DRAFT, rows.get("/only").status(), "초안 값이 없으니 근거 대상이 아니다");
	}

	@Test
	void status는_비교하지_않는다() throws Exception {
		List<CompareRow> rows = RuleComparator.compare(json("{'status': 'AI_DRAFT', 'a': 1}"),
				json("{'status': 'VERIFIED', 'a': 1}"), ALL);

		assertFalse(rows.stream().anyMatch(r -> r.path().equals("/status")));
		assertTrue(rows.stream().allMatch(r -> r.status() == CompareStatus.MATCH));
	}

	@Test
	void 표_칸은_세로선과_줄바꿈을_바꾸고_길면_자른다() {
		assertEquals("a\\|b c", CompareReport.cell("a|b\nc"));
		String longText = "가".repeat(CompareReport.CELL_LIMIT + 5);
		assertEquals(CompareReport.CELL_LIMIT + 1, CompareReport.cell(longText).codePointCount(0,
				CompareReport.cell(longText).length()));
	}

}
