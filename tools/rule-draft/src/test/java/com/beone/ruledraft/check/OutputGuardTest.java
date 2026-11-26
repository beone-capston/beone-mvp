package com.beone.ruledraft.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.beone.ruledraft.TestPaths;

class OutputGuardTest {

	@TempDir
	Path tmp;

	@Test
	void 규칙_디렉터리와_그_아래는_거부한다() {
		Path rules = TestPaths.rulesDir();
		assertThrows(IOException.class, () -> OutputGuard.ensureWritable(rules, List.of(rules)));
		assertThrows(IOException.class, () -> OutputGuard.ensureWritable(rules.resolve("01664/new.json"), List.of(rules)));
	}

	@Test
	void 다른_기준_표본을_지정해도_저장소_규칙_경로_이름은_거부한다() {
		Path elsewhere = tmp.resolve("repo/backend/src/main/resources/rules/x.json");
		assertThrows(IOException.class, () -> OutputGuard.ensureWritable(elsewhere, List.of(tmp.resolve("samples"))));
	}

	@Test
	void 규칙_디렉터리로_가는_링크도_거부한다() throws IOException {
		Path link = Files.createSymbolicLink(tmp.resolve("link"), TestPaths.rulesDir());
		assertThrows(IOException.class, () -> OutputGuard.ensureWritable(link.resolve("a.json"), List.of(TestPaths.rulesDir())));
	}

	@Test
	void 그_밖의_경로는_허용한다() throws IOException {
		OutputGuard.ensureWritable(tmp.resolve("out/99999.draft.json"), List.of(TestPaths.rulesDir()));
	}


	@Test
	void 링크_뒤의_점점은_운영체제_방식으로_풀어_규칙_경로면_거부한다() throws IOException {
		// tmp/trap -> rules/01664 이면 tmp/trap/../x.json 은 실제로 rules/x.json 이다(글자 정규화로는 tmp/x.json)
		Path trap = Files.createSymbolicLink(tmp.resolve("trap"), TestPaths.rulesDir().resolve("01664"));
		Path target = tmp.resolve("trap/../x.json");
		assertThrows(IOException.class, () -> OutputGuard.ensureWritable(target, List.of(TestPaths.rulesDir())));
		assertTrue(Files.isSymbolicLink(trap));
	}

	@Test
	void 돌려준_경로는_실제_쓰기_위치와_같다() throws IOException {
		Path real = Files.createDirectories(tmp.resolve("real/sub"));
		Path link = Files.createSymbolicLink(tmp.resolve("link"), real);
		Path got = OutputGuard.ensureWritable(link.resolve("../out.json"), List.of(TestPaths.rulesDir()));
		assertEquals(tmp.toRealPath().resolve("real/out.json"), got);
	}

	@Test
	void 없는_경로_부분의_점점은_거부한다() {
		assertThrows(IOException.class,
				() -> OutputGuard.ensureWritable(tmp.resolve("nope/../x.json"), List.of(TestPaths.rulesDir())));
	}

}
