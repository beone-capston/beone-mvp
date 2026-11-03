package com.beone.api.benefit.rule.loading;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.beone.api.benefit.card.MerchantClass;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 규칙 JSON, 허용 목록, 근거 표, 평가 fixture가 서로 어긋나지 않는지 파일을 직접 읽어 확인한다.
 */
class RuleDataConsistencyTest {

	private static final Path RULES = Path.of("src/main/resources/rules");

	private static final Path EVIDENCE = Path.of("../docs/rules");

	private static final Path FIXTURE = Path.of("../fixtures/evaluation-cases.json");

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	@Test
	void allowlistEqualsRuleCodesPlusOrdinaryDomestic() throws IOException {
		Set<String> ruleCodes = new HashSet<>();
		for (JsonNode rule : ruleFiles()) {
			collectCodes(rule, ruleCodes);
		}
		assertThat(ruleCodes).hasSize(49).doesNotContain("ORDINARY_DOMESTIC");

		Set<String> expected = new HashSet<>(ruleCodes);
		expected.add("ORDINARY_DOMESTIC");
		assertThat(allowlist()).isEqualTo(expected);
	}

	@Test
	void fixtureMerchantClassesAreInAllowlist() throws IOException {
		List<String> fixtureCodes = new ArrayList<>();
		collectFieldValues(MAPPER.readTree(Files.readString(FIXTURE)), "merchantClass", fixtureCodes);
		assertThat(fixtureCodes).isNotEmpty();
		assertThat(allowlist()).containsAll(fixtureCodes);
	}

	@Test
	void everyEvidenceIdExistsInItsCardEvidenceTable() throws IOException {
		for (JsonNode rule : ruleFiles()) {
			String card = rule.get("cardId").stringValue();
			String table = Files.readString(EVIDENCE.resolve(card + ".md"), StandardCharsets.UTF_8);
			List<String> evidence = new ArrayList<>();
			collectEvidence(rule, evidence);
			assertThat(evidence).as(card).isNotEmpty();
			for (String id : evidence) {
				assertThat(table).as(card + " " + id).contains("| " + id + " |");
			}
		}
	}

	@Test
	void everyServiceDeclaresExclusionKeysAndAtLeastOnePaymentMethod() throws IOException {
		for (JsonNode rule : ruleFiles()) {
			for (JsonNode service : rule.get("services")) {
				assertThat(service.has("excludedClasses")).isTrue();
				assertThat(service.has("unevaluatedExclusions")).isTrue();
				assertThat(service.get("paymentMethods").isEmpty()).isFalse();
			}
		}
	}

	private static Set<String> allowlist() throws IOException {
		return MerchantClassAllowlist.parse(Files.readString(RULES.resolve("merchant-classes.txt")))
			.stream()
			.map(MerchantClass::toString)
			.collect(Collectors.toSet());
	}

	private static List<JsonNode> ruleFiles() throws IOException {
		try (Stream<Path> files = Files.find(RULES, 2, (path, attributes) -> path.toString().endsWith(".json"))) {
			List<JsonNode> rules = new ArrayList<>();
			for (Path file : files.toList()) {
				rules.add(MAPPER.readTree(Files.readString(file, StandardCharsets.UTF_8)));
			}
			assertThat(rules).hasSize(4);
			return rules;
		}
	}

	private static void collectCodes(JsonNode rule, Set<String> codes) {
		JsonNode policy = rule.get("policy");
		addAll(policy.get("performanceExcludedClasses"), codes);
		addPairs(policy, codes);
		for (JsonNode service : rule.get("services")) {
			if (service.get("target").has("classes")) {
				addAll(service.get("target").get("classes"), codes);
			}
			addAll(service.get("excludedClasses"), codes);
			addPairs(service, codes);
		}
	}

	private static void addPairs(JsonNode owner, Set<String> codes) {
		if (owner.has("inclusionUnconfirmed")) {
			for (JsonNode pair : owner.get("inclusionUnconfirmed")) {
				codes.add(pair.get("merchantClass").stringValue());
				codes.add(pair.get("relatedClass").stringValue());
			}
		}
	}

	private static void addAll(JsonNode array, Set<String> codes) {
		array.forEach(item -> codes.add(item.stringValue()));
	}

	private static void collectFieldValues(JsonNode node, String field, List<String> values) {
		if (node.isObject()) {
			for (String key : node.propertyNames()) {
				JsonNode value = node.get(key);
				if (key.equals(field) && value.isString()) {
					values.add(value.stringValue());
				}
				collectFieldValues(value, field, values);
			}
		}
		else if (node.isArray()) {
			node.forEach(item -> collectFieldValues(item, field, values));
		}
	}

	private static void collectEvidence(JsonNode node, List<String> evidence) {
		if (node.isObject()) {
			for (String key : node.propertyNames()) {
				JsonNode value = node.get(key);
				if (key.equals("evidence") || key.equals("performanceExcludedClassesEvidence")) {
					if (value.isString()) {
						evidence.add(value.stringValue());
					}
					else {
						value.forEach(item -> evidence.add(item.stringValue()));
					}
				}
				else {
					collectEvidence(value, evidence);
				}
			}
		}
		else if (node.isArray()) {
			node.forEach(item -> collectEvidence(item, evidence));
		}
	}

}
