package com.beone.ruledraft;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * {@code <명령> --이름 값 ... [--live]} 형식의 단순 인자. 모르는 옵션은 오류다.
 */
final class CliArgs {

	private static final Set<String> FLAGS = Set.of("live");

	final String command;
	private final Map<String, String> values = new HashMap<>();
	private final Set<String> flags = new HashSet<>();

	private CliArgs(String command) {
		this.command = command;
	}

	static CliArgs parse(String[] args, Set<String> allowed) throws UsageException {
		if (args.length == 0) {
			throw new UsageException("명령이 없다");
		}
		CliArgs parsed = new CliArgs(args[0]);
		for (int i = 1; i < args.length; i++) {
			String a = args[i];
			if (!a.startsWith("--")) {
				throw new UsageException("알 수 없는 인자: " + a);
			}
			String name = a.substring(2);
			if (!allowed.contains(name)) {
				throw new UsageException("알 수 없는 옵션: " + a);
			}
			if (FLAGS.contains(name)) {
				parsed.flags.add(name);
				continue;
			}
			if (i + 1 >= args.length) {
				throw new UsageException("값이 없다: " + a);
			}
			parsed.values.put(name, args[++i]);
		}
		return parsed;
	}

	boolean flag(String name) {
		return flags.contains(name);
	}

	String require(String name) throws UsageException {
		String v = values.get(name);
		if (v == null || v.isBlank()) {
			throw new UsageException("--" + name + "가 필요하다");
		}
		return v;
	}

	Path path(String name) {
		String v = values.get(name);
		return v == null ? null : Path.of(v);
	}

	Path pathOr(String name, String fallback) {
		return Path.of(values.getOrDefault(name, fallback));
	}

}
