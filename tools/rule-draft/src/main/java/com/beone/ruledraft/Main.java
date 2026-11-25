package com.beone.ruledraft;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * <p>종료 코드: 0 성공, 1 실패(스키마 불일치·입출력·모델 오류), 2 인자·실행 조건 오류, 3 초안은 썼으나 근거 누락 있음.
 */
public final class Main {

	static final String DEFAULT_RULES_DIR = "../../backend/src/main/resources/rules";

	static final String USAGE = """
			사용법:
			  text  --source <상품설명서.pdf> --out <파일.txt> [--rules-dir <규칙 디렉터리>]
			  draft --source <pdf 또는 txt> --card <카드ID 5자리> --out <디렉터리>
			        (--recorded <녹화 응답 파일> | --live) [--rules-dir <규칙 디렉터리>]
			  --live는 환경 변수 %s가 있을 때만 동작한다.
			""".formatted(LiveGate.KEY_ENV);

	private Main() {
	}

	public static void main(String[] args) {
		System.exit(run(args, System.getenv(), JdkHttpTransport::new, System.out, System.err));
	}

	static int run(String[] args, Map<String, String> env, Supplier<HttpTransport> transport, PrintStream out,
			PrintStream err) {
		try {
			if (args.length == 0) {
				throw new UsageException("명령이 없다");
			}
			return switch (args[0]) {
				case "text" -> text(CliArgs.parse(args, Set.of("source", "out", "rules-dir")), out);
				case "draft" -> draft(CliArgs.parse(args, Set.of("source", "card", "out", "rules-dir", "recorded", "live")),
						env, transport, out, err);
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

}
