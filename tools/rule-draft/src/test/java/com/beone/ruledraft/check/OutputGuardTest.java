package com.beone.ruledraft.check;

import static org.junit.jupiter.api.Assertions.assertThrows;

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

}
