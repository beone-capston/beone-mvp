package com.beone.ruledraft.review;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 비교 결과와 사람의 수정 항목을 검사해 승인 기록 JSON을 만든다. 파일을 읽거나 쓰지 않는다.
 *
 * <p>승인 기록은 "사람이 이 비교 결과를 보고 이렇게 고치기로 했다"는 기록일 뿐이다. 규칙 JSON의 {@code VERIFIED} 반영은
 * 이 기록과 별도로 사람이 rules JSON을 고쳐 커밋한다.
 */
public final class ApprovalRecorder {

	public static final String NOTE = "이 기록은 규칙 파일을 바꾸지 않는다. VERIFIED 반영은 사람이 원문과 대조해 rules JSON을 고친 뒤 별도 커밋으로 한다.";

	private ApprovalRecorder() {
	}

	/**
	 * 승인을 막는 문제 목록. 비어 있어야 기록할 수 있다.
	 *
	 * @param compare 비교 결과 JSON({@link CompareReport#toJson})
	 */
	public static List<String> problems(JsonNode compare, String reviewer, List<ReviewEdit> edits) {
		List<String> problems = new ArrayList<>();
		if (reviewer == null || reviewer.isBlank()) {
			problems.add("승인자가 비어 있다");
		}
		Map<String, JsonNode> rows = new HashMap<>();
		for (JsonNode row : compare.path("rows")) {
			rows.put(row.path("path").asText(), row);
			if (CompareStatus.NO_EVIDENCE.name().equals(row.path("status").asText())) {
				problems.add("근거 없음: " + row.path("path").asText());
			}
		}
		Set<String> seen = new HashSet<>();
		for (ReviewEdit e : edits) {
			String path = e.path();
			if (path == null || !path.startsWith("/")) {
				problems.add("수정 경로는 /로 시작해야 한다: " + path);
				continue;
			}
			if (!seen.add(path)) {
				problems.add("같은 경로를 두 번 고쳤다: " + path);
			}
			if (e.reason() == null || e.reason().isBlank()) {
				problems.add("수정 사유가 없다: " + path);
			}
			JsonNode row = rows.get(path);
			if (row == null) {
				problems.add("비교 결과에 없는 경로: " + path);
				continue;
			}
			JsonNode draft = row.has("draft") ? row.get("draft") : null;
			if (!same(draft, e.before())) {
				problems.add("수정 전 값이 초안 값과 다르다: " + path);
			}
			if (same(e.before(), e.after())) {
				problems.add("수정 전후 값이 같다: " + path);
			}
		}
		return problems;
	}

	public static ObjectNode record(ObjectMapper mapper, JsonNode compare, String compareFile, String compareSha256,
			String reviewer, Instant approvedAt, List<ReviewEdit> edits) {
		ObjectNode root = mapper.createObjectNode();
		root.put("cardId", compare.path("cardId").asText());
		root.put("reviewer", reviewer);
		root.put("approvedAt", approvedAt.toString());
		ObjectNode src = root.putObject("compare");
		src.put("fileName", compareFile);
		src.put("sha256", compareSha256);
		src.put("draftSha256", compare.path("draftSha256").asText());
		src.set("manual", compare.path("manual").deepCopy());
		src.set("summary", compare.path("summary").deepCopy());
		ArrayNode list = root.putArray("edits");
		for (ReviewEdit e : edits) {
			ObjectNode item = list.addObject();
			item.put("path", e.path());
			// Java null(값 없음)은 키를 생략하고, JSON null(NullNode)만 null로 쓴다. 추가는 before, 삭제는 after가 없다.
			if (e.before() != null) {
				item.set("before", e.before().deepCopy());
			}
			if (e.after() != null) {
				item.set("after", e.after().deepCopy());
			}
			item.put("reason", e.reason());
		}
		root.put("note", NOTE);
		return root;
	}

	/** 둘 다 없음(null)이거나 같은 값. JSON null 노드와 "없음"은 다르다. */
	private static boolean same(JsonNode a, JsonNode b) {
		if (a == null || b == null) {
			return a == b;
		}
		return RuleComparator.sameValue(a, b);
	}

}
