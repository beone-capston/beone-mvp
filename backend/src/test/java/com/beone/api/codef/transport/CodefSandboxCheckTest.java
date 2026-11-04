package com.beone.api.codef.transport;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * 실제 CODEF 샌드박스를 호출한다. {@code codefSandboxCheck} 작업에서만 돈다.
 * 태그로 빠지고, 그 작업이 넘기는 시스템 속성이 없으면 IDE의 전체 실행에서도 돌지 않는다.
 */
@Tag("codef-sandbox")
@EnabledIfSystemProperty(named = "beone.codef.sandbox.confirmed", matches = "true")
class CodefSandboxCheckTest {

	@Test
	void callsSandboxOnceAndPrintsOnlyAllowedFields() {
		CodefTransportSettings settings = CodefTransportSettings.defaults();
		CodefSandboxCheck.requireSandbox(settings);
		Map<String, String> environment = System.getenv();
		CodefApiKeys keys = CodefApiKeys.fromEnvironment(environment);
		if (!keys.isConfigured()) {
			System.out.println("CODEF_SANDBOX failure=KEYS_NOT_CONFIGURED missing=" + keys.missingVariables());
		}
		assertThat(keys.isConfigured()).as("keys configured").isTrue();

		List<String> output = new CodefSandboxCheck(settings, keys).run(settings.productHost().getHost(), "1")
			.printable(environment);

		output.forEach(System.out::println);
		assertThat(output.getLast()).as("self check").isEqualTo("OUTPUT_SELF_CHECK=PASS");
	}

}
