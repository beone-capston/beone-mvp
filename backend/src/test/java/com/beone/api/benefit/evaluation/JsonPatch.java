package com.beone.api.benefit.evaluation;

import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 평가 변형용 최소 RFC 6902 패치.
 * {@code add}와 {@code replace}만 지원하고, 그 밖의 연산은 조용히 무시하지 않도록 실패시킨다.
 */
final class JsonPatch {

	private JsonPatch() {
	}

	/**
	 * {@code document}의 복사본에 연산을 적용해 반환한다.
	 */
	static JsonNode apply(JsonNode document, JsonNode operations) {
		JsonNode patched = document.deepCopy();
		for (JsonNode operation : operations.values()) {
			FixtureJson.requireOnlyKeys(operation, "patch operation", "op", "path", "value");
			String op = FixtureJson.text(operation, "op");
			List<String> path = parsePointer(FixtureJson.text(operation, "path"));
			JsonNode value = FixtureJson.required(operation, "value").deepCopy();
			JsonNode parent = navigate(patched, path.subList(0, path.size() - 1));
			String last = path.get(path.size() - 1);
			switch (op) {
				case "add" -> add(parent, last, value);
				case "replace" -> replace(parent, last, value);
				default -> throw new IllegalArgumentException("unsupported patch operation: " + op);
			}
		}
		return patched;
	}

	private static void add(JsonNode parent, String key, JsonNode value) {
		if (parent instanceof ObjectNode object) {
			object.set(key, value);
		}
		else if (parent instanceof ArrayNode array) {
			if ("-".equals(key)) {
				array.add(value);
			}
			else {
				int index = Integer.parseInt(key);
				if (index < 0 || index > array.size()) {
					throw new IllegalArgumentException("patch index out of range: " + key);
				}
				array.insert(index, value);
			}
		}
		else {
			throw new IllegalArgumentException("patch parent is not a container");
		}
	}

	private static void replace(JsonNode parent, String key, JsonNode value) {
		if (parent instanceof ObjectNode object) {
			if (!object.has(key)) {
				throw new IllegalArgumentException("replace target does not exist: " + key);
			}
			object.set(key, value);
		}
		else if (parent instanceof ArrayNode array) {
			int index = Integer.parseInt(key);
			if (index < 0 || index >= array.size()) {
				throw new IllegalArgumentException("replace index out of range: " + key);
			}
			array.set(index, value);
		}
		else {
			throw new IllegalArgumentException("patch parent is not a container");
		}
	}

	private static JsonNode navigate(JsonNode root, List<String> tokens) {
		JsonNode current = root;
		for (String token : tokens) {
			JsonNode next = current.isArray() ? current.get(Integer.parseInt(token)) : current.get(token);
			if (next == null) {
				throw new IllegalArgumentException("patch path does not exist at " + token);
			}
			current = next;
		}
		return current;
	}

	private static List<String> parsePointer(String pointer) {
		if (!pointer.startsWith("/")) {
			throw new IllegalArgumentException("JSON pointer must start with '/': " + pointer);
		}
		List<String> tokens = new ArrayList<>();
		for (String raw : pointer.substring(1).split("/", -1)) {
			tokens.add(raw.replace("~1", "/").replace("~0", "~"));
		}
		return tokens;
	}

}
