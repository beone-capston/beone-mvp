package com.beone.ruledraft.check;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 도구가 규칙 경로에 쓰지 못하게 막는다. VERIFIED 반영은 사람이 별도 커밋으로만 한다(결정 41).
 */
public final class OutputGuard {

	/** 저장소 안 규칙 경로(작업 디렉터리 기준이 달라도 이름으로도 막는다). */
	static final String RULES_SUFFIX = "backend/src/main/resources/rules";

	private OutputGuard() {
	}

	/**
	 * {@code target}이 보호 경로 안이면 예외를 던진다.
	 *
	 * @param target 쓰려는 파일 또는 디렉터리
	 * @param protectedRoots 추가 보호 경로(예: 기준 표본 디렉터리)
	 */
	public static void ensureWritable(Path target, List<Path> protectedRoots) throws IOException {
		Path resolved = canonical(target);
		for (Path root : protectedRoots) {
			Path r = canonical(root);
			if (resolved.startsWith(r)) {
				throw new IOException("규칙 경로에는 쓰지 않는다: " + target);
			}
		}
		String unix = resolved.toString().replace('\\', '/');
		if (unix.endsWith("/" + RULES_SUFFIX) || unix.contains("/" + RULES_SUFFIX + "/")) {
			throw new IOException("규칙 경로에는 쓰지 않는다: " + target);
		}
	}

	/** 아직 없는 경로는 존재하는 가장 가까운 조상의 실제 경로에 나머지를 붙인다(링크 우회 방지). */
	static Path canonical(Path path) throws IOException {
		Path abs = path.toAbsolutePath().normalize();
		Path existing = abs;
		while (existing != null && !Files.exists(existing)) {
			existing = existing.getParent();
		}
		if (existing == null) {
			return abs;
		}
		return existing.toRealPath().resolve(existing.relativize(abs)).normalize();
	}

}
