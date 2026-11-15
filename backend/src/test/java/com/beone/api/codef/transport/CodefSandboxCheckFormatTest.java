package com.beone.api.codef.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 샌드박스 확인의 출력 형식을 합성 응답으로 시험한다. 로컬 서버만 호출한다.
 */
class CodefSandboxCheckFormatTest {

	private static final Map<String, String> ENVIRONMENT = Map.of(CodefApiKeys.CLIENT_ID_VARIABLE, "synthetic-id-marker",
			CodefApiKeys.CLIENT_SECRET_VARIABLE, "synthetic-value-marker");

	private static final String TOKEN = "synthetic-token-marker";

	private CodefStubServer stub;

	private CodefSandboxCheck check;

	@BeforeEach
	void start() throws IOException {
		stub = new CodefStubServer();
		check = new CodefSandboxCheck(stub.settings(Duration.ofMillis(500)), CodefApiKeys.fromEnvironment(ENVIRONMENT));
	}

	@AfterEach
	void stop() {
		stub.close();
	}

	@Test
	void printsOnlyAllowedFieldsAndCallsEachApiOnce() {
		stub.respond(CodefStubServer.TOKEN_PATH, 200, "{\"access_token\":\"" + TOKEN + "\",\"expires_in\":1,\"secretish\":\"value-marker\"}");
		stub.respond(CodefApi.HELD_CARDS.path(), 200, encoded("{\"result\":{\"code\":\"CF-00000\",\"message\":\"body-marker\"},\"data\":[{\"cardName\":\"body-marker\"}]}"));
		stub.respond(CodefApi.APPROVALS.path(), 200, "{\"result\":{\"code\":\"body-marker\"},\"data\":{\"a\":\"body-marker\"}}");
		stub.respond(CodefApi.CARD_PERFORMANCES.path(), 500, "body-marker");

		List<String> output = check.run("sandbox.codef.io", "1").printable(ENVIRONMENT);

		assertThat(output).containsExactly("CODEF_SANDBOX host=sandbox.codef.io run=1",
				"TOKEN    success=true http=200 encoding=RAW_JSON fields=access_token,expires_in otherFields=1",
				"CARD     sent=1 http=200 encoding=PERCENT resultCode=CF-00000 hasResult=true data=ARRAY",
				"APPROVAL sent=1 http=200 encoding=RAW_JSON resultCode=UNRECOGNIZED hasResult=true data=OBJECT",
				"RESULT   sent=1 http=500 encoding=UNREADABLE resultCode=NONE hasResult=false data=MISSING",
				"OUTPUT_SELF_CHECK=PASS");
		assertThat(String.join("\n", output)).doesNotContain("body-marker", "value-marker", TOKEN);
		assertThat(stub.requests()).hasSize(4);
	}

	@Test
	void stopsBeforeProductCallsWhenTokenFails() {
		stub.respond(CodefStubServer.TOKEN_PATH, 401, "body-marker");

		List<String> output = check.run("sandbox.codef.io", "2").printable(ENVIRONMENT);

		assertThat(output).hasSize(3);
		assertThat(output.get(1)).startsWith("TOKEN    success=false http=401");
		assertThat(stub.requests()).hasSize(1);
	}

	@Test
	void callFailureWritesReasonOnly() {
		stub.respond(CodefStubServer.TOKEN_PATH, 200, "{\"access_token\":\"" + TOKEN + "\"}");
		stub.neverRespond(CodefApi.HELD_CARDS.path());
		stub.dropConnection(CodefApi.APPROVALS.path());
		stub.respond(CodefApi.CARD_PERFORMANCES.path(), 200, "{}");

		List<String> output = check.run("sandbox.codef.io", "1").printable(ENVIRONMENT);

		assertThat(output).contains("CARD     sent=1 failure=TIMEOUT", "APPROVAL sent=1 failure=CONNECTION_FAILED");
	}

	@Test
	void selfCheckDropsAllLinesWhenSecretAppears() {
		CodefSandboxCheck.Report report = new CodefSandboxCheck.Report(List.of("line synthetic-value-marker"), null);

		assertThat(report.printable(ENVIRONMENT)).containsExactly("OUTPUT_SELF_CHECK=FAIL");
	}

	@Test
	void guardRejectsAnythingButOfficialSandbox() {
		assertThatThrownBy(() -> CodefSandboxCheck.requireSandbox(stub.settings(Duration.ofSeconds(1))))
			.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> CodefSandboxCheck.requireSandbox(new CodefTransportSettings(CodefTransportSettings.DEFAULT_TOKEN_URI,
				java.net.URI.create("https://api.codef.io"), Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1))))
			.isInstanceOf(IllegalStateException.class);
		CodefSandboxCheck.requireSandbox(CodefTransportSettings.defaults());
	}

	@Test
	void buildScriptExcludesSandboxTagFromRegularTests() throws IOException {
		String build = java.nio.file.Files.readString(java.nio.file.Path.of("build.gradle"));

		assertThat(build).contains("excludeTags 'integration', 'web', 'codef-sandbox'");
		assertThat(build).doesNotContain("dependsOn codefSandboxCheck", "dependsOn 'codefSandboxCheck'");
	}

	private static String encoded(String json) {
		return URLEncoder.encode(json, StandardCharsets.UTF_8);
	}

}
