package com.beone.ruledraft.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.beone.ruledraft.source.SourceDocument;
import com.beone.ruledraft.source.SourcePage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class EvidenceCheckerTest {

	private final ObjectMapper mapper = new ObjectMapper();
	private final SourceDocument source = new SourceDocument("s.txt", "0".repeat(64),
			List.of(new SourcePage(1, "월 할인한도\n1만원"), new SourcePage(2, "영화관 35% 할인")));

	private JsonNode draft() throws Exception {
		return mapper.readTree("""
				{"status":"AI_DRAFT","limitBuckets":[{"monthlyCap":10000,"evidence":"E1"}],"services":[{"rate":3500}],"sources":[{"uri":"u"}]}
				""");
	}

	@Test
	void 조상_경로_근거가_아래_값을_덮고_공백은_무시한다() throws Exception {
		EvidenceReport r = EvidenceChecker.check(draft(),
				Map.of("/limitBuckets", new Evidence(1, "월 할인한도 1만원"), "/services/0/rate", new Evidence(2, "영화관 35%")),
				source);
		assertTrue(r.complete(), r.toString());
	}

	@Test
	void 근거_없는_값은_누락으로_남는다() throws Exception {
		EvidenceReport r = EvidenceChecker.check(draft(), Map.of("/limitBuckets", new Evidence(1, "월 할인한도")), source);
		assertEquals(List.of("/services/0/rate"), r.missing());
		assertFalse(r.missing().contains("/status"));
		assertFalse(r.missing().contains("/sources/0/uri"));
		assertFalse(r.missing().contains("/limitBuckets/0/evidence"));
	}

	@Test
	void 확인할_수_없는_근거는_덮지_못한다() throws Exception {
		EvidenceReport r = EvidenceChecker.check(draft(), Map.of(
				"/limitBuckets", new Evidence(2, "월 할인한도"),
				"/services", new Evidence(9, "영화관"),
				"/nothing", new Evidence(1, "월"),
				"/services/0", new Evidence(2, " ")), source);
		assertEquals(List.of("/limitBuckets: 2쪽에서 인용문을 찾지 못함", "/nothing: 초안에 없는 경로",
				"/services: 원문에 없는 쪽 9", "/services/0: 인용문 없음"), r.unverified());
		assertEquals(List.of("/limitBuckets/0/monthlyCap", "/services/0/rate"), r.missing());
	}


	@Test
	void NBSP만_있는_인용은_확인되지_않고_아래_값도_덮지_못한다() throws Exception {
		EvidenceReport r = EvidenceChecker.check(draft(), Map.of(
				"/limitBuckets", new Evidence(1, "\u00A0\u00A0"),
				"/services", new Evidence(2, " \u00A0 \t\u3000 ")), source);
		assertEquals(List.of("/limitBuckets: 인용문 없음", "/services: 인용문 없음"), r.unverified());
		assertTrue(r.verified().isEmpty());
		assertEquals(List.of("/limitBuckets/0/monthlyCap", "/services/0/rate"), r.missing());
		assertFalse(r.complete());
	}

}
