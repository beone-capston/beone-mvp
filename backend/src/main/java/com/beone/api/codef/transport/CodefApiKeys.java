package com.beone.api.codef.transport;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * CODEF 클라이언트 키. 프로세스 환경 변수로만 읽는다. 기본값이 없고, 문자열 변환과 예외 메시지에 값을 내지 않는다.
 * 하나라도 없거나 비어 있으면 "설정 안 됨" 상태이며, 서버는 뜨고 CODEF 호출만 요청 없이 실패한다.
 */
public final class CodefApiKeys {

	static final String CLIENT_ID_VARIABLE = "CODEF_CLIENT_ID";

	static final String CLIENT_SECRET_VARIABLE = "CODEF_CLIENT_SECRET";

	private final String clientId;

	private final String clientSecret;

	private final List<String> missingVariables;

	private CodefApiKeys(String clientId, String clientSecret, List<String> missingVariables) {
		this.clientId = clientId;
		this.clientSecret = clientSecret;
		this.missingVariables = List.copyOf(missingVariables);
	}

	public static CodefApiKeys fromEnvironment(Map<String, String> environment) {
		String clientId = environment.get(CLIENT_ID_VARIABLE);
		String clientSecret = environment.get(CLIENT_SECRET_VARIABLE);
		List<String> missing = new ArrayList<>();
		if (isBlank(clientId)) {
			missing.add(CLIENT_ID_VARIABLE);
		}
		if (isBlank(clientSecret)) {
			missing.add(CLIENT_SECRET_VARIABLE);
		}
		if (!missing.isEmpty()) {
			return new CodefApiKeys(null, null, missing);
		}
		return new CodefApiKeys(clientId, clientSecret, missing);
	}

	public boolean isConfigured() {
		return missingVariables.isEmpty();
	}

	/**
	 * 비어 있는 환경 변수의 이름. 값과 길이는 담지 않는다.
	 */
	public List<String> missingVariables() {
		return missingVariables;
	}

	String basicAuthorization() {
		if (!isConfigured()) {
			throw new IllegalStateException("codef keys are not configured");
		}
		byte[] pair = (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8);
		return "Basic " + Base64.getEncoder().encodeToString(pair);
	}

	@Override
	public String toString() {
		return isConfigured() ? "CodefApiKeys[configured]" : "CodefApiKeys[not configured]";
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

}
