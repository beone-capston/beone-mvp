package com.beone.api.benefit.evaluation;

import static com.beone.api.benefit.evaluation.FixtureJson.date;
import static com.beone.api.benefit.evaluation.FixtureJson.integer;
import static com.beone.api.benefit.evaluation.FixtureJson.integerValue;
import static com.beone.api.benefit.evaluation.FixtureJson.isAbsentOrNull;
import static com.beone.api.benefit.evaluation.FixtureJson.required;
import static com.beone.api.benefit.evaluation.FixtureJson.requireOnlyKeys;
import static com.beone.api.benefit.evaluation.FixtureJson.text;
import static com.beone.api.benefit.evaluation.FixtureJson.textList;
import static com.beone.api.benefit.evaluation.FixtureJson.textOrNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.card.LimitBucketId;
import com.beone.api.benefit.card.MerchantClass;
import com.beone.api.benefit.card.PaymentMethod;
import com.beone.api.benefit.card.ServiceId;
import com.beone.api.benefit.card.TransactionNature;
import com.beone.api.benefit.input.CardMonthlyState;
import com.beone.api.benefit.input.HeldCard;
import com.beone.api.benefit.input.Ledger;
import com.beone.api.benefit.input.LedgerCoverage;
import com.beone.api.benefit.input.LedgerEntryType;
import com.beone.api.benefit.input.LedgerSource;
import com.beone.api.benefit.input.LedgerTransaction;
import com.beone.api.benefit.input.Order;
import com.beone.api.benefit.input.PlannedSpending;
import com.beone.api.benefit.input.PlannedSpendings;
import com.beone.api.benefit.input.RecommendationInput;
import com.beone.api.benefit.input.SelectionMode;
import com.beone.api.benefit.input.SelectionPreference;
import com.beone.api.benefit.input.ServiceSelection;
import com.beone.api.benefit.money.BenefitAmount;
import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.result.ApplicationStatus;
import com.beone.api.benefit.result.SelectionBasis;
import com.beone.api.benefit.rule.CardRuleVersion;
import com.beone.api.benefit.rule.RulePolicy;
import com.beone.api.benefit.rule.RuleTerm;
import com.beone.api.benefit.rule.ValidityPeriod;
import com.beone.api.benefit.rule.VerificationStatus;
import com.beone.api.benefit.state.BasisPeriod;
import com.beone.api.benefit.state.StateSource;
import com.beone.api.benefit.state.StateValue;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code fixtures/evaluation-cases.json}을 모델로 읽는다.
 * fixture의 입력과 기대 결과를 표현만 하며, 파일을 수정하거나 후보 규칙을 {@code VERIFIED}로 바꾸거나 혜택을 계산하지 않는다.
 *
 * <p>
 * 매핑 기준(이슈 #15 Q1~Q3):
 * <ul>
 * <li>{@code prior.kind}: {@code ORDINARY}/{@code TAX}는 해당 성격의 승인 거래로, {@code CANCEL_RECEIVED}는 음수 금액과 {@code originalAt}을 유지한 취소로 바꾼다. fixture에 없는 값은 비워 둔다.</li>
 * <li>날짜는 날짜로 두고 시각을 만들지 않는다.</li>
 * <li>{@code ruleSnapshot}은 fixture 상태를 유지한 평가 전용 규칙으로 바꾼다.</li>
 * <li>fixture 기본값은 명시적으로 적용한다. 적히지 않은 혜택 사용액은 {@code usedBenefitsWon}의 합성 값이고, 보유 카드마다 주문월 기준 전월을 완전 기간으로 선언한다.</li>
 * </ul>
 */
final class EvaluationFixtureReader {

	static final Path FIXTURE = Path.of("..", "fixtures", "evaluation-cases.json");

	private static final String NOT_IN_FIXTURE = "not stated by the evaluation fixture";

	private static final JsonMapper MAPPER = JsonMapper.builder()
		.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
		.build();

	private final Defaults defaults;

	private EvaluationFixtureReader(Defaults defaults) {
		this.defaults = defaults;
	}

	static EvaluationFixture read() throws IOException {
		return read(Files.readString(FIXTURE));
	}

	static EvaluationFixture read(String json) {
		JsonNode root = MAPPER.readTree(json);
		requireOnlyKeys(root, "fixture", "schemaVersion", "status", "reviewStatus", "purpose", "policyBasisCommit",
				"defaults", "sources", "cases", "mutations", "reviewRecord");
		// 기본값을 먼저 읽고, 사례를 매핑한 뒤 변형은 원본 사례 JSON에 패치를 적용해 매핑한다.
		JsonNode defaultsNode = required(root, "defaults");
		EvaluationFixtureReader reader = new EvaluationFixtureReader(Defaults.read(defaultsNode));

		Map<String, JsonNode> rawCases = new LinkedHashMap<>();
		List<EvaluationScenario> cases = new ArrayList<>();
		for (JsonNode caseNode : required(root, "cases").values()) {
			String id = text(caseNode, "id");
			if (rawCases.put(id, caseNode) != null) {
				throw new IllegalArgumentException("duplicate case id " + id);
			}
			cases.add(reader.scenario(caseNode, Optional.empty(), required(caseNode, "expected"),
					text(required(caseNode, "expected"), "calculation")));
		}

		List<EvaluationScenario> mutations = new ArrayList<>();
		for (JsonNode mutation : required(root, "mutations").values()) {
			requireOnlyKeys(mutation, "mutation", "id", "base", "expected", "calculation", "dimension", "patch");
			String baseId = text(mutation, "base");
			JsonNode base = rawCases.get(baseId);
			if (base == null) {
				throw new IllegalArgumentException("mutation " + text(mutation, "id") + " refers to unknown case " + baseId);
			}
			JsonNode patched = JsonPatch.apply(base, required(mutation, "patch"));
			mutations.add(reader.mutationScenario(mutation, patched));
		}

		return new EvaluationFixture(text(root, "schemaVersion"), text(root, "status"), text(root, "reviewStatus"),
				text(root, "purpose"), text(root, "policyBasisCommit"), defaults(defaultsNode),
				textMap(required(root, "sources")), textMap(required(root, "reviewRecord")), cases, mutations);
	}

	private EvaluationScenario mutationScenario(JsonNode mutation, JsonNode patchedCase) {
		EvaluationScenario mapped = scenario(patchedCase, Optional.of(text(mutation, "base")),
				required(mutation, "expected"), text(mutation, "calculation"));
		return new EvaluationScenario(text(mutation, "id"), mapped.baseCaseId(), text(mutation, "dimension"),
				mapped.sourceKeys(), mapped.input(), mapped.ruleSnapshots(), mapped.expected());
	}

	private EvaluationScenario scenario(JsonNode caseNode, Optional<String> baseCaseId, JsonNode expectedNode,
			String calculation) {
		requireOnlyKeys(caseNode, "case", "id", "focus", "cards", "prior", "current", "ruleSnapshot", "selection",
				"usedBenefits", "future", "representativeCard", "expected", "sources");
		String id = text(caseNode, "id");
		List<CardId> cards = textList(caseNode, "cards").stream().map(CardId::of).toList();
		Order order = order(required(caseNode, "current"));
		BasisPeriod orderMonth = BasisPeriod.month(YearMonth.from(order.date()));

		// 보유 카드: fixture 기본 등록일을 합성 상태값으로 둔다.
		List<HeldCard> held = cards.stream()
			.map(card -> new HeldCard(card, defaults.holdingSource,
					StateValue.synthetic(defaults.registeredAt, BasisPeriod.day(defaults.currentAt))))
			.toList();

		// 이번 달 혜택 사용액: "카드:한도" 키를 카드별 한도 묶음 사용액으로 나눈다.
		Map<CardId, Map<LimitBucketId, StateValue<Won>>> usedByCard = new HashMap<>();
		if (caseNode.get("usedBenefits") != null) {
			for (Map.Entry<String, JsonNode> entry : required(caseNode, "usedBenefits").properties()) {
				String[] key = CardScopedKey.split(entry.getKey());
				CardId card = CardId.of(key[0]);
				Won used = Won.of(integerValue(entry.getValue(), entry.getKey()));
				usedByCard.computeIfAbsent(card, ignored -> new HashMap<>())
					.put(LimitBucketId.of(key[1]), StateValue.synthetic(used, orderMonth));
			}
		}
		for (CardId card : usedByCard.keySet()) {
			if (!cards.contains(card)) {
				throw new IllegalArgumentException(id + " has benefit usage for card " + card + " that is not held");
			}
		}

		// 선택 서비스: fixture에 카드 ID가 없으므로 보유 카드가 한 장일 때만 연결한다.
		List<StateValue<ServiceSelection>> selections = List.of();
		if (caseNode.get("selection") != null) {
			JsonNode selection = required(caseNode, "selection");
			requireOnlyKeys(selection, "selection", "type", "effectiveFrom");
			if (cards.size() != 1) {
				throw new IllegalArgumentException(id + " has a selection but does not name its card");
			}
			LocalDate effectiveFrom = date(selection, "effectiveFrom");
			selections = List.of(StateValue.synthetic(
					new ServiceSelection(ServiceId.of(text(selection, "type")), effectiveFrom),
					BasisPeriod.of(effectiveFrom, defaults.currentAt)));
		}
		List<StateValue<ServiceSelection>> cardSelections = selections;

		List<CardMonthlyState> states = cards.stream()
			.map(card -> new CardMonthlyState(card, Optional.empty(), usedByCard.getOrDefault(card, Map.of()),
					StateValue.synthetic(defaults.usedBenefits, orderMonth), cardSelections))
			.toList();

		// 원장: 주문월 기준 전월을 보유 카드마다 완전 기간으로 선언한다(fixture priorLedgerCompleteness).
		BasisPeriod priorMonth = BasisPeriod.month(YearMonth.from(order.date()).minusMonths(1));
		List<LedgerCoverage> coverage = cards.stream().map(card -> new LedgerCoverage(card, priorMonth)).toList();
		List<LedgerTransaction> transactions = required(caseNode, "prior").values()
			.stream()
			.map(EvaluationFixtureReader::ledgerTransaction)
			.toList();
		Ledger ledger = new Ledger(LedgerSource.SIMULATION, transactions, coverage);

		// 예정 소비와 대표 카드: 사례에 없으면 fixture 기본값을 쓴다.
		JsonNode futureNode = caseNode.get("future") != null ? required(caseNode, "future") : defaults.future;
		PlannedSpendings planned = new PlannedSpendings(
				futureNode.values().stream().map(EvaluationFixtureReader::plannedSpending).toList());

		String representative = caseNode.get("representativeCard") != null ? textOrNull(caseNode, "representativeCard")
				: defaults.representativeCard;
		SelectionPreference preference = new SelectionPreference(SelectionMode.AUTO_RECOMMEND,
				Optional.ofNullable(representative).map(CardId::of));

		RecommendationInput input = new RecommendationInput(defaults.currentAt, order, held, ledger, states, planned,
				preference);
		return new EvaluationScenario(id, baseCaseId, text(caseNode, "focus"), textList(caseNode, "sources"), input,
				ruleSnapshots(caseNode), expected(expectedNode, calculation));
	}

	private static Order order(JsonNode node) {
		requireOnlyKeys(node, "current", "at", "merchantClass", "amountWon", "paymentMethod");
		return new Order(Optional.empty(), Optional.empty(), Won.of(integer(node, "amountWon")), date(node, "at"),
				Optional.empty(), MerchantClass.of(text(node, "merchantClass")),
				PaymentMethod.valueOf(text(node, "paymentMethod")));
	}

	private static PlannedSpending plannedSpending(JsonNode node) {
		requireOnlyKeys(node, "future", "at", "merchantClass", "amountWon", "paymentMethod");
		return new PlannedSpending(date(node, "at"), Optional.empty(), Won.of(integer(node, "amountWon")),
				MerchantClass.of(text(node, "merchantClass")), Optional.empty(),
				PaymentMethod.valueOf(text(node, "paymentMethod")));
	}

	private static LedgerTransaction ledgerTransaction(JsonNode node) {
		requireOnlyKeys(node, "prior", "cardId", "at", "amountWon", "kind", "originalAt");
		CardId card = CardId.of(text(node, "cardId"));
		LocalDate date = date(node, "at");
		Won amount = Won.of(integer(node, "amountWon"));
		String kind = text(node, "kind");
		Optional<LocalDate> originalDate = node.get("originalAt") == null ? Optional.empty()
				: Optional.of(date(node, "originalAt"));
		return switch (kind) {
			case "ORDINARY", "TAX" -> new LedgerTransaction(card, date, Optional.empty(), amount,
					LedgerEntryType.APPROVAL, Optional.of(TransactionNature.valueOf(kind)), Optional.empty(),
					Optional.empty(), originalDate);
			case "CANCEL_RECEIVED" -> new LedgerTransaction(card, date, Optional.empty(), amount,
					LedgerEntryType.CANCELLATION, Optional.empty(), Optional.empty(), Optional.empty(), originalDate);
			default -> throw new IllegalArgumentException("unknown ledger kind " + kind);
		};
	}

	private static List<CardRuleVersion> ruleSnapshots(JsonNode caseNode) {
		if (caseNode.get("ruleSnapshot") == null) {
			return List.of();
		}
		List<CardRuleVersion> snapshots = new ArrayList<>();
		for (Map.Entry<String, JsonNode> entry : required(caseNode, "ruleSnapshot").properties()) {
			JsonNode snapshot = entry.getValue();
			requireOnlyKeys(snapshot, "ruleSnapshot", "ruleVersion", "status", "validFrom", "validTo");
			snapshots.add(new CardRuleVersion(CardId.of(entry.getKey()), text(snapshot, "ruleVersion"),
					ValidityPeriod.between(date(snapshot, "validFrom"), date(snapshot, "validTo")),
					VerificationStatus.valueOf(text(snapshot, "status")), true, List.of(), unresolvedPolicy(),
					List.of(), List.of()));
		}
		return snapshots;
	}

	private static RulePolicy unresolvedPolicy() {
		return new RulePolicy(RuleTerm.unresolved(NOT_IN_FIXTURE), RuleTerm.unresolved(NOT_IN_FIXTURE),
				RuleTerm.unresolved(NOT_IN_FIXTURE), Set.of(), Set.of(),
				RuleTerm.unresolved(NOT_IN_FIXTURE), RuleTerm.unresolved(NOT_IN_FIXTURE),
				RuleTerm.unresolved(NOT_IN_FIXTURE), RuleTerm.unresolved(NOT_IN_FIXTURE));
	}

	private static ExpectedOutcome expected(JsonNode node, String calculation) {
		requireOnlyKeys(node, "expected", "benefits", "selected", "calculation", "applicationStatusByCard", "services",
				"serviceStatus", "tie", "tieBreak", "monthlyTotalWon");
		Map<CardId, BenefitAmount> benefits = new HashMap<>();
		for (Map.Entry<String, JsonNode> entry : required(node, "benefits").properties()) {
			benefits.put(CardId.of(entry.getKey()), amount(entry.getValue(), entry.getKey()));
		}
		Map<CardId, ApplicationStatus> statuses = new HashMap<>();
		for (Map.Entry<String, JsonNode> entry : required(node, "applicationStatusByCard").properties()) {
			statuses.put(CardId.of(entry.getKey()), status(entry.getValue(), entry.getKey()));
		}
		Map<ServiceRef, BenefitAmount> services = new HashMap<>();
		if (node.get("services") != null) {
			for (Map.Entry<String, JsonNode> entry : required(node, "services").properties()) {
				services.put(ServiceRef.parse(entry.getKey()), amount(entry.getValue(), entry.getKey()));
			}
		}
		Map<ServiceRef, ApplicationStatus> serviceStatus = new HashMap<>();
		if (node.get("serviceStatus") != null) {
			for (Map.Entry<String, JsonNode> entry : required(node, "serviceStatus").properties()) {
				serviceStatus.put(ServiceRef.parse(entry.getKey()), status(entry.getValue(), entry.getKey()));
			}
		}
		Optional<CardId> selected = Optional.ofNullable(textOrNull(node, "selected")).map(CardId::of);
		List<CardId> tie = node.get("tie") == null ? List.of() : textList(node, "tie").stream().map(CardId::of).toList();
		Optional<SelectionBasis> tieBreak = Optional.ofNullable(textOrNull(node, "tieBreak")).map(SelectionBasis::valueOf);
		Optional<Won> monthlyTotal = isAbsentOrNull(node, "monthlyTotalWon") ? Optional.empty()
				: Optional.of(Won.of(integer(node, "monthlyTotalWon")));
		return new ExpectedOutcome(benefits, statuses, services, serviceStatus, selected, tie, tieBreak, monthlyTotal,
				calculation);
	}

	/**
	 * fixture의 {@code null}은 {@code 확인 필요}이며 0원으로 바꾸지 않는다.
	 */
	private static BenefitAmount amount(JsonNode value, String key) {
		if (value.isNull()) {
			return BenefitAmount.needsConfirmation("evaluation fixture marks " + key + " as 확인 필요");
		}
		return new BenefitAmount.Confirmed(Won.of(integerValue(value, key)));
	}

	private static ApplicationStatus status(JsonNode value, String key) {
		if (!value.isString()) {
			throw new IllegalArgumentException("status of " + key + " must be a string");
		}
		return ApplicationStatus.valueOf(value.stringValue());
	}

	private static Map<String, String> textMap(JsonNode node) {
		Map<String, String> values = new LinkedHashMap<>();
		for (Map.Entry<String, JsonNode> entry : node.properties()) {
			JsonNode value = entry.getValue();
			if (!value.isNull() && !value.isString()) {
				throw new IllegalArgumentException("'" + entry.getKey() + "' must be text or null");
			}
			values.put(entry.getKey(), value.isNull() ? "null" : value.stringValue());
		}
		return values;
	}

	private static Map<String, String> defaults(JsonNode node) {
		Map<String, String> notes = new LinkedHashMap<>();
		for (String key : Defaults.NOTE_KEYS) {
			notes.put(key, text(node, key));
		}
		return notes;
	}

	/**
	 * 매핑에 쓰는 fixture 기본값. 코드에 고정하지 않고 파일에서 읽는다.
	 */
	private record Defaults(LocalDate currentAt, LocalDate registeredAt, Won usedBenefits, JsonNode future,
			String representativeCard, StateSource holdingSource) {

		static final List<String> NOTE_KEYS = List.of("priorLedgerCompleteness", "knownClassification",
				"ruleApplicability", "otherExclusions", "stateProvenance", "tieBreak", "cardStatusRule");

		static Defaults read(JsonNode node) {
			requireOnlyKeys(node, "defaults", "mode", "currentAt", "registeredAt", "priorLedgerCompleteness",
					"knownClassification", "usedBenefitsWon", "ruleApplicability", "future", "otherExclusions",
					"stateProvenance", "representativeCard", "tieBreak", "cardStatusRule");
			String mode = text(node, "mode");
			if (!"SYNTHETIC".equals(mode)) {
				throw new IllegalArgumentException("only SYNTHETIC fixture mode is mapped, got " + mode);
			}
			JsonNode future = required(node, "future");
			if (!future.isArray()) {
				throw new IllegalArgumentException("default future must be an array");
			}
			return new Defaults(date(node, "currentAt"), date(node, "registeredAt"),
					Won.of(integer(node, "usedBenefitsWon")), future, textOrNull(node, "representativeCard"),
					StateSource.SYNTHETIC);
		}

	}

}
