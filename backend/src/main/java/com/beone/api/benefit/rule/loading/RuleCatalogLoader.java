package com.beone.api.benefit.rule.loading;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.beone.api.benefit.card.MerchantClass;
import com.beone.api.benefit.rule.CardRuleVersion;
import com.beone.api.benefit.rule.RuleCatalog;

/**
 * 클래스패스의 {@code rules/<cardId>/<ruleVersion>.json}을 모두 읽어 {@link RuleCatalog}를 만든다.
 * 파일이 하나라도 잘못되면 예외로 끝난다. 일부만 읽은 규칙으로 계산하지 않기 위해서다.
 */
public final class RuleCatalogLoader {

	private static final String ALLOWLIST = "classpath:rules/merchant-classes.txt";

	private static final String RULES = "classpath*:rules/*/*.json";

	private RuleCatalogLoader() {
	}

	public static RuleCatalog load() {
		return new RuleCatalog(loadVersions());
	}

	static List<CardRuleVersion> loadVersions() {
		PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
		try {
			Set<MerchantClass> allowed = MerchantClassAllowlist.parse(content(resolver.getResource(ALLOWLIST)));
			RuleJsonReader reader = new RuleJsonReader(allowed);
			Resource[] resources = resolver.getResources(RULES);
			if (resources.length == 0) {
				throw new IllegalStateException("no rule files found");
			}
			List<CardRuleVersion> versions = new ArrayList<>();
			for (Resource resource : resources) {
				versions.add(readFile(reader, resource));
			}
			return List.copyOf(versions);
		}
		catch (IOException exception) {
			throw new UncheckedIOException("rule files could not be read", exception);
		}
	}

	private static CardRuleVersion readFile(RuleJsonReader reader, Resource resource) throws IOException {
		String name = resource.getFilename();
		String directory = parentDirectory(resource);
		try {
			CardRuleVersion version = reader.read(content(resource));
			requireLocation(version, directory, name);
			return version;
		}
		catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("rule file " + directory + "/" + name + ": " + exception.getMessage(), exception);
		}
	}

	// 파일 위치와 내용이 다르면 다른 카드의 규칙을 잘못 둔 것이므로 거부한다.
	private static void requireLocation(CardRuleVersion version, String directory, String name) {
		if (!version.cardId().toString().equals(directory) || !(version.ruleVersion() + ".json").equals(name)) {
			throw new IllegalArgumentException("file location does not match cardId and ruleVersion");
		}
	}

	private static String parentDirectory(Resource resource) throws IOException {
		String[] parts = resource.getURI().toString().split("/");
		return parts[parts.length - 2];
	}

	private static String content(Resource resource) throws IOException {
		return resource.getContentAsString(StandardCharsets.UTF_8);
	}

}
