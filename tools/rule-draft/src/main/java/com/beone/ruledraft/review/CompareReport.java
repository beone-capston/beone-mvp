package com.beone.ruledraft.review;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 비교 결과와 입력 식별 정보. JSON과 Markdown 표로 내보낸다.
 *
 * @param cardId 카드 ID
 * @param draftSha256 비교한 초안 파일 내용의 sha256(근거 파일의 draftSha256과 같음을 확인한 값)
 * @param manualFile 수동 규칙 파일 이름
 * @param manualSha256 수동 규칙 파일 내용의 sha256
 * @param rows 경로별 비교 결과
 */
public record CompareReport(String cardId, String draftSha256, String manualFile, String manualSha256,
		List<CompareRow> rows) {

	/** Markdown 표에서 값·인용을 자르는 길이. JSON에는 전체를 남긴다. */
	static final int CELL_LIMIT = 80;

	public Map<CompareStatus, Integer> summary() {
		Map<CompareStatus, Integer> counts = new EnumMap<>(CompareStatus.class);
		for (CompareStatus s : CompareStatus.values()) {
			counts.put(s, 0);
		}
		rows.forEach(r -> counts.merge(r.status(), 1, Integer::sum));
		return counts;
	}

	public List<CompareRow> noEvidence() {
		return rows.stream().filter(r -> r.status() == CompareStatus.NO_EVIDENCE).toList();
	}

	public ObjectNode toJson(ObjectMapper mapper) {
		ObjectNode root = mapper.createObjectNode();
		root.put("cardId", cardId);
		root.put("draftSha256", draftSha256);
		ObjectNode manual = root.putObject("manual");
		manual.put("fileName", manualFile);
		manual.put("sha256", manualSha256);
		ObjectNode sum = root.putObject("summary");
		summary().forEach((s, n) -> sum.put(s.name(), n));
		ArrayNode list = root.putArray("rows");
		for (CompareRow r : rows) {
			ObjectNode row = list.addObject();
			row.put("path", r.path());
			row.put("status", r.status().name());
			row.put("comparison", r.comparison().name());
			putValue(row, "draft", r.draft());
			putValue(row, "manual", r.manual());
			if (r.evidence() != null) {
				ObjectNode ev = row.putObject("evidence");
				ev.put("path", r.evidencePath());
				ev.put("page", r.evidence().page());
				ev.put("quote", r.evidence().quote());
			}
		}
		return root;
	}

	/** 경로가 없으면 키를 생략한다. JSON null 값은 null로 남겨 "없음"과 구분한다. */
	private static void putValue(ObjectNode row, String key, JsonNode value) {
		if (value != null) {
			row.set(key, value.deepCopy());
		}
	}

	public String toMarkdown() {
		StringBuilder sb = new StringBuilder();
		sb.append("# 규칙 초안 비교: ").append(cardId).append("\n\n");
		sb.append("- 초안 sha256: `").append(draftSha256).append("`\n");
		sb.append("- 수동 규칙: `").append(manualFile).append("` (sha256 `").append(manualSha256).append("`)\n");
		sb.append("- `/status`는 비교하지 않는다(초안은 항상 AI_DRAFT).\n\n");
		sb.append("| 분류 | 건수 |\n|---|---|\n");
		summary().forEach((s, n) -> sb.append("| ").append(s.label()).append(" | ").append(n).append(" |\n"));
		sb.append("\n| 분류 | 경로 | 초안 | 수동 | 근거 |\n|---|---|---|---|---|\n");
		for (CompareRow r : rows) {
			String label = r.status().label();
			if (r.status() == CompareStatus.NO_EVIDENCE) {
				label += "(" + r.comparison().label() + ")";
			}
			String ev = r.evidence() == null ? "" : r.evidence().page() + "쪽: " + r.evidence().quote();
			sb.append("| ").append(label)
				.append(" | `").append(cell(r.path())).append('`')
				.append(" | ").append(r.draft() == null ? "(없음)" : cell(r.draft().toString()))
				.append(" | ").append(r.manual() == null ? "(없음)" : cell(r.manual().toString()))
				.append(" | ").append(cell(ev))
				.append(" |\n");
		}
		return sb.toString();
	}

	/** 표 칸: 줄바꿈·세로선·백틱을 바꾸고 길면 자른다. */
	static String cell(String text) {
		String t = text.replace("\r", " ").replace("\n", " ").replace("|", "\\|").replace("`", "'");
		if (t.codePointCount(0, t.length()) > CELL_LIMIT) {
			t = t.substring(0, t.offsetByCodePoints(0, CELL_LIMIT)) + "…";
		}
		return t;
	}

}
