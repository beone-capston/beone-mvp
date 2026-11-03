package com.beone.api.benefit.rule.loading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.beone.api.benefit.card.MerchantClass;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 잘못된 규칙 JSON을 기본값 없이 거부하는지 확인한다. 정상 문서 한 개를 고쳐 가며 시험한다.
 */
class RuleJsonReaderTest {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private static final Set<MerchantClass> ALLOWED = Set.of(MerchantClass.of("KNOWN"), MerchantClass.of("OTHER"),
			MerchantClass.of("PERF"), MerchantClass.of("RELATED"));

	private static final String VALID = """
			{
			  "cardId": "00001",
			  "ruleVersion": "V1",
			  "status": "VERIFIED",
			  "evaluationOnly": false,
			  "validity": {"from": "2026-01-01", "to": null},
			  "sources": [{"uri": "https://example.com/terms.pdf", "locator": "1쪽"}],
			  "policy": {
			    "performanceWindow": {"value": "PREVIOUS_CALENDAR_MONTH", "evidence": "E1"},
			    "performanceDateBasis": {"unresolved": "표기 없음"},
			    "cancellationAttribution": {"unresolved": "표기 없음"},
			    "performanceExcludedNatures": ["TAX"],
			    "performanceExcludedClasses": ["PERF"],
			    "performanceExcludedClassesEvidence": "E1",
			    "excludesBenefitedSales": {"value": true, "evidence": "E1"},
			    "limitPeriod": {"unresolved": "표기 없음"},
			    "limitDeductionOrder": {"unresolved": "표기 없음"},
			    "newCardGrace": {"value": null, "evidence": "E1"}
			  },
			  "limitBuckets": [
			    {"id": "B", "tiers": [{"minimumPerformance": 100, "monthlyCap": 10}], "parent": null, "evidence": "E1"}
			  ],
			  "services": [{
			    "id": "S",
			    "kind": "BILLING_DISCOUNT",
			    "target": {"type": "SPECIFIC_CLASSES", "classes": ["KNOWN"]},
			    "paymentMethods": ["DIRECT"],
			    "excludedClasses": [],
			    "unevaluatedExclusions": [],
			    "minimumAmount": 0,
			    "maximumTargetAmount": null,
			    "tiers": [{"minimumPerformance": 100, "calculation": {"value": {"type": "RATE", "basisPoints": 100}, "evidence": "E1"}}],
			    "limitBuckets": ["B"],
			    "requiresSelection": false,
			    "stackableWith": {"value": [], "evidence": "E1"},
			    "fractionalWon": {"value": "TRUNCATE", "basis": "USER_DECISION", "decision": "D-03"},
			    "evidence": ["E1"]
			  }]
			}
			""";

	private final RuleJsonReader reader = new RuleJsonReader(ALLOWED);

	@Test
	void readsValidDocument() {
		var rule = reader.read(VALID);

		assertThat(rule.services()).singleElement().satisfies(service -> {
			assertThat(service.details().unevaluatedExclusions()).isEmpty();
			assertThat(service.details().fractionalWonBasis()).isPresent();
		});
		assertThat(rule.policy().newCardGrace().resolvedValue()).isEmpty();
	}

	@Test
	void rejectsMalformedJsonWithoutEchoingInput() {
		assertThatThrownBy(() -> reader.read("{\"cardId\": \"SECRET-LIKE\""))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageNotContaining("SECRET-LIKE");
	}

	@Test
	void rejectsDuplicateKeys() {
		assertRejected(VALID.replaceFirst("\"cardId\": \"00001\",", "\"cardId\": \"00001\", \"cardId\": \"00002\","), "malformed");
	}

	@Test
	void rejectsUnknownKeysAtEveryLevel() {
		assertRejected(root -> root.put("memo", "x"), "unknown key 'memo'");
		assertRejected(root -> policy(root).put("note", "x"), "unknown key 'note'");
		assertRejected(root -> service(root).put("note", "x"), "unknown key 'note'");
		assertRejected(root -> ((ObjectNode) service(root).get("target")).put("note", "x"), "unknown key 'note'");
		assertRejected(root -> ((ObjectNode) service(root).get("stackableWith")).put("note", "x"), "unknown key 'note'");
	}

	@Test
	void rejectsMissingRequiredKeysInsteadOfDefaulting() {
		for (String key : new String[] { "paymentMethods", "excludedClasses", "unevaluatedExclusions", "maximumTargetAmount",
				"stackableWith", "fractionalWon", "evidence", "requiresSelection" }) {
			assertRejected(root -> service(root).remove(key), "missing key '" + key + "'");
		}
		assertRejected(root -> policy(root).remove("newCardGrace"), "missing key 'newCardGrace'");
		assertRejected(root -> root.remove("limitBuckets"), "missing key 'limitBuckets'");
	}

	@Test
	void rejectsEmptyPaymentMethods() {
		assertRejected(root -> service(root).set("paymentMethods", MAPPER.createArrayNode()), "");
	}

	@Test
	void rejectsCodesOutsideAllowlistEverywhere() {
		assertRejected(root -> codes(service(root).get("target").get("classes")).add("TYPO"), "allowlist");
		assertRejected(root -> codes(service(root).get("excludedClasses")).add("TYPO"), "allowlist");
		assertRejected(root -> codes(policy(root).get("performanceExcludedClasses")).add("TYPO"), "allowlist");
		assertRejected(root -> service(root).set("inclusionUnconfirmed", pairs("TYPO", "KNOWN")), "allowlist");
	}

	@Test
	void rejectsInclusionPairsThatDoNotFitTheService() {
		assertRejected(root -> service(root).set("inclusionUnconfirmed", pairs("OTHER", "RELATED")), "unrelated class");
		assertRejected(root -> service(root).set("inclusionUnconfirmed", pairs("KNOWN", "KNOWN")), "two different classes");
		assertRejected(root -> {
			codes(service(root).get("excludedClasses")).add("OTHER");
			service(root).set("inclusionUnconfirmed", pairs("OTHER", "KNOWN"));
		}, "known class");
		assertRejected(root -> policy(root).set("inclusionUnconfirmed", pairs("OTHER", "RELATED")), "unrelated class");
		assertRejected(root -> policy(root).set("inclusionUnconfirmed", pairs("PERF", "PERF")), "two different classes");
	}

	@Test
	void acceptsInclusionPairRelatedToTargetOrExclusion() {
		String pairOnTarget = mutate(root -> service(root).set("inclusionUnconfirmed", pairs("OTHER", "KNOWN")));
		assertThat(reader.read(pairOnTarget).services().getFirst().details().inclusionUnconfirmed()).hasSize(1);

		String pairOnExclusion = mutate(root -> {
			codes(service(root).get("excludedClasses")).add("RELATED");
			service(root).set("inclusionUnconfirmed", pairs("OTHER", "RELATED"));
		});
		assertThat(reader.read(pairOnExclusion).services().getFirst().details().inclusionUnconfirmed()).hasSize(1);
	}

	@Test
	void rejectsTermsThatMixValueAndUnresolved() {
		assertRejected(root -> ((ObjectNode) service(root).get("stackableWith")).put("unresolved", "x"), "unknown key");
	}

	@Test
	void rejectsResolvedTermsWithoutEvidence() {
		assertRejected(root -> ((ObjectNode) service(root).get("tiers").get(0).get("calculation")).remove("evidence"),
				"missing key 'evidence'");
		assertRejected(root -> ((ObjectNode) service(root).get("stackableWith")).remove("evidence"), "missing key 'evidence'");
		assertRejected(root -> ((ObjectNode) policy(root).get("performanceWindow")).remove("evidence"), "missing key 'evidence'");
		assertRejected(root -> ((ObjectNode) policy(root).get("excludesBenefitedSales")).remove("evidence"), "missing key 'evidence'");
		assertRejected(root -> ((ObjectNode) policy(root).get("newCardGrace")).remove("evidence"), "missing key 'evidence'");
	}

	@Test
	void rejectsResolvedFractionalWonWithoutBasis() {
		assertRejected(root -> ((ObjectNode) service(root).get("fractionalWon")).remove("basis"), "missing key 'basis'");
		assertRejected(root -> ((ObjectNode) service(root).get("fractionalWon")).remove("decision"), "missing key 'decision'");
	}

	@Test
	void keepsUnresolvedTermUnresolved() {
		var rule = reader.read(mutate(root -> service(root).set("fractionalWon", MAPPER.createObjectNode().put("unresolved", "표기 없음"))));
		var service = rule.services().getFirst();
		assertThat(service.fractionalWon().isResolved()).isFalse();
		assertThat(service.details().fractionalWonBasis()).isEmpty();
	}

	@Test
	void rejectsWrongTypesAndFormats() {
		assertRejected(root -> service(root).put("minimumAmount", "0"), "whole won");
		assertRejected(root -> service(root).put("minimumAmount", 1.5), "whole won");
		assertRejected(root -> service(root).put("requiresSelection", "false"), "boolean");
		assertRejected(root -> service(root).put("kind", "UNKNOWN"), "unknown value");
		assertRejected(root -> ((ObjectNode) root.get("validity")).put("from", "2026/01/01"), "ISO date");
		assertRejected(root -> ((ArrayNode) service(root).get("evidence")).add("근거"), "evidence id");
		assertRejected(root -> ((ObjectNode) service(root).get("target")).put("type", "NOPE"), "unknown target type");
		assertRejected(root -> policy(root).put("performanceExcludedClassesEvidence", "bad"), "evidence id");
	}

	@Test
	void rejectsDuplicateListValues() {
		assertRejected(root -> codes(service(root).get("target").get("classes")).add("KNOWN"), "duplicate");
		assertRejected(root -> codes(service(root).get("paymentMethods")).add("DIRECT"), "duplicate");
	}

	@Test
	void rejectsReferencesTheModelForbids() {
		assertRejected(root -> codes(service(root).get("limitBuckets")).add("MISSING"), "");
	}

	private void assertRejected(Consumer<ObjectNode> change, String message) {
		assertRejected(mutate(change), message);
	}

	private void assertRejected(String json, String message) {
		assertThatThrownBy(() -> reader.read(json)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
	}

	private static String mutate(Consumer<ObjectNode> change) {
		ObjectNode root = (ObjectNode) MAPPER.readTree(VALID);
		change.accept(root);
		return MAPPER.writeValueAsString(root);
	}

	private static ObjectNode policy(ObjectNode root) {
		return (ObjectNode) root.get("policy");
	}

	private static ObjectNode service(ObjectNode root) {
		return (ObjectNode) root.get("services").get(0);
	}

	private static ArrayNode codes(JsonNode node) {
		return (ArrayNode) node;
	}

	private static ArrayNode pairs(String merchantClass, String relatedClass) {
		ArrayNode pairs = MAPPER.createArrayNode();
		pairs.addObject().put("merchantClass", merchantClass).put("relatedClass", relatedClass);
		return pairs;
	}

}
