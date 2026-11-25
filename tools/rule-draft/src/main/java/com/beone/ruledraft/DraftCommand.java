package com.beone.ruledraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.beone.ruledraft.check.DraftResponse;
import com.beone.ruledraft.check.EvidenceChecker;
import com.beone.ruledraft.check.EvidenceReport;
import com.beone.ruledraft.check.OutputGuard;
import com.beone.ruledraft.check.RuleKeySchema;
import com.beone.ruledraft.model.DraftRequest;
import com.beone.ruledraft.model.ModelException;
import com.beone.ruledraft.model.RuleDraftModel;
import com.beone.ruledraft.source.SourceDocument;
import com.beone.ruledraft.source.SourceLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * {@code draft} 명령: 원문 → 모델 → 스키마 검사 → 근거 검사 → 초안·근거 JSON 출력.
 *
 * <p>스키마가 맞지 않으면 아무것도 쓰지 않고 실패한다(종료 1). 근거가 빠지거나 확인되지 않은 값이 있으면
 * 파일은 쓰되 종료 3으로 알린다. 비교기(D41-2)가 그 값을 '근거 없음'으로 분류한다.
 */
final class DraftCommand {

	static final String DRAFT_STATUS = "AI_DRAFT";

	private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
	private final PrintStream out;
	private final PrintStream err;

	DraftCommand(PrintStream out, PrintStream err) {
		this.out = out;
		this.err = err;
	}

	int run(String cardId, Path sourcePath, Path outDir, Path rulesDir, RuleDraftModel model) throws IOException, ModelException {
		if (!cardId.matches("\\d{5}")) {
			throw new IOException("--card는 숫자 5자리다");
		}
		OutputGuard.ensureWritable(outDir, List.of(rulesDir));
		RuleKeySchema schema = RuleKeySchema.fromSamples(rulesDir);
		SourceDocument source = SourceLoader.load(sourcePath);

		String raw = model.draft(new DraftRequest(cardId, source, instructions(schema, rulesDir, cardId)));
		DraftResponse response = DraftResponse.parse(raw, mapper);
		ObjectNode draft = response.draft();

		String modelStatus = draft.path("status").asText(null);
		draft.put("status", DRAFT_STATUS);

		List<String> problems = new ArrayList<>(schema.validate(draft));
		if (!cardId.equals(draft.path("cardId").asText(null))) {
			problems.add("cardId 불일치: --card " + cardId);
		}
		if (!problems.isEmpty()) {
			err.println("스키마 불일치로 초안을 쓰지 않았다(" + problems.size() + "건):");
			problems.forEach(p -> err.println("  - " + p));
			return 1;
		}

		EvidenceReport report = EvidenceChecker.check(draft, response.evidence(), source);
		Path draftFile = outDir.resolve(cardId + ".draft.json");
		Path evidenceFile = outDir.resolve(cardId + ".evidence.json");
		OutputGuard.ensureWritable(draftFile, List.of(rulesDir));
		OutputGuard.ensureWritable(evidenceFile, List.of(rulesDir));
		Files.createDirectories(outDir);
		Files.writeString(draftFile, mapper.writeValueAsString(draft) + "\n", StandardCharsets.UTF_8);
		Files.writeString(evidenceFile,
				mapper.writeValueAsString(evidenceJson(cardId, source, model, modelStatus, report)) + "\n",
				StandardCharsets.UTF_8);

		out.println("초안: " + draftFile);
		out.println("근거: " + evidenceFile);
		out.println("확인된 근거 " + report.verified().size() + "건, 근거 누락 값 " + report.missing().size()
				+ "건, 확인 실패 근거 " + report.unverified().size() + "건");
		if (!report.complete()) {
			err.println("근거가 빠지거나 확인되지 않은 항목이 있다. 비교·승인 전에 사람이 원문과 대조해야 한다.");
			return 3;
		}
		return 0;
	}

	private ObjectNode evidenceJson(String cardId, SourceDocument source, RuleDraftModel model, String modelStatus,
			EvidenceReport report) {
		ObjectNode root = mapper.createObjectNode();
		root.put("cardId", cardId);
		ObjectNode src = root.putObject("source");
		src.put("fileName", source.fileName());
		src.put("sha256", source.sha256());
		src.put("pageCount", source.pages().size());
		root.put("model", model.describe());
		root.put("status", DRAFT_STATUS);
		if (!Objects.equals(modelStatus, DRAFT_STATUS)) {
			root.put("modelStatus", modelStatus);
		}
		ObjectNode fields = root.putObject("fields");
		report.verified().forEach((path, ev) -> {
			ObjectNode f = fields.putObject(path);
			f.put("page", ev.page());
			f.put("quote", ev.quote());
		});
		ArrayNode missing = root.putArray("missing");
		report.missing().forEach(missing::add);
		ArrayNode unverified = root.putArray("unverified");
		report.unverified().forEach(unverified::add);
		return root;
	}

	/**
	 * 지시문 = 고정 안내 + 허용 구조 경로 + 형식 예시 1개.
	 * 형식 예시는 대상 카드가 아닌 다른 카드의 규칙을 쓴다(대상 카드의 수동 규칙을 보여 주면 비교가 무의미해진다).
	 */
	String instructions(RuleKeySchema schema, Path rulesDir, String cardId) throws IOException {
		String base;
		try (InputStream in = Objects.requireNonNull(DraftCommand.class.getResourceAsStream("/draft-instructions.txt"))) {
			base = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		Path example = exampleRule(rulesDir, cardId);
		JsonNode exampleJson = mapper.readTree(example.toFile());
		return base + "\n허용 구조 경로(경로 [값 종류]):\n" + schema.describeShapes() + "\n형식 예시(다른 카드 "
				+ exampleJson.path("cardId").asText() + "의 규칙):\n" + mapper.writeValueAsString(exampleJson) + "\n";
	}

	static Path exampleRule(Path rulesDir, String cardId) throws IOException {
		return RuleKeySchema.ruleFiles(rulesDir).stream()
			.filter(p -> !p.getFileName().toString().startsWith(cardId + "-"))
			.findFirst()
			.orElseThrow(() -> new IOException("형식 예시로 쓸 다른 카드 규칙이 없다"));
	}

}
