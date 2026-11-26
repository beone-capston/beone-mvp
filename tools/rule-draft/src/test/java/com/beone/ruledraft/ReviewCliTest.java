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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * draft → compare → approve 흐름. 녹화 응답과 합성 수동 규칙만 쓴다.
 * 모든 경우에 네트워크 전송이 만들어지지 않고, 기준 규칙 디렉터리 내용이 바뀌지 않아야 한다.
 */
class ReviewCliTest {

	/** 2026-11-25 00:30 KST = 2026-11-24 15:30 UTC. 기록 날짜는 서울 기준이다. */
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-24T15:30:00Z"), ZoneId.of("Asia/Seoul"));

	@TempDir
	Path tmp;

	private final ObjectMapper mapper = new ObjectMapper();
	private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
	private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
	private FailingTransport transport;
	private Map<String, String> rulesBefore;

	@BeforeEach
	void setUp() throws IOException {
		transport = new FailingTransport();
		rulesBefore = snapshot(TestPaths.rulesDir());
	}

	@AfterEach
	void invariants() throws IOException {
		assertEquals(0, transport.calls.get(), "실호출 0회");
		assertEquals(0, transport.created.get(), "전송 객체도 만들지 않는다");
		assertEquals(rulesBefore, snapshot(TestPaths.rulesDir()), "도구는 규칙 디렉터리를 바꾸지 않는다");
	}

	private static Map<String, String> snapshot(Path dir) throws IOException {
		Map<String, String> map = new TreeMap<>();
		try (Stream<Path> files = Files.walk(dir)) {
			for (Path p : files.filter(Files::isRegularFile).toList()) {
				map.put(dir.relativize(p).toString(), DraftCommand.sha256(Files.readString(p)));
			}
		}
		return map;
	}

	private int run(String... args) {
		return Main.run(args, Map.of(), transport.supplier(), new PrintStream(outBytes, true, StandardCharsets.UTF_8),
				new PrintStream(errBytes, true, StandardCharsets.UTF_8), CLOCK);
	}

	private String err() {
		return errBytes.toString(StandardCharsets.UTF_8);
	}

	private Path draftDir(Path recorded) {
		Path dir = tmp.resolve("draft");
		run("draft", "--source", TestPaths.fixture("synthetic-99999.txt").toString(), "--card", "99999", "--out",
				dir.toString(), "--rules-dir", TestPaths.rulesDir().toString(), "--recorded", recorded.toString());
		return dir;
	}

	/** 초안을 바탕으로 합성 수동 규칙을 만든다: 값 하나 다르게, 하나 빼고, 하나 더한다. */
	private Path manualFrom(Path draftFile) throws IOException {
		ObjectNode manual = (ObjectNode) mapper.readTree(draftFile.toFile());
		manual.put("status", "VERIFIED");
		ObjectNode service = (ObjectNode) manual.at("/services/0");
		service.put("maximumTargetAmount", 30000);
		service.remove("minimumAmount");
		service.put("onlyInManual", "x");
		Path file = tmp.resolve("manual-99999.json");
		Files.writeString(file, mapper.writeValueAsString(manual));
		return file;
	}

	private int compare(Path draftDir, Path manual, Path out) {
		return run("compare", "--draft", draftDir.resolve("99999.draft.json").toString(), "--evidence",
				draftDir.resolve("99999.evidence.json").toString(), "--manual", manual.toString(), "--out", out.toString(),
				"--rules-dir", TestPaths.rulesDir().toString());
	}

	private int approve(Path compareJson, Path edits, Path out) {
		return run("approve", "--compare", compareJson.toString(), "--edits", edits.toString(), "--reviewer", "검토자A",
				"--out", out.toString(), "--rules-dir", TestPaths.rulesDir().toString());
	}

	private Path edits(String json) throws IOException {
		Path file = tmp.resolve("edits.json");
		Files.writeString(file, json.replace('\'', '"'));
		return file;
	}

	private static final String EDITS = "{'edits': [{'path': '/services/0/maximumTargetAmount', 'before': 20000, 'after': 30000,"
			+ " 'reason': '원문 2쪽 한도 표 재확인'}]}";

	@Test
	void 비교_결과를_JSON과_표로_쓰고_승인_기록을_남긴다() throws IOException {
		Path draftDir = draftDir(TestPaths.fixture("recorded-99999.json"));
		Path manual = manualFrom(draftDir.resolve("99999.draft.json"));
		Path cmp = tmp.resolve("cmp");

		assertEquals(0, compare(draftDir, manual, cmp), err());
		JsonNode json = mapper.readTree(cmp.resolve("99999.compare.json").toFile());
		Map<String, String> status = new TreeMap<>();
		json.path("rows").forEach(r -> status.put(r.path("path").asText(), r.path("status").asText()));
		assertEquals("MISMATCH", status.get("/services/0/maximumTargetAmount"));
		assertEquals("MISSING_IN_MANUAL", status.get("/services/0/minimumAmount"));
		assertEquals("MISSING_IN_DRAFT", status.get("/services/0/onlyInManual"));
		assertEquals("MATCH", status.get("/cardId"));
		assertFalse(status.containsKey("/status"));
		assertEquals(0, json.at("/summary/NO_EVIDENCE").asInt());
		assertEquals(64, json.at("/manual/sha256").asText().length());
		String md = Files.readString(cmp.resolve("99999.compare.md"));
		assertTrue(md.contains("| 분류 | 경로 | 초안 | 수동 | 근거 |"));
		assertTrue(md.contains("| 불일치 | `/services/0/maximumTargetAmount` | 20000 | 30000 | 2쪽: "), md);

		Path reviews = tmp.resolve("reviews");
		assertEquals(0, approve(cmp.resolve("99999.compare.json"), edits(EDITS), reviews), err());
		JsonNode record = mapper.readTree(reviews.resolve("99999-2026-11-25.json").toFile());
		assertEquals("검토자A", record.path("reviewer").asText());
		assertEquals("2026-11-24T15:30:00Z", record.path("approvedAt").asText());
		assertEquals(30000, record.at("/edits/0/after").asInt());
		assertEquals(json.path("draftSha256").asText(), record.at("/compare/draftSha256").asText());
		assertEquals(DraftCommand.sha256(Files.readString(cmp.resolve("99999.compare.json"))),
				record.at("/compare/sha256").asText());
		assertFalse(record.toString().contains("\"VERIFIED\""), "승인 기록은 상태를 바꾸지 않는다");
	}

	@Test
	void 비교와_승인은_소수를_double로_뭉개지_않는다() throws IOException {
		Path draftDir = draftDir(TestPaths.fixture("recorded-99999.json"));
		Path draftFile = draftDir.resolve("99999.draft.json");
		String key = "\"maximumTargetAmount\":30000";
		String manualText = Files.readString(manualFrom(draftFile));
		assertTrue(manualText.contains(key), manualText);
		Path same = tmp.resolve("manual-same.json");
		Path close = tmp.resolve("manual-close.json");
		// 초안 값 20000. 20000.000000000001은 double로 읽으면 20000.0이 되어 일치로 보인다.
		Files.writeString(same, manualText.replace(key, "\"maximumTargetAmount\":20000.0"));
		Files.writeString(close, manualText.replace(key, "\"maximumTargetAmount\":20000.000000000001"));

		assertEquals(0, compare(draftDir, same, tmp.resolve("cmp-same")), err());
		assertEquals("MATCH", rowStatus(tmp.resolve("cmp-same"), "/services/0/maximumTargetAmount"));
		Path cmp = tmp.resolve("cmp-close");
		assertEquals(0, compare(draftDir, close, cmp), err());
		assertEquals("MISMATCH", rowStatus(cmp, "/services/0/maximumTargetAmount"));

		Path compareJson = cmp.resolve("99999.compare.json");
		assertEquals(1, approve(compareJson, edits(EDITS.replace("'before': 20000,", "'before': 20000.000000000001,")),
				tmp.resolve("rev-close")));
		assertTrue(err().contains("수정 전 값이 초안 값과 다르다: /services/0/maximumTargetAmount"), err());
		assertEquals(0, approve(compareJson, edits(EDITS.replace("'before': 20000,", "'before': 20000.0,")),
				tmp.resolve("rev-same")), err());
	}

	private String rowStatus(Path cmpDir, String path) throws IOException {
		for (JsonNode r : mapper.readTree(cmpDir.resolve("99999.compare.json").toFile()).path("rows")) {
			if (path.equals(r.path("path").asText())) {
				return r.path("status").asText();
			}
		}
		return null;
	}

	@Test
	void 같은_날_다시_승인하면_기존_기록을_두고_번호를_붙인다() throws IOException {
		Path draftDir = draftDir(TestPaths.fixture("recorded-99999.json"));
		Path cmp = tmp.resolve("cmp");
		compare(draftDir, manualFrom(draftDir.resolve("99999.draft.json")), cmp);
		Path reviews = tmp.resolve("reviews");

		assertEquals(0, approve(cmp.resolve("99999.compare.json"), edits(EDITS), reviews));
		String first = Files.readString(reviews.resolve("99999-2026-11-25.json"));
		assertEquals(0, approve(cmp.resolve("99999.compare.json"), edits("{'edits': []}"), reviews));

		assertEquals(first, Files.readString(reviews.resolve("99999-2026-11-25.json")));
		assertEquals(0, mapper.readTree(reviews.resolve("99999-2026-11-25-2.json").toFile()).path("edits").size());
	}

	@Test
	void 근거_없는_초안은_비교가_종료_3이고_승인은_거부되어_아무것도_쓰지_않는다() throws IOException {
		Path recorded = tmp.resolve("recorded.json");
		String text = Files.readString(TestPaths.fixture("recorded-99999.json"));
		ObjectNode root = (ObjectNode) mapper.readTree(text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1));
		((ObjectNode) root.get("evidence")).remove("/services/0");
		Files.writeString(recorded, mapper.writeValueAsString(root));
		Path draftDir = draftDir(recorded);
		Path cmp = tmp.resolve("cmp");

		assertEquals(3, compare(draftDir, manualFrom(draftDir.resolve("99999.draft.json")), cmp));
		JsonNode json = mapper.readTree(cmp.resolve("99999.compare.json").toFile());
		assertTrue(json.at("/summary/NO_EVIDENCE").asInt() > 0);

		Path reviews = tmp.resolve("reviews");
		assertEquals(1, approve(cmp.resolve("99999.compare.json"), edits(EDITS), reviews));
		assertTrue(err().contains("근거 없음: /services/0/maximumTargetAmount"), err());
		assertFalse(Files.exists(reviews.resolve("99999-2026-11-25.json")));
	}

	@Test
	void 초안이_근거_파일의_짝이_아니면_비교하지_않는다() throws IOException {
		Path draftDir = draftDir(TestPaths.fixture("recorded-99999.json"));
		Path manual = manualFrom(draftDir.resolve("99999.draft.json"));
		Path draftFile = draftDir.resolve("99999.draft.json");
		Files.writeString(draftFile, Files.readString(draftFile).replace("20000", "20001"));
		Path cmp = tmp.resolve("cmp");

		assertEquals(1, compare(draftDir, manual, cmp));
		assertTrue(err().contains("draftSha256"), err());
		assertFalse(Files.exists(cmp.resolve("99999.compare.json")));
	}

	@Test
	void 수정_전_값이_초안과_다르면_승인을_거부한다() throws IOException {
		Path draftDir = draftDir(TestPaths.fixture("recorded-99999.json"));
		Path cmp = tmp.resolve("cmp");
		compare(draftDir, manualFrom(draftDir.resolve("99999.draft.json")), cmp);
		Path reviews = tmp.resolve("reviews");

		assertEquals(1, approve(cmp.resolve("99999.compare.json"), edits(EDITS.replace("20000", "1")), reviews));
		assertTrue(err().contains("수정 전 값이 초안 값과 다르다: /services/0/maximumTargetAmount"), err());
		assertFalse(Files.exists(reviews));
	}

	@Test
	void 비교와_승인_모두_규칙_경로에_쓰려_하면_종료_1() throws IOException {
		Path draftDir = draftDir(TestPaths.fixture("recorded-99999.json"));
		Path manual = manualFrom(draftDir.resolve("99999.draft.json"));
		Path cmp = tmp.resolve("cmp");
		compare(draftDir, manual, cmp);

		// 기준 표본 디렉터리 아래와, 이름이 backend/src/main/resources/rules인 경로 아래 둘 다 막는다
		Path fakeRules = Files.createDirectories(tmp.resolve("repo/backend/src/main/resources/rules"));
		for (Path target : List.of(TestPaths.rulesDir().resolve("99999"), fakeRules)) {
			assertEquals(1, compare(draftDir, manual, target), err());
			assertEquals(1, approve(cmp.resolve("99999.compare.json"), edits(EDITS), target), err());
		}
		try (Stream<Path> files = Files.list(fakeRules)) {
			assertEquals(0, files.count());
		}
	}

	@Test
	void 승인에_필요한_인자가_없으면_종료_2() {
		assertEquals(2, run("approve", "--compare", "x.json", "--edits", "e.json"));
		assertTrue(err().contains("--reviewer가 필요하다"));
	}

}
