package com.beone.ruledraft.check;

import java.util.Map;
import java.util.function.BiConsumer;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * JSON Pointer(RFC 6901) 경로 도구. 근거 파일의 키는 이 형식이다(예: {@code /limitBuckets/0/tiers/1/monthlyCap}).
 */
public final class JsonPaths {

	/** 키 구조 비교용으로 배열 번호를 대신하는 자리 표시. */
	public static final String ANY_INDEX = "[]";

	private JsonPaths() {
	}

	public static String child(String parent, String key) {
		return parent + "/" + key.replace("~", "~0").replace("/", "~1");
	}

	public static String index(String parent, int index) {
		return parent + "/" + index;
	}

	/** 노드 종류 이름. object·array·string·number·boolean·null */
	public static String kind(JsonNode node) {
		if (node.isObject()) {
			return "object";
		}
		if (node.isArray()) {
			return "array";
		}
		if (node.isTextual()) {
			return "string";
		}
		if (node.isNumber()) {
			return "number";
		}
		if (node.isBoolean()) {
			return "boolean";
		}
		if (node.isNull()) {
			return "null";
		}
		return node.getNodeType().name().toLowerCase();
	}

	/**
	 * 모든 노드를 방문한다. 콜백은 (실제 경로, 배열 번호를 {@link #ANY_INDEX}로 바꾼 구조 경로, 노드)를 받는다.
	 * 루트는 경로 ""로 방문한다.
	 */
	public static void walk(JsonNode node, Visitor visitor) {
		walk("", "", node, visitor);
	}

	private static void walk(String path, String shape, JsonNode node, Visitor visitor) {
		visitor.visit(path, shape, node);
		if (node.isObject()) {
			for (Map.Entry<String, JsonNode> e : node.properties()) {
				walk(child(path, e.getKey()), child(shape, e.getKey()), e.getValue(), visitor);
			}
		}
		else if (node.isArray()) {
			for (int i = 0; i < node.size(); i++) {
				walk(index(path, i), shape + "/" + ANY_INDEX, node.get(i), visitor);
			}
		}
	}

	/** 자식이 없는 노드(값, 빈 배열·객체)만 방문한다. */
	public static void leaves(JsonNode node, BiConsumer<String, JsonNode> consumer) {
		walk(node, (path, shape, n) -> {
			if (!path.isEmpty() && (!n.isContainerNode() || n.isEmpty())) {
				consumer.accept(path, n);
			}
		});
	}

	@FunctionalInterface
	public interface Visitor {

		void visit(String path, String shape, JsonNode node);

	}

}
