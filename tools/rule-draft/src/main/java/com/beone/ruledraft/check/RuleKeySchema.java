package com.beone.ruledraft.check;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 기존 규칙 JSON(기준 표본)에서 뽑은 키 구조. 초안이 같은 구조인지 검사한다.
 *
 * <p>규칙:
 * <ul>
 *   <li>초안의 모든 구조 경로(배열 번호를 {@code []}로 바꾼 경로)가 표본에 있어야 한다(모르는 키 금지).</li>
 *   <li>각 경로의 값 종류(object·array·string·number·boolean·null)가 표본에서 본 종류 중 하나여야 한다.</li>
 *   <li>객체마다 표본의 같은 경로 객체들에 공통으로 있던 키는 초안에도 있어야 한다(필수 키).</li>
 * </ul>
 * 값 자체(금액·비율 등)는 검사하지 않는다. 값 비교는 비교기(D41-2)가 한다.
 */
public final class RuleKeySchema {

	private final Map<String, Set<String>> kinds;
	private final Map<String, Set<String>> requiredKeys;
	private final int sampleCount;

	private RuleKeySchema(Map<String, Set<String>> kinds, Map<String, Set<String>> requiredKeys, int sampleCount) {
		this.kinds = kinds;
		this.requiredKeys = requiredKeys;
		this.sampleCount = sampleCount;
	}

	/**
	 * 규칙 파일 목록. backend 배치와 같은 {@code <카드ID 5자리>/<카드ID>-*.json}만 고른다.
	 * 같은 디렉터리의 다른 JSON(로컬 도구 상태 등)은 표본에 넣지 않는다.
	 */
	public static List<Path> ruleFiles(Path rulesDir) throws IOException {
		try (Stream<Path> s = Files.list(rulesDir)) {
			List<Path> cardDirs = s.filter(Files::isDirectory)
				.filter(d -> d.getFileName().toString().matches("\\d{5}"))
				.sorted()
				.toList();
			List<Path> files = new ArrayList<>();
			for (Path dir : cardDirs) {
				String prefix = dir.getFileName() + "-";
				try (Stream<Path> f = Files.list(dir)) {
					f.filter(Files::isRegularFile)
						.filter(p -> p.getFileName().toString().startsWith(prefix) && p.getFileName().toString().endsWith(".json"))
						.sorted()
						.forEach(files::add);
				}
			}
			return files;
		}
	}

	/** {@link #ruleFiles}가 고른 규칙 JSON을 표본으로 읽는다. 읽기만 한다. */
	public static RuleKeySchema fromSamples(Path rulesDir) throws IOException {
		List<Path> files = ruleFiles(rulesDir);
		if (files.isEmpty()) {
			throw new IOException("기준 표본 규칙 JSON이 없다: " + rulesDir);
		}
		ObjectMapper mapper = new ObjectMapper();
		List<JsonNode> samples = new ArrayList<>();
		for (Path f : files) {
			samples.add(mapper.readTree(f.toFile()));
		}
		return fromNodes(samples);
	}

	static RuleKeySchema fromNodes(List<JsonNode> samples) {
		Map<String, Set<String>> kinds = new TreeMap<>();
		Map<String, Set<String>> required = new TreeMap<>();
		for (JsonNode sample : samples) {
			JsonPaths.walk(sample, (path, shape, node) -> {
				kinds.computeIfAbsent(shape, k -> new TreeSet<>()).add(JsonPaths.kind(node));
				if (node.isObject()) {
					Set<String> keys = new LinkedHashSet<>();
					node.fieldNames().forEachRemaining(keys::add);
					required.merge(shape, keys, (a, b) -> {
						Set<String> common = new TreeSet<>(a);
						common.retainAll(b);
						return common;
					});
				}
			});
		}
		return new RuleKeySchema(kinds, required, samples.size());
	}

	public int sampleCount() {
		return sampleCount;
	}

	public Set<String> shapes() {
		return Collections.unmodifiableSet(kinds.keySet());
	}

	/** 문제 목록을 돌려준다. 비어 있으면 통과다. */
	public List<String> validate(JsonNode draft) {
		List<String> problems = new ArrayList<>();
		if (!draft.isObject()) {
			problems.add("초안 루트가 객체가 아니다");
			return problems;
		}
		JsonPaths.walk(draft, (path, shape, node) -> {
			Set<String> allowed = kinds.get(shape);
			String where = path.isEmpty() ? "(루트)" : path;
			if (allowed == null) {
				problems.add("표본에 없는 키: " + where);
				return;
			}
			String kind = JsonPaths.kind(node);
			if (!allowed.contains(kind)) {
				problems.add("값 종류 불일치: " + where + " 기대 " + allowed + " 실제 " + kind);
				return;
			}
			if (node.isObject()) {
				Set<String> need = requiredKeys.getOrDefault(shape, Set.of());
				for (String key : need) {
					if (!node.has(key)) {
						problems.add("필수 키 누락: " + JsonPaths.child(path, key));
					}
				}
			}
		});
		return problems;
	}

	/** 지시문에 넣을 허용 구조 경로 목록(경로 → 값 종류). */
	public String describeShapes() {
		StringBuilder sb = new StringBuilder();
		Iterator<Map.Entry<String, Set<String>>> it = kinds.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, Set<String>> e = it.next();
			if (e.getKey().isEmpty()) {
				continue;
			}
			sb.append(e.getKey()).append(' ').append(e.getValue()).append('\n');
		}
		return sb.toString();
	}

}
