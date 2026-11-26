package com.beone.ruledraft;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import com.beone.ruledraft.check.OutputGuard;
import com.beone.ruledraft.model.HttpTransport;
import com.beone.ruledraft.model.JdkHttpTransport;
import com.beone.ruledraft.model.ModelException;
import com.beone.ruledraft.model.RuleDraftModel;
import com.beone.ruledraft.source.SourceLoader;

/**
 * 약관 초안 오프라인 도구 진입점(결정 41, PRD FR-08). 서비스 API·배포 대상이 아니다.
 *
 * <p>종료 코드: 0 성공, 1 실패(스키마 불일치·입출력·모델 오류·승인 거부), 2 인자·실행 조건 오류,
 * 3 결과는 썼으나 근거 누락 있음(draft: 근거 누락·확인 실패, compare: 근거 없음 항목).
 */
public final class Main {

	static final String DEFAULT_RULES_DIR = "../../backend/src/main/resources/rules";

	/** 승인 기록 기본 위치(저장소 docs/rules/reviews, 이 디렉터리 기준). */
	static final String DEFAULT_REVIEWS_DIR = "../../docs/rules/reviews";

	/** 승인 기록 날짜의 기준 시간대. */
	static final ZoneId REVIEW_ZONE = ZoneId.of("Asia/Seoul");

	static final String USAGE = """
			사용법:
			  text  --source <상품설명서.pdf> --out <파일.txt> [--rules-dir <규칙 디렉터리>]
			  draft --source <pdf 또는 txt> --card <카드ID 5자리> --out <디렉터리>
			        (--recorded <녹화 응답 파일> | --live) [--rules-dir <규칙 디렉터리>]
			  compare --draft <카드.draft.json> --evidence <카드.evidence.json> --manual <수동 규칙 JSON>
			          --out <디렉터리> [--rules-dir <규칙 디렉터리>]
			  approve --compare <카드.compare.json> --edits <수정 항목 JSON> --reviewer <승인자>
			          [--out <디렉터리, 기본 %s>] [--rules-dir <규칙 디렉터리>]
			  --live는 환경 변수 %s가 있을 때만 동작한다.
			  도구는 규칙 파일에 쓰지 않는다. VERIFIED 반영은 사람이 별도 커밋으로 한다.
			""".formatted(Main.DEFAULT_REVIEWS_DIR, LiveGate.KEY_ENV);

	private Main() {
	}

	public static void main(String[] args) {
		System.exit(run(args, System.getenv(), JdkHttpTransport::new, System.out, System.err));
	}

	static int run(String[] args, Map<String, String> env, Supplier<HttpTransport> transport, PrintStream out,
			PrintStream err) {
		return run(args, env, transport, out, err, Clock.system(REVIEW_ZONE));
	}

	static int run(String[] args, Map<String, String> env, Supplier<HttpTransport> transport, PrintStream out,
			PrintStream err, Clock clock) {
		try {
			if (args.length == 0) {
				throw new UsageException("명령이 없다");
			}
			return switch (args[0]) {
				case "text" -> text(CliArgs.parse(args, Set.of("source", "out", "rules-dir")), out);
				case "draft" -> draft(CliArgs.parse(args, Set.of("source", "card", "out", "rules-dir", "recorded", "live")),
						env, transport, out, err);
				case "compare" -> compare(CliArgs.parse(args, Set.of("draft", "evidence", "manual", "out", "rules-dir")), out,
						err);
				case "approve" -> approve(CliArgs.parse(args, Set.of("compare", "edits", "reviewer", "out", "rules-dir")),
						out, err, clock);
				default -> throw new UsageException("알 수 없는 명령: " + args[0]);
			};
		}
		catch (UsageException e) {
			err.println(e.getMessage());
			err.print(USAGE);
			return 2;
		}
		catch (IOException | ModelException | RuntimeException e) {
			// 메시지만 출력한다. 키는 어떤 예외 메시지에도 넣지 않는다.
			err.println("실패: " + e.getMessage());
			return 1;
		}
	}

	private static int text(CliArgs a, PrintStream out) throws UsageException, IOException {
		Path source = Path.of(a.require("source"));
		// 검사한 경로(링크·.. 해석 결과)에만 쓴다
		Path target = OutputGuard.ensureWritable(Path.of(a.require("out")), List.of(a.pathOr("rules-dir", DEFAULT_RULES_DIR)));
		Path parent = target.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.writeString(target, SourceLoader.load(source).toMarkedText(), StandardCharsets.UTF_8);
		out.println("텍스트: " + target);
		return 0;
	}

	private static int draft(CliArgs a, Map<String, String> env, Supplier<HttpTransport> transport, PrintStream out,
			PrintStream err) throws UsageException, IOException, ModelException {
		// 실행 조건을 가장 먼저 확인한다. 조건이 맞지 않으면 파일을 읽거나 쓰지 않는다.
		RuleDraftModel model = LiveGate.select(a.path("recorded"), a.flag("live"), env, transport);
		String card = a.require("card");
		Path source = Path.of(a.require("source"));
		Path outDir = Path.of(a.require("out"));
		Path rulesDir = a.pathOr("rules-dir", DEFAULT_RULES_DIR);
		return new DraftCommand(out, err).run(card, source, outDir, rulesDir, model);
	}

	private static int compare(CliArgs a, PrintStream out, PrintStream err) throws UsageException, IOException {
		return new CompareCommand(out, err).run(Path.of(a.require("draft")), Path.of(a.require("evidence")),
				Path.of(a.require("manual")), Path.of(a.require("out")), a.pathOr("rules-dir", DEFAULT_RULES_DIR));
	}

	private static int approve(CliArgs a, PrintStream out, PrintStream err, Clock clock)
			throws UsageException, IOException {
		String reviewer = a.require("reviewer");
		return new ApproveCommand(out, err, clock).run(Path.of(a.require("compare")), Path.of(a.require("edits")),
				reviewer, a.pathOr("out", DEFAULT_REVIEWS_DIR), a.pathOr("rules-dir", DEFAULT_RULES_DIR));
	}

}
