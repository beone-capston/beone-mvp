package com.beone.ruledraft.check;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 도구가 규칙 경로에 쓰지 못하게 막는다. VERIFIED 반영은 사람이 별도 커밋으로만 한다(결정 41).
 *
 * <p>검사한 경로와 실제로 쓰는 경로가 같아야 하므로, 호출 쪽은 {@link #ensureWritable}이 돌려준 경로에만 쓴다.
 */
public final class OutputGuard {

	/** 저장소 안 규칙 경로(작업 디렉터리 기준이 달라도 이름으로도 막는다). */
	static final String RULES_SUFFIX = "backend/src/main/resources/rules";

	private OutputGuard() {
	}

	/**
	 * {@code target}이 보호 경로 안이면 예외를 던지고, 아니면 실제로 쓸 경로를 돌려준다.
	 *
	 * @param target 쓰려는 파일 또는 디렉터리
	 * @param protectedRoots 추가 보호 경로(예: 기준 표본 디렉터리)
	 * @return 링크와 {@code ..}를 운영체제 방식으로 푼 경로. 호출 쪽은 이 경로에 쓴다
	 */
	public static Path ensureWritable(Path target, List<Path> protectedRoots) throws IOException {
		Path resolved = canonical(target);
		for (Path root : protectedRoots) {
			// 보호 경로는 없을 수도 있다(다른 작업 디렉터리에서 실행). 그때는 글자 정규화 경로로 비교한다
			Path r = Files.exists(root) ? root.toRealPath() : root.toAbsolutePath().normalize();
			if (resolved.startsWith(r)) {
				throw new IOException("규칙 경로에는 쓰지 않는다: " + target);
			}
		}
		String unix = resolved.toString().replace('\\', '/');
		if (unix.endsWith("/" + RULES_SUFFIX) || unix.contains("/" + RULES_SUFFIX + "/")) {
			throw new IOException("규칙 경로에는 쓰지 않는다: " + target);
		}
		return resolved;
	}

	/**
	 * 존재하는 가장 긴 앞부분은 운영체제가 푸는 방식({@link Path#toRealPath})으로 풀고, 아직 없는 나머지를 붙인다.
	 *
	 * <p>앞부분을 먼저 글자로 정규화하지 않는다. {@code link/..}처럼 링크 뒤의 {@code ..}는 운영체제에서는 링크 대상의
	 * 부모를 뜻하므로, 글자 정규화로 지우면 검사 경로와 실제 쓰기 경로가 어긋난다. 아직 없는 나머지에
	 * {@code .}·{@code ..}가 있으면 어디로 풀릴지 정할 수 없으므로 거부한다.
	 */
	static Path canonical(Path path) throws IOException {
		Path abs = path.toAbsolutePath();
		Path existing = abs;
		while (existing != null && !Files.exists(existing)) {
			existing = existing.getParent();
		}
		if (existing == null) {
			throw new IOException("경로를 확인할 수 없다: " + path);
		}
		if (existing.getNameCount() == abs.getNameCount()) {
			return existing.toRealPath();
		}
		// relativize는 .. 를 정규화하므로 쓰지 않고, getParent로 얻은 앞부분 뒤의 이름을 그대로 자른다
		Path rest = abs.subpath(existing.getNameCount(), abs.getNameCount());
		for (Path part : rest) {
			String name = part.toString();
			if (name.equals("..") || name.equals(".")) {
				throw new IOException("아직 없는 경로 부분에 . 또는 ..을 쓸 수 없다: " + path);
			}
		}
		return existing.toRealPath().resolve(rest);
	}

}
