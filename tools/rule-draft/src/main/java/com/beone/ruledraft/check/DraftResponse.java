package com.beone.ruledraft.check;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 모델 응답 텍스트를 해석한 결과. 응답 형식: {@code {"draft": {...}, "evidence": {"/경로": {"page": N, "quote": "..."}}}}.
 * 앞뒤 설명 문장이나 코드 울타리(```)가 있으면 첫 {@code {}부터 마지막 {@code }}까지만 읽는다.
 *
 * @param draft 규칙 초안(객체)
 * @param evidence 경로별 근거
 */
public record DraftResponse(ObjectNode draft, Map<String, Evidence> evidence) {

	public static DraftResponse parse(String text, ObjectMapper mapper) throws IOException {
		int start = text.indexOf('{');
		int end = text.lastIndexOf('}');
		if (start < 0 || end < start) {
			throw new IOException("응답에 JSON 객체가 없다");
		}
		JsonNode root = mapper.readTree(text.substring(start, end + 1));
		JsonNode draft = root.get("draft");
		if (draft == null || !draft.isObject()) {
			throw new IOException("응답에 draft 객체가 없다");
		}
		Map<String, Evidence> evidence = new LinkedHashMap<>();
		JsonNode ev = root.path("evidence");
		if (!ev.isMissingNode() && !ev.isObject()) {
			throw new IOException("응답의 evidence가 객체가 아니다");
		}
		for (Map.Entry<String, JsonNode> e : ev.properties()) {
			JsonNode v = e.getValue();
			// 정수 JSON 숫자만 쪽 번호로 받는다. 2.7 같은 소수·문자열은 잘라 쓰지 않고 0(없는 쪽)으로 두어 확인 실패가 되게 한다
			JsonNode pageNode = v.path("page");
			int page = pageNode.isIntegralNumber() && pageNode.canConvertToInt() ? pageNode.intValue() : 0;
			evidence.put(e.getKey(), new Evidence(page, v.path("quote").asText("")));
		}
		return new DraftResponse((ObjectNode) draft, evidence);
	}

}
