package com.beone.api.codef.transport;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 샌드박스 확인 한 번을 실행하고 허용한 항목만 담은 줄을 만든다. 토큰 1회, 상품 3종 각 1회를 보내고 다시 보내지 않는다.
 * 응답 값, 메시지, 헤더, 예외 메시지는 줄에 넣지 않는다.
 */
final class CodefSandboxCheck {

	private static final Set<String> TOKEN_FIELDS = Set.of("access_token", "token_type", "expires_in", "scope");

	private static final Pattern RESULT_CODE = Pattern.compile("CF-\\d{5}");

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private static final String OTHER_FIELDS_LABEL = " otherFields=";

	private final CodefHttp http;

	private final CodefApiKeys keys;

	CodefSandboxCheck(CodefTransportSettings settings, CodefApiKeys keys) {
		this.http = new CodefHttp(settings);
		this.keys = keys;
	}

	/** 공식 샌드박스 호스트가 아니면 호출 전에 실패한다. */
	static void requireSandbox(CodefTransportSettings settings) {
		if (!CodefTransportSettings.SANDBOX_HOST.equals(settings.productHost())
				|| !CodefTransportSettings.DEFAULT_TOKEN_URI.equals(settings.tokenUri())) {
			throw new IllegalStateException("codef sandbox check runs only against the sandbox host");
		}
	}

	Report run(String hostLabel, String run) {
		List<String> lines = new ArrayList<>();
		lines.add("CODEF_SANDBOX host=" + hostLabel + " run=" + run);
		String token = requestToken(lines);
		if (token != null) {
			lines.add(product("CARD", token, CodefApi.HELD_CARDS, "{\"organization\":\"0301\"}"));
			lines.add(product("APPROVAL", token, CodefApi.APPROVALS,
					"{\"organization\":\"0301\",\"startDate\":\"20260901\",\"endDate\":\"20260930\",\"orderBy\":\"0\"}"));
			lines.add(product("RESULT", token, CodefApi.CARD_PERFORMANCES, "{\"organization\":\"0301\"}"));
		}
		return new Report(lines, token);
	}

	private String requestToken(List<String> lines) {
		CodefHttp.Reply reply;
		try {
			reply = http.postToken(keys);
		}
		catch (CodefHttp.Failure failure) {
			lines.add("TOKEN    success=false failure=" + failure.reason());
			return null;
		}
		String token = reply.status() == 200 ? CodefTokenCache.readAccessToken(reply.body()).orElse(null) : null;
		lines.add("TOKEN    success=" + (token != null) + " http=" + reply.status() + " encoding=" + encoding(reply.body())
				+ tokenFields(reply.body()));
		return token;
	}

	private String product(String label, String token, CodefApi api, String body) {
		String prefix = String.format("%-8s sent=1", label);
		CodefHttp.Reply reply;
		try {
			reply = http.postProduct(token, api, body);
		}
		catch (CodefHttp.Failure failure) {
			return prefix + " failure=" + failure.reason();
		}
		JsonNode root = parse(reply.body());
		JsonNode result = root == null ? null : root.get("result");
		return prefix + " http=" + reply.status() + " encoding=" + encoding(reply.body()) + " resultCode=" + resultCode(result)
				+ " hasResult=" + (result != null) + " data=" + dataShape(root);
	}

	private static String tokenFields(String body) {
		JsonNode root = parse(body);
		if (root == null || !root.isObject()) {
			return " fields=UNREADABLE";
		}
		Set<String> known = new TreeSet<>();
		int others = 0;
		for (String name : root.propertyNames()) {
			if (TOKEN_FIELDS.contains(name)) {
				known.add(name);
			}
			else {
				others++;
			}
		}
		return " fields=" + String.join(",", known) + OTHER_FIELDS_LABEL + others;
	}

	private static String encoding(String body) {
		if (body.isEmpty()) {
			return "EMPTY";
		}
		return body.startsWith("{") ? "RAW_JSON" : (parse(body) != null ? "PERCENT" : "UNREADABLE");
	}

	private static String resultCode(JsonNode result) {
		JsonNode code = result == null ? null : result.get("code");
		if (code == null) {
			return "NONE";
		}
		return code.isString() && RESULT_CODE.matcher(code.stringValue()).matches() ? code.stringValue() : "UNRECOGNIZED";
	}

	private static String dataShape(JsonNode root) {
		JsonNode data = root == null ? null : root.get("data");
		if (data == null) {
			return "MISSING";
		}
		return data.isArray() ? "ARRAY" : (data.isObject() ? "OBJECT" : "OTHER");
	}

	private static JsonNode parse(String body) {
		try {
			String json = body.startsWith("{") ? body : URLDecoder.decode(body, StandardCharsets.UTF_8);
			return MAPPER.readTree(json);
		}
		catch (JacksonException | IllegalArgumentException exception) {
			return null;
		}
	}

	/** 실행 결과. 토큰은 자체 검사에만 쓰고 출력하지 않는다. */
	static final class Report {

		private final List<String> lines;

		private final String token;

		Report(List<String> lines, String token) {
			this.lines = List.copyOf(lines);
			this.token = token;
		}

		/**
		 * 출력할 줄을 돌려준다. 자체 검사에 실패하면 결과 줄을 버리고 실패 표시만 돌려준다.
		 */
		List<String> printable(Map<String, String> environment) {
			List<String> secrets = new ArrayList<>();
			if (token != null) {
				secrets.add(token);
			}
			for (String variable : List.of(CodefApiKeys.CLIENT_ID_VARIABLE, CodefApiKeys.CLIENT_SECRET_VARIABLE)) {
				String value = environment.get(variable);
				if (value != null && !value.isBlank()) {
					secrets.add(value);
				}
			}
			for (String line : lines) {
				for (String secret : secrets) {
					if (line.contains(secret)) {
						return List.of("OUTPUT_SELF_CHECK=FAIL");
					}
				}
			}
			List<String> printable = new ArrayList<>(lines);
			printable.add("OUTPUT_SELF_CHECK=PASS");
			return printable;
		}

		@Override
		public String toString() {
			return "Report[masked]";
		}

	}

}
