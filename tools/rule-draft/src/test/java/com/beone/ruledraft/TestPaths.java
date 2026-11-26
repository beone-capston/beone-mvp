package com.beone.ruledraft;

import java.net.URISyntaxException;
import java.nio.file.Path;

/** 테스트 공용 경로. */
public final class TestPaths {

	private TestPaths() {
	}

	/** build.gradle이 넘기는 backend 규칙 디렉터리(기준 표본). */
	public static Path rulesDir() {
		String dir = System.getProperty("ruledraft.rulesDir");
		if (dir == null) {
			throw new IllegalStateException("ruledraft.rulesDir 시스템 속성이 없다");
		}
		return Path.of(dir);
	}

	public static Path fixture(String name) {
		try {
			return Path.of(TestPaths.class.getResource("/fixtures/" + name).toURI());
		}
		catch (URISyntaxException e) {
			throw new IllegalStateException(e);
		}
	}

}
