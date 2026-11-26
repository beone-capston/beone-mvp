package com.beone.ruledraft;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.beone.ruledraft.check.Evidence;
import com.beone.ruledraft.check.OutputGuard;
import com.beone.ruledraft.review.CompareReport;
import com.beone.ruledraft.review.CompareRow;
import com.beone.ruledraft.review.ReviewJson;
import com.beone.ruledraft.review.RuleComparator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@code compare}: 초안·근거·수동 규칙을 읽어 비교 결과를 {@code <카드>.compare.json}·{@code <카드>.compare.md}로 쓴다.
 *
 * <p>근거는 {@code draft}가 원문과 대조해 확인한 항목({@code fields})만 쓴다. 근거 파일의 {@code draftSha256}이
 * 초안 파일 내용과 다르면 다른 실행의 짝이므로 비교하지 않는다. 규칙 파일은 읽기만 한다.
 */
final class CompareCommand {

	private final ObjectMapper mapper = ReviewJson.mapper();
	private final PrintStream out;
	private final PrintStream err;

	CompareCommand(PrintStream out, PrintStream err) {
		this.out = out;
		this.err = err;
	}

	int run(Path draftPath, Path evidencePath, Path manualPath, Path outDir, Path rulesDir) throws IOException {
		Path safeOut = OutputGuard.ensureWritable(outDir, List.of(rulesDir));
		String draftText = Files.readString(draftPath, StandardCharsets.UTF_8);
		JsonNode draft = mapper.readTree(draftText);
		JsonNode evidence = mapper.readTree(evidencePath.toFile());
		String manualText = Files.readString(manualPath, StandardCharsets.UTF_8);
		JsonNode manual = mapper.readTree(manualText);

		String cardId = draft.path("cardId").asText("");
		if (!cardId.matches("\\d{5}")) {
			throw new IOException("초안의 cardId가 숫자 5자리가 아니다");
		}
		if (!cardId.equals(evidence.path("cardId").asText()) || !cardId.equals(manual.path("cardId").asText())) {
			throw new IOException("초안·근거·수동 규칙의 cardId가 같지 않다");
		}
		String draftSha = DraftCommand.sha256(draftText);
		if (!draftSha.equals(evidence.path("draftSha256").asText())) {
			throw new IOException("근거 파일의 draftSha256이 초안 파일과 다르다(같은 draft 실행의 짝이 아님)");
		}

		List<CompareRow> rows = RuleComparator.compare(draft, manual, verifiedEvidence(evidence));
		CompareReport report = new CompareReport(cardId, draftSha, manualPath.getFileName().toString(),
				DraftCommand.sha256(manualText), rows);

		Path jsonFile = OutputGuard.ensureWritable(safeOut.resolve(cardId + ".compare.json"), List.of(rulesDir));
		Path mdFile = OutputGuard.ensureWritable(safeOut.resolve(cardId + ".compare.md"), List.of(rulesDir));
		DraftCommand.writePair(safeOut, jsonFile, mapper.writeValueAsString(report.toJson(mapper)) + "\n", mdFile,
				report.toMarkdown());

		out.println("비교 JSON: " + jsonFile);
		out.println("비교 표: " + mdFile);
		StringBuilder line = new StringBuilder();
		report.summary().forEach((s, n) -> line.append(line.isEmpty() ? "" : ", ").append(s.label()).append(' ').append(n));
		out.println(line);
		if (!report.noEvidence().isEmpty()) {
			err.println("근거 없음 항목이 " + report.noEvidence().size() + "건 있다. 승인(approve) 전에 원문 근거를 채워 draft부터 다시 만든다.");
			return 3;
		}
		return 0;
	}

	private static Map<String, Evidence> verifiedEvidence(JsonNode evidence) throws IOException {
		Map<String, Evidence> map = new TreeMap<>();
		JsonNode fields = evidence.path("fields");
		if (!fields.isObject()) {
			throw new IOException("근거 파일에 fields가 없다");
		}
		for (Map.Entry<String, JsonNode> e : fields.properties()) {
			JsonNode v = e.getValue();
			if (!v.path("page").canConvertToInt() || !v.path("quote").isTextual()) {
				throw new IOException("근거 형식 오류: " + e.getKey());
			}
			map.put(e.getKey(), new Evidence(v.path("page").asInt(), v.path("quote").asText()));
		}
		return map;
	}

}
