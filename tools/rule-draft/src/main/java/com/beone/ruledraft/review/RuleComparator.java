package com.beone.ruledraft.review;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.beone.ruledraft.check.Evidence;
import com.beone.ruledraft.check.EvidenceChecker;
import com.beone.ruledraft.check.JsonPaths;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * AI 초안과 수동 규칙을 값 경로(JSON Pointer)별로 비교한다. 순수 함수이며 파일을 읽거나 쓰지 않는다.
 *
 * <ul>
 *   <li>비교 단위는 자식이 없는 노드(값, 빈 배열·객체)다. 배열은 번호끼리 비교한다(순서가 다르면 불일치로 나온다).</li>
 *   <li>숫자는 표기와 상관없이 값으로 비교한다(10000과 10000.0은 일치).</li>
 *   <li>{@code /status}는 비교하지 않는다. 초안은 도구가 AI_DRAFT로 고정하므로 항상 다르다.</li>
 *   <li>초안에 있는 값 중 근거를 요구하는 값({@link EvidenceChecker#exempt} 제외)에 확인된 근거가 자기·조상 경로에 없으면
 *       근거 없음으로 분류한다.</li>
 * </ul>
 */
public final class RuleComparator {

	static final String STATUS_PATH = "/status";

	private RuleComparator() {
	}

	public static List<CompareRow> compare(JsonNode draft, JsonNode manual, Map<String, Evidence> verified) {
		Map<String, JsonNode> draftLeaves = leaves(draft);
		Map<String, JsonNode> manualLeaves = leaves(manual);
		List<String> paths = new ArrayList<>(draftLeaves.keySet());
		manualLeaves.keySet().stream().filter(p -> !draftLeaves.containsKey(p)).forEach(paths::add);

		List<CompareRow> rows = new ArrayList<>();
		for (String path : paths) {
			if (path.equals(STATUS_PATH)) {
				continue;
			}
			JsonNode d = draftLeaves.get(path);
			JsonNode m = manualLeaves.get(path);
			CompareStatus comparison;
			if (d == null) {
				comparison = CompareStatus.MISSING_IN_DRAFT;
			}
			else if (m == null) {
				comparison = CompareStatus.MISSING_IN_MANUAL;
			}
			else {
				comparison = sameValue(d, m) ? CompareStatus.MATCH : CompareStatus.MISMATCH;
			}
			String evidencePath = d == null ? null : coveringPath(path, verified);
			CompareStatus status = comparison;
			if (d != null && !EvidenceChecker.exempt(path) && evidencePath == null) {
				status = CompareStatus.NO_EVIDENCE;
			}
			rows.add(new CompareRow(path, status, comparison, d, m, evidencePath == null ? null : verified.get(evidencePath),
					evidencePath));
		}
		return rows;
	}

	/** 숫자는 값으로, 나머지는 JSON 값 그대로 비교한다. */
	public static boolean sameValue(JsonNode a, JsonNode b) {
		if (a.isNumber() && b.isNumber()) {
			return a.decimalValue().compareTo(b.decimalValue()) == 0;
		}
		return a.equals(b);
	}

	private static Map<String, JsonNode> leaves(JsonNode root) {
		Map<String, JsonNode> map = new LinkedHashMap<>();
		JsonPaths.leaves(root, map::put);
		return map;
	}

	/** 경로 자신 또는 가장 가까운 조상 중 확인된 근거가 있는 경로. 없으면 null. */
	static String coveringPath(String path, Map<String, Evidence> verified) {
		String p = path;
		while (!p.isEmpty()) {
			if (verified.containsKey(p)) {
				return p;
			}
			p = p.substring(0, p.lastIndexOf('/'));
		}
		return null;
	}

}
