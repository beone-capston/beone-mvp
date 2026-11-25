package com.beone.ruledraft.check;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import com.beone.ruledraft.source.SourceDocument;
import com.beone.ruledraft.source.SourcePage;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 초안의 값마다 원문 근거(쪽·인용)가 있는지 확인한다.
 *
 * <ul>
 *   <li>근거는 값 경로 자신 또는 조상 경로에 달 수 있다(예: {@code /limitBuckets/0} 근거가 그 아래 값 전체를 덮는다).</li>
 *   <li>인용문은 해당 쪽 텍스트에 공백을 무시하고 그대로 있어야 확인된 근거로 친다.</li>
 *   <li>프로젝트 관리 필드는 원문 근거를 요구하지 않는다: {@code /status}(도구가 AI_DRAFT로 고정),
 *       {@code /evaluationOnly}, {@code /sources} 아래, 이름이 {@code evidence}이거나 {@code Evidence}로 끝나는 키
 *       (docs/rules 근거 표 번호).</li>
 * </ul>
 */
public final class EvidenceChecker {

	private EvidenceChecker() {
	}

	public static EvidenceReport check(JsonNode draft, Map<String, Evidence> evidence, SourceDocument source) {
		Set<String> draftPaths = new HashSet<>();
		JsonPaths.walk(draft, (path, shape, node) -> draftPaths.add(path));

		Map<String, Evidence> verified = new TreeMap<>();
		List<String> unverified = new ArrayList<>();
		for (Map.Entry<String, Evidence> e : new TreeMap<>(evidence).entrySet()) {
			String path = e.getKey();
			Optional<String> problem = verify(path, e.getValue(), draftPaths, source);
			if (problem.isPresent()) {
				unverified.add(path + ": " + problem.get());
			}
			else {
				verified.put(path, e.getValue());
			}
		}

		List<String> missing = new ArrayList<>();
		JsonPaths.leaves(draft, (path, node) -> {
			if (!exempt(path) && !covered(path, verified)) {
				missing.add(path);
			}
		});
		return new EvidenceReport(verified, missing, unverified);
	}

	private static Optional<String> verify(String path, Evidence ev, Set<String> draftPaths, SourceDocument source) {
		if (path.isEmpty() || !draftPaths.contains(path)) {
			return Optional.of("초안에 없는 경로");
		}
		if (ev.quote() == null || ev.quote().isBlank()) {
			return Optional.of("인용문 없음");
		}
		Optional<SourcePage> page = source.page(ev.page());
		if (page.isEmpty()) {
			return Optional.of("원문에 없는 쪽 " + ev.page());
		}
		if (!normalize(page.get().text()).contains(normalize(ev.quote()))) {
			return Optional.of(ev.page() + "쪽에서 인용문을 찾지 못함");
		}
		return Optional.empty();
	}

	static boolean covered(String path, Map<String, Evidence> verified) {
		String p = path;
		while (!p.isEmpty()) {
			if (verified.containsKey(p)) {
				return true;
			}
			p = p.substring(0, p.lastIndexOf('/'));
		}
		return false;
	}

	/** 원문 근거를 요구하지 않는 프로젝트 관리 필드. */
	public static boolean exempt(String path) {
		if (path.equals("/status") || path.equals("/evaluationOnly") || path.equals("/sources") || path.startsWith("/sources/")) {
			return true;
		}
		for (String segment : path.split("/")) {
			if (segment.equals("evidence") || segment.endsWith("Evidence")) {
				return true;
			}
		}
		return false;
	}

	static String normalize(String text) {
		return text.replaceAll("[\\s\\u00A0\\u3000]+", "");
	}

}
