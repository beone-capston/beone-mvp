package com.beone.api.benefit.rule;

import java.util.Objects;
import java.util.Optional;

/**
 * 규칙의 근거가 되는 공식 문서. 쪽이나 항목 위치는 선택 값이다.
 */
public record OfficialSource(String uri, Optional<String> locator) {

	public OfficialSource {
		if (uri == null || uri.isBlank()) {
			throw new IllegalArgumentException("source uri is required");
		}
		Objects.requireNonNull(locator, "locator");
		if (locator.isPresent() && locator.get().isBlank()) {
			throw new IllegalArgumentException("locator must not be blank when present");
		}
	}

	public static OfficialSource of(String uri, String locator) {
		return new OfficialSource(uri, Optional.of(locator));
	}

}
