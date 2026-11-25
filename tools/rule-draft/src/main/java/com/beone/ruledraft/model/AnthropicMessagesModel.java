package com.beone.ruledraft.model;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import com.beone.ruledraft.source.SourcePage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Anthropic Messages API 어댑터(후보 제공자). 컴파일만 확인하고 테스트에서는 호출하지 않는다.
 *
 * <p>제공자·모델 선택과 실제 호출은 사용자 승인 항목이다(결정 41). 키는 생성자로만 받고
 * 요청 헤더 밖으로 내보내지 않는다. 오류 메시지에는 HTTP 상태와 응답 종료 사유만 넣는다.
 */
public final class AnthropicMessagesModel implements RuleDraftModel {

	static final URI ENDPOINT = URI.create("https://api.anthropic.com/v1/messages");
	static final String API_VERSION = "2026-10-21";
	public static final String DEFAULT_MODEL = "claude-opus-5-5";
	private static final int MAX_TOKENS = 16000;

	private final HttpTransport transport;
	private final String apiKey;
	private final String model;
	private final ObjectMapper mapper = new ObjectMapper();

	public AnthropicMessagesModel(HttpTransport transport, String apiKey, String model) {
		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalArgumentException("키가 비어 있다");
		}
		this.transport = transport;
		this.apiKey = apiKey;
		this.model = model;
	}

	@Override
	public String draft(DraftRequest request) throws ModelException {
		String body = requestBody(request);
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("content-type", "application/json");
		headers.put("anthropic-version", API_VERSION);
		headers.put("x-api-key", apiKey);
		HttpTransport.Result result;
		try {
			result = transport.post(ENDPOINT, headers, body);
		}
		catch (IOException e) {
			// 원인 예외 메시지에 요청 정보가 섞일 수 있어 연결하지 않는다
			throw new ModelException("모델 호출 실패(네트워크): " + e.getClass().getSimpleName());
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ModelException("모델 호출이 중단되었다");
		}
		if (result.status() != 200) {
			throw new ModelException("모델 호출 실패: HTTP " + result.status());
		}
		return extractText(result.body());
	}

	String requestBody(DraftRequest request) {
		ObjectNode root = mapper.createObjectNode();
		root.put("model", model);
		root.put("max_tokens", MAX_TOKENS);
		root.put("system", request.instructions());
		ArrayNode messages = root.putArray("messages");
		ObjectNode user = messages.addObject();
		user.put("role", "user");
		StringBuilder text = new StringBuilder();
		text.append("카드 ID: ").append(request.cardId()).append("\n원문 파일: ").append(request.source().fileName()).append("\n\n");
		for (SourcePage page : request.source().pages()) {
			text.append("[[page ").append(page.number()).append("]]\n").append(page.text().strip()).append("\n\n");
		}
		user.put("content", text.toString());
		try {
			return mapper.writeValueAsString(root);
		}
		catch (IOException e) {
			throw new IllegalStateException("요청 본문을 만들지 못했다", e);
		}
	}

	String extractText(String responseBody) throws ModelException {
		JsonNode root;
		try {
			root = mapper.readTree(responseBody);
		}
		catch (IOException e) {
			throw new ModelException("모델 응답이 JSON이 아니다");
		}
		String stop = root.path("stop_reason").asText("");
		if (!"end_turn".equals(stop)) {
			throw new ModelException("모델 응답이 정상 종료되지 않았다: stop_reason=" + stop);
		}
		StringBuilder sb = new StringBuilder();
		for (JsonNode block : root.path("content")) {
			if ("text".equals(block.path("type").asText())) {
				sb.append(block.path("text").asText());
			}
		}
		if (sb.isEmpty()) {
			throw new ModelException("모델 응답에 텍스트가 없다");
		}
		return sb.toString();
	}

	@Override
	public String describe() {
		return "anthropic:" + model;
	}

}
