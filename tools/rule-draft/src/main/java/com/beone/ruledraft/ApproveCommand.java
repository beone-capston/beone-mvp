package com.beone.ruledraft;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.beone.ruledraft.check.OutputGuard;
import com.beone.ruledraft.review.ApprovalRecorder;
import com.beone.ruledraft.review.ReviewEdit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * {@code approve}: 비교 결과와 사람의 수정 항목으로 승인 기록 {@code <카드>-<날짜>.json}을 남긴다.
 *
 * <ul>
 *   <li>근거 없음 항목이 남은 비교 결과, 형식이 틀린 수정 항목은 거부하고 아무것도 쓰지 않는다(종료 1).</li>
 *   <li>기존 기록은 덮어쓰지 않는다. 같은 날 기록이 있으면 {@code -2}부터 번호를 붙인다.</li>
 *   <li>규칙 파일에는 쓰지 않는다. 규칙 경로로 출력하려 하면 실패한다.</li>
 * </ul>
 */
final class ApproveCommand {

	static final int MAX_SAME_DAY = 99;

	private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
	private final PrintStream out;
	private final PrintStream err;
	private final Clock clock;

	ApproveCommand(PrintStream out, PrintStream err, Clock clock) {
		this.out = out;
		this.err = err;
		this.clock = clock;
	}

	int run(Path comparePath, Path editsPath, String reviewer, Path outDir, Path rulesDir) throws IOException {
		Path safeOut = OutputGuard.ensureWritable(outDir, List.of(rulesDir));
		String compareText = Files.readString(comparePath, StandardCharsets.UTF_8);
		JsonNode compare = mapper.readTree(compareText);
		String cardId = compare.path("cardId").asText("");
		if (!cardId.matches("\\d{5}")) {
			throw new IOException("비교 결과의 cardId가 숫자 5자리가 아니다");
		}
		List<ReviewEdit> edits = readEdits(editsPath);

		List<String> problems = ApprovalRecorder.problems(compare, reviewer, edits);
		if (!problems.isEmpty()) {
			err.println("승인 기록을 남기지 않았다(" + problems.size() + "건):");
			problems.forEach(p -> err.println("  - " + p));
			return 1;
		}

		Instant now = clock.instant();
		String body = mapper.writeValueAsString(ApprovalRecorder.record(mapper, compare,
				comparePath.getFileName().toString(), DraftCommand.sha256(compareText), reviewer.strip(), now, edits)) + "\n";
		Path written = writeNew(safeOut, rulesDir, cardId + "-" + LocalDate.ofInstant(now, clock.getZone()), body);
		out.println("승인 기록: " + written);
		out.println("수정 항목 " + edits.size() + "건. 규칙 파일은 바꾸지 않았다. VERIFIED 반영은 사람이 별도 커밋으로 한다.");
		return 0;
	}

	/**
	 * 수정 항목 파일: {@code {"edits": [{"path", "before", "after", "reason"}]}}.
	 * {@code before}·{@code after} 키를 생략하면 "그 경로가 없음"이고, JSON {@code null}은 null 값이다.
	 */
	List<ReviewEdit> readEdits(Path editsPath) throws IOException {
		JsonNode root = mapper.readTree(editsPath.toFile());
		JsonNode list = root == null ? null : root.get("edits");
		if (list == null || !list.isArray()) {
			throw new IOException("수정 항목 파일에 edits 배열이 없다(고친 것이 없으면 빈 배열)");
		}
		List<ReviewEdit> edits = new ArrayList<>();
		for (JsonNode e : list) {
			if (!e.isObject()) {
				throw new IOException("수정 항목은 객체여야 한다");
			}
			edits.add(new ReviewEdit(e.path("path").isTextual() ? e.get("path").asText() : null, e.get("before"),
					e.get("after"), e.path("reason").isTextual() ? e.get("reason").asText() : null));
		}
		return edits;
	}

	private static Path writeNew(Path dir, Path rulesDir, String base, String body) throws IOException {
		Files.createDirectories(dir);
		for (int i = 1; i <= MAX_SAME_DAY; i++) {
			String name = i == 1 ? base + ".json" : base + "-" + i + ".json";
			Path target = OutputGuard.ensureWritable(dir.resolve(name), List.of(rulesDir));
			try {
				Files.writeString(target, body, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW,
						StandardOpenOption.WRITE);
				return target;
			}
			catch (FileAlreadyExistsException e) {
				// 기존 기록은 그대로 두고 다음 번호를 쓴다
			}
		}
		throw new IOException("같은 날 승인 기록이 너무 많다: " + base);
	}

}
