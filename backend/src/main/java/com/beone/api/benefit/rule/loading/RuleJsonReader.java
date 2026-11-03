package com.beone.api.benefit.rule.loading;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.card.LimitBucketId;
import com.beone.api.benefit.card.MerchantClass;
import com.beone.api.benefit.card.PaymentMethod;
import com.beone.api.benefit.card.ServiceId;
import com.beone.api.benefit.card.TransactionNature;
import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.rule.BenefitCalculation;
import com.beone.api.benefit.rule.BenefitKind;
import com.beone.api.benefit.rule.BenefitService;
import com.beone.api.benefit.rule.CancellationAttribution;
import com.beone.api.benefit.rule.CardRuleVersion;
import com.beone.api.benefit.rule.FractionalWonPolicy;
import com.beone.api.benefit.rule.GraceSpan;
import com.beone.api.benefit.rule.InclusionUnconfirmed;
import com.beone.api.benefit.rule.LimitBucket;
import com.beone.api.benefit.rule.LimitDeductionOrder;
import com.beone.api.benefit.rule.LimitPeriod;
import com.beone.api.benefit.rule.LimitTier;
import com.beone.api.benefit.rule.MerchantTarget;
import com.beone.api.benefit.rule.NewCardGrace;
import com.beone.api.benefit.rule.OfficialSource;
import com.beone.api.benefit.rule.PerformanceWindow;
import com.beone.api.benefit.rule.RulePolicy;
import com.beone.api.benefit.rule.RuleTerm;
import com.beone.api.benefit.rule.ServiceDetails;
import com.beone.api.benefit.rule.ServiceTier;
import com.beone.api.benefit.rule.StackingBasis;
import com.beone.api.benefit.rule.TermBasis;
import com.beone.api.benefit.rule.TransactionDateBasis;
import com.beone.api.benefit.rule.ValidityPeriod;
import com.beone.api.benefit.rule.VerificationStatus;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 규칙 JSON 한 개를 {@link CardRuleVersion}으로 읽는다.
 *
 * <p>
 * 모르는 키, 빠진 필수 키, 잘못된 타입과 허용 목록 밖의 업종 코드는 거부한다. 기본값을 채우지 않는다.
 * 키가 없을 때 빈 값으로 읽으면 "제외 없음"처럼 다른 뜻이 되기 때문이다(docs/rules/README.md).
 * 근거 ID(`evidence`)는 형식만 확인하고 모델에 담지 않는다.
 */
public final class RuleJsonReader {

	private static final JsonMapper MAPPER = JsonMapper.builder()
		.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
		.build();

	private final Set<MerchantClass> allowedClasses;

	public RuleJsonReader(Set<MerchantClass> allowedClasses) {
		this.allowedClasses = Set.copyOf(allowedClasses);
	}

	public CardRuleVersion read(String json) {
		Objects.requireNonNull(json, "json");
		JsonNode root;
		try {
			root = MAPPER.readTree(json);
		}
		catch (JacksonException exception) {
			// 입력 일부가 메시지에 실리지 않도록 원인 종류만 남긴다.
			throw new IllegalArgumentException("rule json is malformed: " + exception.getClass().getSimpleName());
		}
		onlyKeys(root, "rule", "cardId", "ruleVersion", "status", "evaluationOnly", "validity", "sources", "policy",
				"limitBuckets", "services");
		return new CardRuleVersion(CardId.of(text(root, "cardId", "rule")), text(root, "ruleVersion", "rule"),
				validity(required(root, "validity", "rule")),
				enumValue(VerificationStatus.class, required(root, "status", "rule"), "rule.status"),
				bool(root, "evaluationOnly", "rule"), list(root, "sources", "rule", this::source),
				policy(required(root, "policy", "rule")), list(root, "services", "rule", this::service),
				list(root, "limitBuckets", "rule", this::limitBucket));
	}

	private ValidityPeriod validity(JsonNode node) {
		onlyKeys(node, "validity", "from", "to");
		LocalDate from = date(text(node, "from", "validity"), "validity.from");
		JsonNode to = required(node, "to", "validity");
		return new ValidityPeriod(from, to.isNull() ? Optional.empty() : Optional.of(date(string(to, "validity.to"), "validity.to")));
	}

	private OfficialSource source(JsonNode node) {
		onlyKeys(node, "source", "uri", "locator");
		return OfficialSource.of(text(node, "uri", "source"), text(node, "locator", "source"));
	}

	private RulePolicy policy(JsonNode node) {
		String where = "policy";
		onlyKeys(node, where, "performanceWindow", "performanceDateBasis", "cancellationAttribution",
				"performanceExcludedNatures", "performanceExcludedClasses", "performanceExcludedClassesEvidence",
				"excludesBenefitedSales", "limitPeriod", "limitDeductionOrder", "newCardGrace", "inclusionUnconfirmed");
		evidenceId(required(node, "performanceExcludedClassesEvidence", where), "policy.performanceExcludedClassesEvidence");
		return new RulePolicy(enumTerm(node, "performanceWindow", where, PerformanceWindow.class),
				enumTerm(node, "performanceDateBasis", where, TransactionDateBasis.class),
				enumTerm(node, "cancellationAttribution", where, CancellationAttribution.class),
				enumSet(required(node, "performanceExcludedNatures", where), TransactionNature.class,
						"policy.performanceExcludedNatures"),
				classSet(required(node, "performanceExcludedClasses", where), "policy.performanceExcludedClasses"),
				term(required(node, "excludesBenefitedSales", where), "policy.excludesBenefitedSales",
						(value, at) -> requireBoolean(value, at)),
				enumTerm(node, "limitPeriod", where, LimitPeriod.class),
				enumTerm(node, "limitDeductionOrder", where, LimitDeductionOrder.class),
				term(required(node, "newCardGrace", where), "policy.newCardGrace", this::grace),
				inclusionPairs(node, where));
	}

	private Optional<NewCardGrace> grace(JsonNode node, String where) {
		if (node.isNull()) {
			return Optional.empty();
		}
		onlyKeys(node, where, "span", "days", "deemedPerformance", "services");
		JsonNode days = required(node, "days", where);
		return Optional.of(new NewCardGrace(enumValue(GraceSpan.class, required(node, "span", where), where + ".span"),
				days.isNull() ? Optional.empty() : Optional.of(integer(days, where + ".days")),
				won(required(node, "deemedPerformance", where), where + ".deemedPerformance"),
				idSet(required(node, "services", where), where + ".services", ServiceId::of)));
	}

	private LimitBucket limitBucket(JsonNode node) {
		String where = "limitBucket " + node.path("id").asString("?");
		onlyKeys(node, where, "id", "tiers", "parent", "evidence");
		evidenceId(required(node, "evidence", where), where + ".evidence");
		JsonNode parent = required(node, "parent", where);
		return new LimitBucket(LimitBucketId.of(text(node, "id", where)), list(node, "tiers", where, tier -> limitTier(tier, where)),
				parent.isNull() ? Optional.empty() : Optional.of(LimitBucketId.of(string(parent, where + ".parent"))));
	}

	private LimitTier limitTier(JsonNode node, String where) {
		onlyKeys(node, where + ".tier", "minimumPerformance", "monthlyCap");
		return new LimitTier(won(required(node, "minimumPerformance", where), where + ".minimumPerformance"),
				won(required(node, "monthlyCap", where), where + ".monthlyCap"));
	}

	private BenefitService service(JsonNode node) {
		String where = "service " + node.path("id").asString("?");
		onlyKeys(node, where, "id", "displayName", "kind", "target", "paymentMethods", "excludedClasses",
				"unevaluatedExclusions", "minimumAmount", "maximumTargetAmount", "tiers", "limitBuckets", "requiresSelection",
				"stackableWith", "fractionalWon", "stackingBasis", "inclusionUnconfirmed", "evidence");
		for (JsonNode evidence : array(required(node, "evidence", where), where + ".evidence")) {
			evidenceId(evidence, where + ".evidence");
		}
		JsonNode maximum = required(node, "maximumTargetAmount", where);
		JsonNode fractionalWon = required(node, "fractionalWon", where);
		return new BenefitService(ServiceId.of(text(node, "id", where)),
				enumValue(BenefitKind.class, required(node, "kind", where), where + ".kind"),
				target(required(node, "target", where), where + ".target"),
				classSet(required(node, "excludedClasses", where), where + ".excludedClasses"),
				enumSet(required(node, "paymentMethods", where), PaymentMethod.class, where + ".paymentMethods"),
				won(required(node, "minimumAmount", where), where + ".minimumAmount"),
				maximum.isNull() ? Optional.empty() : Optional.of(won(maximum, where + ".maximumTargetAmount")),
				list(node, "tiers", where, tier -> serviceTier(tier, where)),
				List.copyOf(idSet(required(node, "limitBuckets", where), where + ".limitBuckets", LimitBucketId::of)),
				bool(node, "requiresSelection", where),
				term(required(node, "stackableWith", where), where + ".stackableWith",
						(value, at) -> idSet(value, at, ServiceId::of)),
				term(fractionalWon, where + ".fractionalWon",
						(value, at) -> enumValue(FractionalWonPolicy.class, value, at), "basis", "decision"),
				details(node, fractionalWon, where));
	}

	private ServiceDetails details(JsonNode node, JsonNode fractionalWon, String where) {
		Optional<String> displayName = node.has("displayName") ? Optional.of(text(node, "displayName", where)) : Optional.empty();
		Optional<StackingBasis> stacking = Optional.empty();
		if (node.has("stackingBasis")) {
			stacking = Optional.of(stackingBasis(node.get("stackingBasis"), where + ".stackingBasis"));
		}
		List<String> unevaluated = new ArrayList<>();
		for (JsonNode exclusion : array(required(node, "unevaluatedExclusions", where), where + ".unevaluatedExclusions")) {
			unevaluated.add(string(exclusion, where + ".unevaluatedExclusions"));
		}
		return new ServiceDetails(displayName, stacking, termBasis(fractionalWon, where + ".fractionalWon"),
				inclusionPairs(node, where), unevaluated);
	}

	private StackingBasis stackingBasis(JsonNode node, String where) {
		onlyKeys(node, where, "value", "evidence");
		evidenceId(required(node, "evidence", where), where + ".evidence");
		return enumValue(StackingBasis.class, required(node, "value", where), where + ".value");
	}

	// 값이 확정된 항목의 근거는 생략하지 못한다. 둘 중 하나만 있으면 거부한다.
	private Optional<TermBasis> termBasis(JsonNode term, String where) {
		if (term.has("unresolved")) {
			return Optional.empty();
		}
		TermBasis.Kind kind = enumValue(TermBasis.Kind.class, required(term, "basis", where), where + ".basis");
		return Optional.of(new TermBasis(kind, text(term, "decision", where)));
	}

	private ServiceTier serviceTier(JsonNode node, String where) {
		onlyKeys(node, where + ".tier", "minimumPerformance", "calculation");
		return new ServiceTier(won(required(node, "minimumPerformance", where), where + ".minimumPerformance"),
				term(required(node, "calculation", where), where + ".calculation", this::calculation));
	}

	private BenefitCalculation calculation(JsonNode node, String where) {
		String type = text(node, "type", where);
		return switch (type) {
			case "RATE" -> {
				onlyKeys(node, where, "type", "basisPoints");
				yield BenefitCalculation.rate(integer(required(node, "basisPoints", where), where + ".basisPoints"));
			}
			case "FIXED_AMOUNT" -> {
				onlyKeys(node, where, "type", "won");
				yield new BenefitCalculation.FixedAmount(won(required(node, "won", where), where + ".won"));
			}
			default -> throw new IllegalArgumentException(where + " has unknown calculation type");
		};
	}

	private MerchantTarget target(JsonNode node, String where) {
		String type = text(node, "type", where);
		return switch (type) {
			case "ALL_MERCHANTS" -> {
				onlyKeys(node, where, "type");
				yield MerchantTarget.allMerchants();
			}
			case "SPECIFIC_CLASSES" -> {
				onlyKeys(node, where, "type", "classes");
				yield MerchantTarget.only(classSet(required(node, "classes", where), where + ".classes"));
			}
			default -> throw new IllegalArgumentException(where + " has unknown target type");
		};
	}

	private Set<InclusionUnconfirmed> inclusionPairs(JsonNode owner, String where) {
		if (!owner.has("inclusionUnconfirmed")) {
			return Set.of();
		}
		String at = where + ".inclusionUnconfirmed";
		Set<InclusionUnconfirmed> pairs = new LinkedHashSet<>();
		for (JsonNode pair : array(owner.get("inclusionUnconfirmed"), at)) {
			onlyKeys(pair, at, "merchantClass", "relatedClass");
			if (!pairs.add(new InclusionUnconfirmed(merchantClass(required(pair, "merchantClass", at), at),
					merchantClass(required(pair, "relatedClass", at), at)))) {
				throw new IllegalArgumentException(at + " has a duplicate pair");
			}
		}
		return pairs;
	}

	private Set<MerchantClass> classSet(JsonNode node, String where) {
		Set<MerchantClass> classes = new LinkedHashSet<>();
		for (JsonNode item : array(node, where)) {
			if (!classes.add(merchantClass(item, where))) {
				throw new IllegalArgumentException(where + " has a duplicate value");
			}
		}
		return classes;
	}

	private MerchantClass merchantClass(JsonNode node, String where) {
		MerchantClass merchantClass = MerchantClass.of(string(node, where));
		if (!allowedClasses.contains(merchantClass)) {
			throw new IllegalArgumentException(where + " has a class outside the allowlist: " + merchantClass);
		}
		return merchantClass;
	}

	private static <T> Set<T> idSet(JsonNode node, String where, Function<String, T> factory) {
		Set<T> ids = new LinkedHashSet<>();
		for (JsonNode item : array(node, where)) {
			if (!ids.add(factory.apply(string(item, where)))) {
				throw new IllegalArgumentException(where + " has a duplicate value");
			}
		}
		return ids;
	}

	private static <E extends Enum<E>> RuleTerm<E> enumTerm(JsonNode owner, String key, String where, Class<E> type) {
		String at = where + "." + key;
		return term(required(owner, key, where), at, (value, path) -> enumValue(type, value, path));
	}

	/**
	 * 확정 값 {@code {"value": ..., "evidence": "E1"}} 또는 미확정 값 {@code {"unresolved": "이유"}}를 읽는다.
	 */
	private static <T> RuleTerm<T> term(JsonNode node, String where, TermValueReader<T> reader, String... extraKeys) {
		if (!node.isObject()) {
			throw new IllegalArgumentException(where + " must be an object");
		}
		if (node.has("unresolved")) {
			onlyKeys(node, where, "unresolved");
			return RuleTerm.unresolved(string(node.get("unresolved"), where + ".unresolved"));
		}
		List<String> allowed = new ArrayList<>(List.of("value", "evidence"));
		allowed.addAll(List.of(extraKeys));
		onlyKeys(node, where, allowed.toArray(String[]::new));
		if (node.has("evidence")) {
			evidenceId(node.get("evidence"), where + ".evidence");
		}
		return RuleTerm.resolved(reader.read(required(node, "value", where), where + ".value"));
	}

	private static Boolean requireBoolean(JsonNode node, String where) {
		if (!node.isBoolean()) {
			throw new IllegalArgumentException(where + " must be a boolean");
		}
		return node.booleanValue();
	}

	private static <E extends Enum<E>> Set<E> enumSet(JsonNode node, Class<E> type, String where) {
		Set<E> values = new LinkedHashSet<>();
		for (JsonNode item : array(node, where)) {
			if (!values.add(enumValue(type, item, where))) {
				throw new IllegalArgumentException(where + " has a duplicate value");
			}
		}
		return values;
	}

	private static <E extends Enum<E>> E enumValue(Class<E> type, JsonNode node, String where) {
		String value = string(node, where);
		try {
			return Enum.valueOf(type, value);
		}
		catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException(where + " has unknown value " + value);
		}
	}

	private static <T> List<T> list(JsonNode owner, String key, String where, Function<JsonNode, T> reader) {
		List<T> items = new ArrayList<>();
		for (JsonNode item : array(required(owner, key, where), where + "." + key)) {
			items.add(reader.apply(item));
		}
		return items;
	}

	private static JsonNode array(JsonNode node, String where) {
		if (!node.isArray()) {
			throw new IllegalArgumentException(where + " must be an array");
		}
		return node;
	}

	private static void onlyKeys(JsonNode node, String where, String... allowed) {
		if (!node.isObject()) {
			throw new IllegalArgumentException(where + " must be an object");
		}
		Set<String> keys = Set.of(allowed);
		for (String key : node.propertyNames()) {
			if (!keys.contains(key)) {
				throw new IllegalArgumentException(where + " has unknown key '" + key + "'");
			}
		}
	}

	private static JsonNode required(JsonNode node, String key, String where) {
		JsonNode value = node.get(key);
		if (value == null) {
			throw new IllegalArgumentException(where + " is missing key '" + key + "'");
		}
		return value;
	}

	private static String text(JsonNode node, String key, String where) {
		return string(required(node, key, where), where + "." + key);
	}

	private static String string(JsonNode node, String where) {
		if (!node.isString() || node.stringValue().isBlank()) {
			throw new IllegalArgumentException(where + " must be a non-blank string");
		}
		return node.stringValue();
	}

	private static boolean bool(JsonNode node, String key, String where) {
		return requireBoolean(required(node, key, where), where + "." + key);
	}

	private static int integer(JsonNode node, String where) {
		if (!node.isIntegralNumber() || !node.canConvertToInt()) {
			throw new IllegalArgumentException(where + " must be an integer");
		}
		return node.intValue();
	}

	private static Won won(JsonNode node, String where) {
		if (!node.isIntegralNumber() || !node.canConvertToLong()) {
			throw new IllegalArgumentException(where + " must be a whole won amount");
		}
		return Won.of(node.longValue());
	}

	private static LocalDate date(String value, String where) {
		try {
			return LocalDate.parse(value);
		}
		catch (java.time.format.DateTimeParseException exception) {
			throw new IllegalArgumentException(where + " must be an ISO date");
		}
	}

	private static void evidenceId(JsonNode node, String where) {
		if (!string(node, where).matches("[A-Z]\\d+")) {
			throw new IllegalArgumentException(where + " must be an evidence id");
		}
	}

	@FunctionalInterface
	private interface TermValueReader<T> {

		T read(JsonNode value, String where);

	}

}
