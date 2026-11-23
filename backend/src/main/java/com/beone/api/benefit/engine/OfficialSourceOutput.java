package com.beone.api.benefit.engine;

/**
 * 규칙의 공식 근거 하나.
 *
 * @param uri 문서 위치
 * @param locator 문서 안 위치
 */
public record OfficialSourceOutput(String uri, String locator) {

	public OfficialSourceOutput {
		EngineChecks.requireText(uri, "uri");
		EngineChecks.requireOptionalText(locator, "locator");
	}

}
