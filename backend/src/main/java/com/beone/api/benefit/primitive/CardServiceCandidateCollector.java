package com.beone.api.benefit.primitive;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.beone.api.benefit.card.LimitBucketId;
import com.beone.api.benefit.card.ServiceId;
import com.beone.api.benefit.input.CardMonthlyState;
import com.beone.api.benefit.input.Order;
import com.beone.api.benefit.input.ServiceSelection;
import com.beone.api.benefit.money.BenefitAmount;
import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.result.ApplicationStatus;
import com.beone.api.benefit.result.CardServiceCandidates;
import com.beone.api.benefit.result.ConfirmationStage;
import com.beone.api.benefit.result.ServiceCandidate;
import com.beone.api.benefit.result.ServiceNeedingConfirmation;
import com.beone.api.benefit.result.UnsupportedReason;
import com.beone.api.benefit.result.UnsupportedService;
import com.beone.api.benefit.rule.BenefitService;
import com.beone.api.benefit.rule.CardRuleVersion;
import com.beone.api.benefit.rule.RuleSelection;
import com.beone.api.benefit.rule.TriggerEvidence;
import com.beone.api.benefit.state.BasisPeriod;
import com.beone.api.benefit.state.StateValue;

/**
 * 규칙 선택 결과({@link RuleSelection})의 서비스마다 주문 한 건의 후보를 취합한다(#33, D-13, D-18 K1).
 *
 * <p>
 * 서비스마다 아래 순서에서 처음 해당하는 분류 하나에만 담는다.
 * <ol>
 * <li>지원 범위 밖: 선택형이거나 한도 기간이 미확정인 서비스. 판정기를 호출하지 않는다. 그 거래에 적용되지 않음이 업종 불일치, 다른 서비스 선택,
 * 선택 없음({@code NONE})으로 확정되면 그 사유의 확정 0원 후보이고(U2 ① 예외), 아니면 금액·확정 의미가 없는 "판정하지 않음" 분류이며 규칙 선택
 * 단계 사유 원문을 함께 보존한다.</li>
 * <li>규칙 선택 단계 확인 필요: {@code servicesNeedingConfirmation}의 사유를 그대로 담는다.</li>
 * <li>전월 실적 미입력({@code Optional.empty})·{@code UNKNOWN}: 실적 값을 만들어 넣지 않고 확인 필요로 둔다.</li>
 * <li>신규 유예({@link NewCardGraceJudge}): 유예 중이면 간주 실적으로 판정하고, 구간을 정할 수 없으면 주문만으로 확인되는 미충족은 확정 0원, 나머지는 확인 필요다.</li>
 * <li>판정기({@link ServiceCandidateEvaluator}) 결과: 확정 금액(확정 0원 포함)은 후보, 확인 필요 금액은 사유 원문과 함께 확인 필요로 옮긴다.</li>
 * </ol>
 *
 * <p>
 * {@code unresolvedConditionalTerms}는 해소하거나 무시하지 않고 그대로 전달한다. 채택·합계·한도 차감 기록은 하지 않는다.
 */
public final class CardServiceCandidateCollector {

	private CardServiceCandidateCollector() {
	}

	/**
	 * 규칙 선택 결과의 서비스마다 주문 한 건의 후보를 취합한다. 채택·합계는 정하지 않는다.
	 *
	 * @param selection 카드 한 장의 규칙 선택 결과
	 * @param order 현재 주문
	 * @param state 같은 카드의 월간 상태
	 * @param registeredAt 같은 카드의 등록일. 신규 유예 판정에 쓴다
	 * @return 규칙을 쓸 수 없으면 {@link CardServiceCandidates.Unavailable}(서비스 목록·금액 없음),
	 * 아니면 규칙의 모든 서비스를 정확히 한 분류에 담은 {@link CardServiceCandidates.Collected}
	 * @throws NullPointerException 입력이 null인 경우
	 * @throws IllegalArgumentException 카드 ID가 다르거나, 확인된 전월 실적이 음수이거나(규칙 사용 불가·범위 밖 경로 포함),
	 * 선택 결과의 계산 가능·확인 필요 서비스가 규칙의 서비스를 중복·누락 없이 나누지 않거나 규칙에 없는 서비스를 담았거나,
	 * 선택 결과의 확인 필요 사유 목록이 비었거나 공백 문구가 있는 경우(지원 범위 밖으로 분류되는 서비스 포함)
	 */
	public static CardServiceCandidates collect(RuleSelection selection, Order order, CardMonthlyState state,
			StateValue<LocalDate> registeredAt) {
		Objects.requireNonNull(selection, "selection");
		Objects.requireNonNull(order, "order");
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(registeredAt, "registeredAt");
		if (!selection.cardId().equals(state.cardId())) {
			throw new IllegalArgumentException(
					"monthly state card " + state.cardId() + " does not match rule selection card " + selection.cardId());
		}
		// 판정기를 거치지 않는 분류에서도 잘못된 실적이 숨겨지지 않도록 먼저 거부한다.
		Optional<Won> performance = knownPerformance(state);
		if (performance.isPresent() && performance.get().isNegative()) {
			throw new IllegalArgumentException(
					"negative prior performance has no confirmed tier rule: " + performance.get().value());
		}
		return switch (selection) {
			case RuleSelection.Unavailable unavailable -> new CardServiceCandidates.Unavailable(unavailable.cardId(),
					unavailable.status(), unavailable.reason());
			case RuleSelection.Selected selected -> collected(selected, order, state, registeredAt, performance);
		};
	}

	private static CardServiceCandidates.Collected collected(RuleSelection.Selected selected, Order order,
			CardMonthlyState state, StateValue<LocalDate> registeredAt, Optional<Won> performance) {
		requireConsistent(selected);
		CardRuleVersion rule = selected.rule();
		Map<ServiceId, List<String>> blocked = selected.servicesNeedingConfirmation();
		List<ServiceCandidate> candidates = new ArrayList<>();
		List<ServiceNeedingConfirmation> needingConfirmation = new ArrayList<>();
		List<UnsupportedService> unsupported = new ArrayList<>();
		Map<LimitBucketId, StateValue<Won>> usedThisMonth = usedInOrderMonth(state, order.date());
		for (BenefitService service : rule.services()) {
			// 1) 지원 범위 밖. 그 거래에 적용되지 않음이 확정되면 판정하지 않음 대신 확정 0원이다.
			Set<UnsupportedReason> unsupportedReasons = unsupportedReasons(rule, service);
			if (!unsupportedReasons.isEmpty()) {
				Set<ApplicationStatus> notApplicable = confirmedNotApplicable(rule, service, order, state);
				if (notApplicable.isEmpty()) {
					unsupported.add(new UnsupportedService(service.id(), unsupportedReasons,
							blocked.getOrDefault(service.id(), List.of())));
				}
				else {
					candidates.add(new ServiceCandidate(service.id(), BenefitAmount.confirmed(0), notApplicable));
				}
				continue;
			}
			// 2) 규칙 선택 단계 확인 필요
			if (blocked.containsKey(service.id())) {
				needingConfirmation.add(new ServiceNeedingConfirmation(service.id(), ConfirmationStage.RULE_SELECTION,
						blocked.get(service.id())));
				continue;
			}
			// 3) 전월 실적 미입력·미확인
			if (performance.isEmpty()) {
				needingConfirmation.add(new ServiceNeedingConfirmation(service.id(), ConfirmationStage.PRIOR_PERFORMANCE,
						List.of(missingPerformanceReason(state))));
				continue;
			}
			// 4) 신규 유예
			NewCardGraceJudge.Judgement grace = NewCardGraceJudge.judge(rule, service, registeredAt, order.date(),
					performance.get());
			if (grace instanceof NewCardGraceJudge.Judgement.Pending undecided) {
				Set<ApplicationStatus> unmet = ServiceCandidateEvaluator.performanceIndependentUnmet(service, order);
				if (unmet.isEmpty()) {
					needingConfirmation.add(new ServiceNeedingConfirmation(service.id(),
							ConfirmationStage.CANDIDATE_EVALUATION, List.of(undecided.reason())));
				}
				else {
					candidates.add(new ServiceCandidate(service.id(), BenefitAmount.confirmed(0), unmet));
				}
				continue;
			}
			// 5) 판정기 결과
			Won tierPerformance = grace instanceof NewCardGraceJudge.Judgement.Deemed deemed ? deemed.performance()
					: performance.get();
			TriggerEvidence graceEvidence = grace instanceof NewCardGraceJudge.Judgement.DefinitionUnresolved
					? TriggerEvidence.UNDETERMINED : TriggerEvidence.CONFIRMED_ABSENT;
			ServiceLimits limits = new ServiceLimits(rule.limitChain(service), usedThisMonth);
			ServiceCandidate candidate = ServiceCandidateEvaluator.evaluate(service, order, tierPerformance, graceEvidence,
					limits);
			if (candidate.standaloneAmount() instanceof BenefitAmount.NeedsConfirmation pending) {
				needingConfirmation.add(new ServiceNeedingConfirmation(service.id(),
						ConfirmationStage.CANDIDATE_EVALUATION, List.of(pending.reason()), candidate.limitReason()));
			}
			else {
				candidates.add(candidate);
			}
		}
		return new CardServiceCandidates.Collected(rule.ref(), candidates, needingConfirmation, unsupported,
				selected.unresolvedConditionalTerms());
	}

	/**
	 * 기준 기간이 주문 월이 아닌 사용액을 {@code UNKNOWN}으로 바꾼다(D-16). 모든 버킷에 같은 기준을 적용하므로 체인 어느 버킷이든 확인 필요가 된다.
	 */
	private static Map<LimitBucketId, StateValue<Won>> usedInOrderMonth(CardMonthlyState state, LocalDate orderDate) {
		BasisPeriod orderMonth = BasisPeriod.month(YearMonth.from(orderDate));
		Map<LimitBucketId, StateValue<Won>> used = new HashMap<>();
		state.usedBenefits().forEach((bucket, value) -> used.put(bucket,
				value.basisPeriod().filter(orderMonth::equals).isPresent() ? value
						: StateValue.unknown(value.basisPeriod().orElse(null), value.refreshedAt().orElse(null))));
		return used;
	}

	private static Optional<Won> knownPerformance(CardMonthlyState state) {
		return state.priorPerformance().filter(StateValue::isKnown).map(StateValue::knownValue);
	}

	private static String missingPerformanceReason(CardMonthlyState state) {
		return state.priorPerformance().isEmpty() ? "prior performance is not provided and has not been derived"
				: "prior performance is UNKNOWN";
	}

	/**
	 * 선택 결과의 계산 가능·확인 필요 서비스가 규칙의 서비스를 중복·누락 없이 나눴는지 확인한다.
	 */
	private static void requireConsistent(RuleSelection.Selected selected) {
		List<BenefitService> services = selected.rule().services();
		Set<ServiceId> ruleIds = new HashSet<>();
		services.forEach(service -> ruleIds.add(service.id()));
		Set<ServiceId> seen = new HashSet<>();
		for (BenefitService calculable : selected.calculableServices()) {
			if (!services.contains(calculable)) {
				throw new IllegalArgumentException("calculable service " + calculable.id() + " is not in rule "
						+ selected.rule().ref());
			}
			if (!seen.add(calculable.id())) {
				throw new IllegalArgumentException("calculable service " + calculable.id() + " is listed twice");
			}
		}
		for (Map.Entry<ServiceId, List<String>> entry : selected.servicesNeedingConfirmation().entrySet()) {
			ServiceId blocked = entry.getKey();
			// 사유 없는 확인 필요는 범위 밖 분류에서 확인 필요 표시가 사라지므로 거부한다.
			if (entry.getValue().isEmpty()) {
				throw new IllegalArgumentException("service " + blocked + " needs confirmation without a reason");
			}
			if (!ruleIds.contains(blocked)) {
				throw new IllegalArgumentException(
						"service " + blocked + " needing confirmation is not in rule " + selected.rule().ref());
			}
			if (!seen.add(blocked)) {
				throw new IllegalArgumentException("service " + blocked + " is both calculable and needing confirmation");
			}
		}
		if (!seen.equals(ruleIds)) {
			Set<ServiceId> missing = new HashSet<>(ruleIds);
			missing.removeAll(seen);
			throw new IllegalArgumentException("rule selection does not classify services " + missing);
		}
		// 공백 사유 검사는 기존 불일치 검사의 예외 순서를 바꾸지 않도록 마지막에 둔다.
		for (Map.Entry<ServiceId, List<String>> entry : selected.servicesNeedingConfirmation().entrySet()) {
			for (String reason : entry.getValue()) {
				if (reason.isBlank()) {
					throw new IllegalArgumentException("service " + entry.getKey() + " has a blank confirmation reason");
				}
			}
		}
	}

	private static Set<UnsupportedReason> unsupportedReasons(CardRuleVersion rule, BenefitService service) {
		Set<UnsupportedReason> reasons = EnumSet.noneOf(UnsupportedReason.class);
		// 한도 기간이 확정된 한도 서비스는 입력 사용액으로 판정한다(D-18 K1).
		if (!service.limitBuckets().isEmpty() && !rule.policy().limitPeriod().isResolved()) {
			reasons.add(UnsupportedReason.HAS_LIMIT_BUCKETS);
		}
		if (service.requiresSelection()) {
			reasons.add(UnsupportedReason.REQUIRES_SELECTION);
		}
		return reasons;
	}

	/**
	 * 범위 밖 서비스가 이 거래에 적용되지 않음이 확정된 사유(D-18 K1 U2 ① 예외).
	 */
	private static Set<ApplicationStatus> confirmedNotApplicable(CardRuleVersion rule, BenefitService service, Order order,
			CardMonthlyState state) {
		Set<ApplicationStatus> reasons = EnumSet.noneOf(ApplicationStatus.class);
		if (ServiceCandidateEvaluator.isConfirmedOutsideTarget(service, order)) {
			reasons.add(ApplicationStatus.NOT_APPLICABLE_CATEGORY);
		}
		if (service.requiresSelection()) {
			Optional<ServiceSelection> selection = effectiveSelection(rule, state, order);
			if (selection.isPresent() && !selection.get().service().equals(Optional.of(service.id()))) {
				reasons.add(ApplicationStatus.NOT_APPLICABLE_SELECTION);
			}
		}
		return reasons;
	}

	/**
	 * 주문일에 유효한 택1 선택. 입력이 없거나 {@code UNKNOWN}이 섞였거나, 적용일이 주문일보다 늦거나, 같은 날 값이 갈리거나, 규칙의 선택형
	 * 서비스가 아닌 값이면 비어 있다.
	 */
	private static Optional<ServiceSelection> effectiveSelection(CardRuleVersion rule, CardMonthlyState state, Order order) {
		List<ServiceSelection> known = new ArrayList<>();
		for (StateValue<ServiceSelection> value : state.serviceSelections()) {
			if (!value.isKnown()) {
				return Optional.empty();
			}
			if (!value.knownValue().effectiveFrom().isAfter(order.date())) {
				known.add(value.knownValue());
			}
		}
		Optional<LocalDate> latest = known.stream().map(ServiceSelection::effectiveFrom).max(LocalDate::compareTo);
		if (latest.isEmpty()) {
			return Optional.empty();
		}
		Set<ServiceSelection> onLatest = new HashSet<>();
		known.stream().filter(selection -> selection.effectiveFrom().equals(latest.get())).forEach(onLatest::add);
		if (onLatest.size() != 1) {
			return Optional.empty();
		}
		ServiceSelection selection = onLatest.iterator().next();
		boolean selectable = selection.service()
			.map(id -> rule.services().stream().anyMatch(service -> service.requiresSelection() && service.id().equals(id)))
			.orElse(true);
		return selectable ? Optional.of(selection) : Optional.empty();
	}

}
