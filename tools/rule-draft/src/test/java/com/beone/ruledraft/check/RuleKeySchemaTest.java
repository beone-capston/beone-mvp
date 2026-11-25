package com.beone.ruledraft.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.beone.ruledraft.TestPaths;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class RuleKeySchemaTest {

	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	void 기준_표본은_backend_규칙_4개다() throws IOException {
		assertEquals(4, RuleKeySchema.fromSamples(TestPaths.rulesDir()).sampleCount());
	}

	@Test
	void 카드_디렉터리_밖이나_이름이_다른_JSON은_표본이_아니다(@TempDir Path tmp) throws IOException {
		Files.createDirectories(tmp.resolve("12345"));
		Files.createDirectories(tmp.resolve(".omc/state"));
		Files.writeString(tmp.resolve("12345/12345-A-1.json"), "{}");
		Files.writeString(tmp.resolve("12345/other.json"), "{}");
		Files.writeString(tmp.resolve(".omc/state/x.json"), "{}");
		Files.writeString(tmp.resolve("top.json"), "{}");
		assertEquals(List.of(tmp.resolve("12345/12345-A-1.json")), RuleKeySchema.ruleFiles(tmp));
	}

	@Test
	void 표본_자신은_모두_통과한다() throws IOException {
		RuleKeySchema schema = RuleKeySchema.fromSamples(TestPaths.rulesDir());
		for (Path f : RuleKeySchema.ruleFiles(TestPaths.rulesDir())) {
			assertEquals(List.of(), schema.validate(mapper.readTree(f.toFile())), f.getFileName().toString());
		}
	}

	@Test
	void 모르는_키_종류_불일치_필수_키_누락을_찾는다() throws IOException {
		RuleKeySchema schema = RuleKeySchema.fromSamples(TestPaths.rulesDir());
		Path sample = RuleKeySchema.ruleFiles(TestPaths.rulesDir()).get(0);
		ObjectNode draft = (ObjectNode) mapper.readTree(sample.toFile());
		draft.put("aiConfidence", 0.9);
		draft.put("evaluationOnly", "false");
		draft.remove("services");
		((ObjectNode) draft.at("/limitBuckets/0/tiers/0")).put("extra", 1);

		List<String> problems = schema.validate(draft);

		assertTrue(problems.contains("표본에 없는 키: /aiConfidence"), problems.toString());
		assertTrue(problems.contains("값 종류 불일치: /evaluationOnly 기대 [boolean] 실제 string"), problems.toString());
		assertTrue(problems.contains("필수 키 누락: /services"), problems.toString());
		assertTrue(problems.contains("표본에 없는 키: /limitBuckets/0/tiers/0/extra"), problems.toString());
	}

	@Test
	void 루트가_객체가_아니면_실패() throws IOException {
		RuleKeySchema schema = RuleKeySchema.fromSamples(TestPaths.rulesDir());
		assertEquals(List.of("초안 루트가 객체가 아니다"), schema.validate(mapper.readTree("[]")));
	}

}
