package com.beone.ruledraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** CLI 전체 흐름. 모든 경우에 네트워크 전송이 만들어지지도 호출되지도 않아야 한다. */
class MainTest {

	private static final String FAKE_KEY = "fake-key-must-not-appear-0123456789";

	@TempDir
	Path tmp;

	private final ObjectMapper mapper = new ObjectMapper();
	private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
	private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
	private FailingTransport transport;

	@BeforeEach
	void setUp() {
		transport = new FailingTransport();
	}

	@AfterEach
	void noNetwork() {
		assertEquals(0, transport.calls.get(), "실호출 0회");
		assertEquals(0, transport.created.get(), "전송 객체도 만들지 않는다");
		String all = out() + err();
		assertFalse(all.contains(FAKE_KEY), "키 값이 출력에 없어야 한다");
	}

	private int run(Map<String, String> env, String... args) {
		return Main.run(args, env, transport.supplier(), new PrintStream(outBytes, true, StandardCharsets.UTF_8),
				new PrintStream(errBytes, true, StandardCharsets.UTF_8));
	}

	private String out() {
		return outBytes.toString(StandardCharsets.UTF_8);
	}

	private String err() {
		return errBytes.toString(StandardCharsets.UTF_8);
	}

	private int draft(Path recorded, Path outDir, String... extra) {
		String[] base = { "draft", "--source", TestPaths.fixture("synthetic-99999.txt").toString(), "--card", "99999",
				"--out", outDir.toString(), "--rules-dir", TestPaths.rulesDir().toString(), "--recorded",
				recorded.toString() };
		String[] args = new String[base.length + extra.length];
		System.arraycopy(base, 0, args, 0, base.length);
		System.arraycopy(extra, 0, args, base.length, extra.length);
		return run(Map.of(), args);
	}

	/** 녹화 응답의 JSON 부분만 꺼내 고친 뒤 임시 파일로 쓴다. */
	private Path mutated(Consumer<ObjectNode> change) throws IOException {
		String text = Files.readString(TestPaths.fixture("recorded-99999.json"));
		JsonNode root = mapper.readTree(text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1));
		change.accept((ObjectNode) root);
		Path file = tmp.resolve("mutated.json");
		Files.writeString(file, mapper.writeValueAsString(root));
		return file;
	}

	@Test
	void 녹화_응답으로_초안과_근거를_만든다() throws IOException {
		Path outDir = tmp.resolve("out");
		int code = draft(TestPaths.fixture("recorded-99999.json"), outDir);

		assertEquals(0, code, err());
		JsonNode draft = mapper.readTree(outDir.resolve("99999.draft.json").toFile());
		JsonNode evidence = mapper.readTree(outDir.resolve("99999.evidence.json").toFile());
		assertEquals("AI_DRAFT", draft.path("status").asText(), "모델이 VERIFIED라고 해도 도구가 AI_DRAFT로 고정한다");
		assertEquals("VERIFIED", evidence.path("modelStatus").asText());
		assertEquals(20000, draft.at("/services/0/maximumTargetAmount").asInt());
		assertEquals(2, evidence.at("/fields/~1services~10/page").asInt());
		assertEquals(0, evidence.path("missing").size());
		assertEquals(0, evidence.path("unverified").size());
		assertEquals(64, evidence.at("/source/sha256").asText().length());
		assertEquals("recorded:recorded-99999.json", evidence.path("model").asText());
	}

	@Test
	void 근거가_빠지면_파일은_쓰고_종료_3() throws IOException {
		Path recorded = mutated(root -> ((ObjectNode) root.get("evidence")).remove("/services/0"));
		Path outDir = tmp.resolve("out");

		assertEquals(3, draft(recorded, outDir), err());
		JsonNode evidence = mapper.readTree(outDir.resolve("99999.evidence.json").toFile());
		assertTrue(evidence.path("missing").toString().contains("/services/0/maximumTargetAmount"));
		assertFalse(evidence.path("missing").toString().contains("/services/0/evidence"), "근거 표 번호는 원문 근거 대상이 아니다");
	}

	@Test
	void 원문에_없는_인용은_확인_실패로_남고_종료_3() throws IOException {
		Path recorded = mutated(root -> ((ObjectNode) root.at("/evidence/~1limitBuckets"))
			.put("quote", "통합 월 할인한도 5만원"));
		Path outDir = tmp.resolve("out");

		assertEquals(3, draft(recorded, outDir));
		JsonNode evidence = mapper.readTree(outDir.resolve("99999.evidence.json").toFile());
		assertTrue(evidence.path("unverified").get(0).asText().startsWith("/limitBuckets: 2쪽에서 인용문을 찾지 못함"));
		assertTrue(evidence.path("missing").toString().contains("/limitBuckets/0/tiers/0/monthlyCap"));
	}

	@Test
	void 스키마가_맞지_않으면_아무것도_쓰지_않고_종료_1() throws IOException {
		Path recorded = mutated(root -> {
			((ObjectNode) root.at("/draft/policy")).put("bonusRate", 5);
			((ObjectNode) root.at("/draft/services/0")).put("minimumAmount", "만원");
			((ObjectNode) root.at("/draft/validity")).remove("to");
		});
		Path outDir = tmp.resolve("out");

		assertEquals(1, draft(recorded, outDir));
		assertTrue(err().contains("표본에 없는 키: /policy/bonusRate"), err());
		assertTrue(err().contains("값 종류 불일치: /services/0/minimumAmount"), err());
		assertTrue(err().contains("필수 키 누락: /validity/to"), err());
		assertFalse(Files.exists(outDir));
	}

	@Test
	void 카드_ID가_다르면_종료_1() throws IOException {
		Path recorded = mutated(root -> ((ObjectNode) root.get("draft")).put("cardId", "01664"));
		assertEquals(1, draft(recorded, tmp.resolve("out")));
		assertTrue(err().contains("cardId 불일치"));
	}

	@Test
	void live_없이_키만_있으면_호출하지_않고_종료_2() {
		Path outDir = tmp.resolve("out");
		int code = run(Map.of(LiveGate.KEY_ENV, FAKE_KEY), "draft", "--source",
				TestPaths.fixture("synthetic-99999.txt").toString(), "--card", "99999", "--out", outDir.toString());

		assertEquals(2, code);
		assertTrue(err().contains("--live"), err());
		assertFalse(Files.exists(outDir));
	}

	@Test
	void live에_키가_없으면_종료_2() {
		Path outDir = tmp.resolve("out");
		int code = run(Map.of(), "draft", "--source", TestPaths.fixture("synthetic-99999.txt").toString(), "--card",
				"99999", "--out", outDir.toString(), "--live");

		assertEquals(2, code);
		assertTrue(err().contains("환경 변수 " + LiveGate.KEY_ENV), err());
		assertFalse(Files.exists(outDir));
	}

	@Test
	void live에_빈_키도_종료_2() {
		int code = run(Map.of(LiveGate.KEY_ENV, "  "), "draft", "--source", "x.txt", "--card", "99999", "--out",
				tmp.toString(), "--live");
		assertEquals(2, code);
	}

	@Test
	void recorded와_live를_함께_쓰면_종료_2() {
		assertEquals(2, draft(TestPaths.fixture("recorded-99999.json"), tmp.resolve("out"), "--live"));
	}

	@Test
	void 규칙_경로로_출력하면_종료_1() throws IOException {
		Path target = TestPaths.rulesDir().resolve("99999");
		assertEquals(1, draft(TestPaths.fixture("recorded-99999.json"), target));
		assertTrue(err().contains("규칙 경로에는 쓰지 않는다"), err());
		assertFalse(Files.exists(target));
	}

	@Test
	void 모르는_옵션과_명령은_종료_2() {
		assertEquals(2, run(Map.of(), "draft", "--model", "x"));
		assertEquals(2, run(Map.of(), "approve"));
		assertEquals(2, run(Map.of()));
	}

	@Test
	void text_명령은_쪽_표시_텍스트를_쓴다() throws IOException {
		Path target = tmp.resolve("t/out.txt");
		assertEquals(0, run(Map.of(), "text", "--source", TestPaths.fixture("synthetic-99999.txt").toString(), "--out",
				target.toString(), "--rules-dir", TestPaths.rulesDir().toString()));
		String text = Files.readString(target);
		assertTrue(text.startsWith("[[page 1]]\n"));
		assertTrue(text.contains("[[page 2]]\n할인한도"));
	}

}
