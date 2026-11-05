package com.beone.api.benefit.primitive;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.beone.api.benefit.card.ServiceId;
import com.beone.api.benefit.input.CardMonthlyState;
import com.beone.api.benefit.input.Order;
import com.beone.api.benefit.input.PolicyTermEvidence;
import com.beone.api.benefit.money.BenefitAmount;
import com.beone.api.benefit.result.Adoption;
import com.beone.api.benefit.result.ApplicationStatus;
import com.beone.api.benefit.state.StateValue;
import com.beone.api.benefit.rule.LimitBucket;
import com.beone.api.benefit.result.ServiceResultNotes;
import com.beone.api.benefit.result.LimitReason;
import com.beone.api.benefit.result.CardServiceCandidates;
import com.beone.api.benefit.result.OrderBenefit;
import com.beone.api.benefit.result.OrderServiceBenefit;
import com.beone.api.benefit.result.PendingReason;
import com.beone.api.benefit.result.PerformanceRecheck;
import com.beone.api.benefit.result.ServiceCandidate;
import com.beone.api.benefit.result.ServiceNeedingConfirmation;
import com.beone.api.benefit.result.UnsupportedService;
import com.beone.api.benefit.rule.BenefitService;
import com.beone.api.benefit.rule.CardRuleVersion;
import com.beone.api.benefit.rule.ConditionalTerm;
import com.beone.api.benefit.rule.RuleSelection;
import com.beone.api.benefit.rule.RuleTerm;
import com.beone.api.benefit.rule.StackingBasis;
import com.beone.api.benefit.rule.TriggerEvidence;

/**
 * 카드 한 장·주문 한 건의 서비스별 최종 채택·기여액과 카드 금액을 정한다(#35, D-13).
 *
 * <p>
 * {@link CardServiceCandidateCollector}로 후보를 취합한 뒤 아래 순서로 판단한다. 규칙 선택({@code RuleCatalog.select})은 호출하지 않는다.
 * <ol>
 * <li>실적과 무관한 미충족(제외 업종·업종·결제 방식·최소 금액·택1 선택)이 있는 후보: {@code NOT_ADOPTED}·확정 0원.</li>
 * <li>나머지 후보는 실적 정책 영향을 본다. 서비스나 그 한도 체인에 실적 조건이 있고, 원본 {@code unresolvedConditionalTerms}에 취소 귀속·할인 매출
 * 실적 제외 조건이 있으며, 그 근거가 {@code CONFIRMED_ABSENT}가 아니면 영향이 있다고 본다.</li>
 * <li>실적 미충족·한도 소진만 있는 후보와 미충족 사유 없는 확정 0원 후보: 영향이 있으면 재확인 대상, 없으면 {@code NOT_ADOPTED}·확정 0원.</li>
 * <li>양수 후보: 영향이 있으면 실적 정책 사유를 남긴다.</li>
 * <li>막는 사유를 모두 모은다: 확인 필요·지원 범위 밖 서비스, 실적 정책 영향. 양수 후보의 모든 쌍이 합산 조건(D-13 #35 8번)을 만족하지 않으면 쌍의
 * 중복 관계 미확정·중복 허용 확정도 사유다.</li>
 * <li>다른 사유가 없고 모든 쌍이 합산 조건을 만족하면 합계를 결제 금액과 비교한다. 넘으면 합계 초과 사유를 남기고, 넘지 않으면 양수 후보를 모두 채택한다.</li>
 * <li>사유가 없고 합산 대상이 아니면 양수 후보 중 한도 적용 후 금액이 가장 크고 같으면 서비스 ID가 가장 앞선 하나만 채택한다. 사유가 있으면 양수 후보는 모두
 * {@code UNDETERMINED}이고 기여액과 카드 금액은 {@code 확인 필요}다.</li>
 * </ol>
 * 결과는 입력·컬렉션 순서와 무관하게 서비스 ID 순으로 정렬한다. 원본 취합 결과와 그 조건부 조건은 바꾸지 않고 보존한다.
 */
public final class OrderBenefitDecider {

	/** 실적 미충족 외의 미충족. 하나라도 있으면 실적이 바뀌어도 확정 0원이다. */
	private static final Set<ApplicationStatus> PERFORMANCE_INDEPENDENT_UNMET = EnumSet.of(
			ApplicationStatus.NOT_APPLICABLE_EXCLUDED, ApplicationStatus.NOT_APPLICABLE_CATEGORY,
			ApplicationStatus.NOT_APPLICABLE_MIN_AMOUNT, ApplicationStatus.NOT_APPLICABLE_PAYMENT_METHOD,
			ApplicationStatus.NOT_APPLICABLE_SELECTION);

	private OrderBenefitDecider() {
	}

	/**
	 * 카드 한 장의 주문 혜택을 정한다.
	 *
	 * @param selection 카드 한 장의 규칙 선택 결과
	 * @param order 현재 주문
	 * @param state 같은 카드의 월간 상태
	 * @param registeredAt 같은 카드의 등록일. 신규 유예 판정에 쓴다
	 * @param evidence 같은 카드의 취소 귀속·할인 매출 근거. 실적 기간 적합성은 호출자 책임이다
	 * @return 규칙을 쓸 수 없으면 {@link OrderBenefit.Unavailable}, 아니면 {@link OrderBenefit.Decided}
	 * @throws NullPointerException 입력이 null인 경우
	 * @throws IllegalArgumentException 근거의 카드가 규칙 선택 결과의 카드와 다르거나, 후보 취합기가 거부하는 입력인 경우
	 */
	public static OrderBenefit decide(RuleSelection selection, Order order, CardMonthlyState state,
			StateValue<LocalDate> registeredAt, PolicyTermEvidence evidence) {
		Objects.requireNonNull(selection, "selection");
		Objects.requireNonNull(order, "order");
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(evidence, "evidence");
		if (!evidence.cardId().equals(selection.cardId())) {
			throw new IllegalArgumentException(
					"policy evidence card " + evidence.cardId() + " does not match rule selection card " + selection.cardId());
		}
		return switch (CardServiceCandidateCollector.collect(selection, order, state, registeredAt)) {
			case CardServiceCandidates.Unavailable unavailable -> new OrderBenefit.Unavailable(unavailable.cardId(),
					unavailable.status(), unavailable.reason());
			case CardServiceCandidates.Collected collected -> decided((RuleSelection.Selected) selection, collected,
					order, evidence);
		};
	}

	/**
	 * 취합된 후보로 서비스별 채택·기여액, 재확인 대상, 막는 사유와 카드 금액을 만든다.
	 *
	 * <p>
	 * 취합기가 {@code Collected}를 돌려준 경우에만 호출하며, 그때 규칙 선택 결과는 {@code Selected}다. 후보마다 실적과 무관한 미충족은
	 * {@code NOT_ADOPTED}·확정 0원, 실적 정책 영향(U4)이 있으면 사유를 남기고 0원 후보는 재확인 대상으로 분리하며, 영향 없는 0원 후보는
	 * {@code NOT_ADOPTED}·확정 0원(U6)이다. 확인 필요·지원 범위 밖 서비스와 양수 후보 쌍의 중복 관계도 사유로 모은다(U2). 사유가 없으면
	 * 합산 대상은 양수 후보를 모두 채택하고(D-13 #35 8번), 그 밖에는 최댓값, 같으면 서비스 ID가 앞선 후보 하나만 채택하며(U3) 양수 후보가 없으면 카드
	 * 금액은 확정 0원이다. 사유가 있으면 양수 후보는 모두 {@code UNDETERMINED}이고 기여액과 카드 금액은 같은 {@code 확인 필요} 값이다.
	 *
	 * <p>
	 * 확정 후보 금액을 바꾸거나 선택형을 계산하지 않는다. 채택된 후보의 기여액만 남은 한도로 줄이며, 합계에 다시 절사하지 않는다. 결과의 대응·정렬·채택 불변식은 {@link OrderBenefit.Decided} 생성자가
	 * 다시 확인한다.
	 *
	 * @param selection 취합에 쓴 규칙 선택 결과. 서비스 ID로 실적 조건·중복 관계를 찾는다
	 * @param source 취합 결과. 그대로 결과에 보존한다
	 * @param order 현재 주문. 합산 합계와 비교할 결제 금액을 쓴다
	 * @param evidence 같은 카드의 취소 귀속·할인 매출 근거
	 * @return 서비스 ID 순으로 정렬한 결정 결과
	 */
	private static OrderBenefit.Decided decided(RuleSelection.Selected selection,
			CardServiceCandidates.Collected source, Order order, PolicyTermEvidence evidence) {
		Map<ServiceId, BenefitService> servicesById = selection.rule()
			.services()
			.stream()
			.collect(Collectors.toMap(BenefitService::id, Function.identity()));
		List<String> policyTerms = affectingPolicyTerms(source, evidence);

		List<ServiceCandidate> candidates = source.candidates()
			.stream()
			.sorted(Comparator.comparing(ServiceCandidate::serviceId, PendingReason.SERVICE_ORDER))
			.toList();
		List<OrderServiceBenefit> settled = new ArrayList<>();
		List<ServiceCandidate> positives = new ArrayList<>();
		List<PerformanceRecheck> rechecks = new ArrayList<>();
		List<PendingReason> reasons = new ArrayList<>();
		for (ServiceCandidate candidate : candidates) {
			Set<ApplicationStatus> unmet = candidate.unmetConditions();
			// 1) 실적과 무관한 미충족은 실적 정책과 관계없이 확정 0원이다.
			if (unmet.stream().anyMatch(PERFORMANCE_INDEPENDENT_UNMET::contains)) {
				settled.add(notAdopted(candidate));
				continue;
			}
			// 2) 실적 정책 조건이 이 서비스의 실적 충족·구간·금액을 바꿀 수 있는지
			BenefitService service = servicesById.get(candidate.serviceId());
			boolean affected = !policyTerms.isEmpty() && (service.hasPerformanceRequirement()
					|| selection.rule().limitChain(service).stream().anyMatch(LimitBucket::dependsOnPerformance));
			if (affected) {
				reasons.add(new PendingReason(PendingReason.Kind.PERFORMANCE_POLICY, List.of(candidate.serviceId()),
						"service " + candidate.serviceId() + " depends on prior performance affected by "
								+ String.join("; ", policyTerms)));
			}
			boolean positive = candidate.standaloneAmount().confirmedWon().isPositive();
			if (positive) {
				positives.add(candidate);
			}
			else if (affected) {
				// 3) 실적 미충족만 있거나 사유 없는 0원이지만 정책 조건에 따라 달라질 수 있다.
				rechecks.add(new PerformanceRecheck(candidate, policyTerms));
			}
			else {
				settled.add(notAdopted(candidate));
			}
		}
		for (ServiceNeedingConfirmation pending : source.needingConfirmation()) {
			reasons.add(new PendingReason(PendingReason.Kind.NEEDS_CONFIRMATION, List.of(pending.serviceId()),
					"service " + pending.serviceId() + " needs confirmation at " + pending.stage() + ": "
							+ String.join("; ", pending.reasons())));
		}
		for (UnsupportedService unsupported : source.unsupported()) {
			reasons.add(new PendingReason(PendingReason.Kind.UNSUPPORTED, List.of(unsupported.serviceId()),
					unsupportedDetail(unsupported)));
		}
		// 모든 쌍이 합산 조건을 만족하면 쌍 사유를 남기지 않는다. 다른 사유가 있으면 합산하지 않고 그 사유로 막는다.
		boolean stacked = positives.size() > 1 && allPairsMeetStackingConditions(positives, servicesById, selection.rule());
		if (!stacked) {
			reasons.addAll(stackingReasons(positives, servicesById));
		}
		else if (reasons.isEmpty() && contributionsExceedPayment(positives, order.amount().value())) {
			reasons.add(new PendingReason(PendingReason.Kind.STACKING_EXCEEDS_PAYMENT,
					positives.stream().map(ServiceCandidate::serviceId).toList(),
					"stacked contributions exceed payment amount " + order.amount().value()));
		}
		reasons.sort(PendingReason.ORDER);

		List<OrderServiceBenefit> services = new ArrayList<>(settled);
		BenefitAmount orderBenefit;
		if (reasons.isEmpty() && stacked) {
			// 4) 합산 예외: 양수 후보 모두 한도 적용 후 금액으로 채택하고 카드 금액은 그 합이다.
			for (ServiceCandidate candidate : positives) {
				services.add(new OrderServiceBenefit(candidate, Adoption.ADOPTED, candidate.amountAfterLimit()));
			}
			orderBenefit = BenefitAmount.confirmed(contributionTotal(positives));
		}
		else if (reasons.isEmpty()) {
			// 5) 막는 사유가 없으면 양수 후보끼리 모두 중복 불가로 확정된 상태다. 한도 적용 후 최댓값, 같으면 서비스 ID가 앞선 하나만 채택한다.
			Optional<ServiceCandidate> best = positives.stream()
				.min(Comparator.comparing((ServiceCandidate candidate) -> candidate.amountAfterLimit().confirmedWon())
					.reversed()
					.thenComparing(ServiceCandidate::serviceId, PendingReason.SERVICE_ORDER));
			for (ServiceCandidate candidate : positives) {
				services.add(candidate.equals(best.get())
						? new OrderServiceBenefit(candidate, Adoption.ADOPTED, candidate.amountAfterLimit())
						: notAdopted(candidate));
			}
			orderBenefit = best.map(ServiceCandidate::amountAfterLimit).orElse(BenefitAmount.confirmed(0));
		}
		else {
			BenefitAmount pending = BenefitAmount.needsConfirmation(pendingSummary(reasons));
			for (ServiceCandidate candidate : positives) {
				services.add(new OrderServiceBenefit(candidate, Adoption.UNDETERMINED, pending));
			}
			orderBenefit = pending;
		}
		services.sort(Comparator.comparing(service -> service.candidate().serviceId(), PendingReason.SERVICE_ORDER));
		return new OrderBenefit.Decided(source, services, rechecks, reasons, orderBenefit,
				serviceNotes(selection.rule().services(), source));
	}

	/**
	 * 규칙의 모든 서비스에 표시 값을 만든다. 서비스 ID 순이다.
	 */
	private static List<ServiceResultNotes> serviceNotes(List<BenefitService> services,
			CardServiceCandidates.Collected source) {
		Map<ServiceId, LimitReason> limitReasons = new HashMap<>();
		for (ServiceNeedingConfirmation pending : source.needingConfirmation()) {
			pending.limitReason().ifPresent(reason -> limitReasons.put(pending.serviceId(), reason));
		}
		Set<ServiceId> truncated = source.candidates()
			.stream()
			.filter(ServiceCandidate::fractionalWonTruncated)
			.map(ServiceCandidate::serviceId)
			.collect(Collectors.toSet());
		return services.stream()
			.map(service -> ServiceResultNotes.of(service, Optional.ofNullable(limitReasons.get(service.id())),
					truncated.contains(service.id())))
			.sorted(Comparator.comparing(ServiceResultNotes::serviceId, PendingReason.SERVICE_ORDER))
			.toList();
	}

	/**
	 * 원본 조건부 조건 중 실적 정책(취소 귀속·할인 매출 실적 제외)이면서 근거로 무시할 수 없는 조건의 설명.
	 *
	 * <p>
	 * 근거가 {@code CONFIRMED_ABSENT}일 때만 조건을 무시한다(U4). 실적 정책이 아닌 조건은 근거가 없으므로 제외한다. 서비스별 실적 조건
	 * 유무는 보지 않으며 호출 쪽이 함께 판단한다.
	 *
	 * @param source 조건부 조건을 담은 취합 결과
	 * @param evidence 같은 카드의 취소 귀속·할인 매출 근거
	 * @return 영향 가능 조건의 설명. 원본 조건 순서이며 없으면 빈 목록
	 */
	private static List<String> affectingPolicyTerms(CardServiceCandidates.Collected source,
			PolicyTermEvidence evidence) {
		List<String> terms = new ArrayList<>();
		for (ConditionalTerm term : source.unresolvedConditionalTerms()) {
			Optional<TriggerEvidence> found = evidence.evidenceFor(term.trigger());
			if (found.isPresent() && !term.mayBeDisregarded(found.get())) {
				terms.add(term.name() + ": " + term.reason() + " (evidence " + found.get() + ")");
			}
		}
		return List.copyOf(terms);
	}

	/**
	 * 양수 후보의 모든 쌍이 합산 조건을 만족하는지 본다(D-13 #35 8번). 규칙 값으로만 판단하며 카드 ID를 보지 않는다.
	 *
	 * @param positives 양수 후보
	 * @param servicesById 규칙의 서비스. 모든 후보의 서비스가 있어야 한다
	 * @param rule 한도 체인을 찾을 규칙
	 * @return 모든 쌍이 조건을 만족하면 참
	 */
	private static boolean allPairsMeetStackingConditions(List<ServiceCandidate> positives,
			Map<ServiceId, BenefitService> servicesById, CardRuleVersion rule) {
		for (int i = 0; i < positives.size(); i++) {
			for (int j = i + 1; j < positives.size(); j++) {
				if (!meetsStackingConditions(servicesById.get(positives.get(i).serviceId()),
						servicesById.get(positives.get(j).serviceId()), rule)) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * 한 쌍의 합산 조건: 양쪽 {@code stackableWith} 확정·대칭, 양쪽 같은 계산 기준 명시, 한도 체인 비겹침.
	 */
	private static boolean meetsStackingConditions(BenefitService left, BenefitService right, CardRuleVersion rule) {
		if (!left.stackableWith().isResolved() || !right.stackableWith().isResolved()
				|| !left.stackableWith().resolvedValue().contains(right.id())
				|| !right.stackableWith().resolvedValue().contains(left.id())) {
			return false;
		}
		Optional<StackingBasis> basis = left.details().stackingBasis();
		if (basis.isEmpty() || !basis.equals(right.details().stackingBasis())) {
			return false;
		}
		return Collections.disjoint(rule.limitChain(left), rule.limitChain(right));
	}

	/**
	 * 한도 적용 후 금액이 결제 금액을 넘는지 본다. 합계를 만들지 않고 결제 금액에서 차례로 빼므로 오버플로가 없다. 같으면 넘지 않는다.
	 */
	private static boolean contributionsExceedPayment(List<ServiceCandidate> positives, long payment) {
		long remaining = payment;
		for (ServiceCandidate candidate : positives) {
			remaining -= candidate.amountAfterLimit().confirmedWon().value();
			if (remaining < 0) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 양수 후보의 한도 적용 후 금액 합계. 결제 금액을 넘지 않음을 확인한 뒤에만 쓴다. 서비스별 절사·한도 적용이 끝난 값을 더하기만 한다.
	 */
	private static long contributionTotal(List<ServiceCandidate> positives) {
		long total = 0;
		for (ServiceCandidate candidate : positives) {
			total = Math.addExact(total, candidate.amountAfterLimit().confirmedWon().value());
		}
		return total;
	}

	/**
	 * 합산 대상이 아닌 주문에서 양수 후보 쌍마다 중복 관계를 본다. 양쪽이 확정이고 서로를 적지 않았을 때만 중복 불가로 확정한다.
	 *
	 * <p>
	 * 한쪽이라도 미확정이거나 한쪽에만 적혀 있으면 {@code STACKING_UNRESOLVED}, 양쪽이 서로를 적었으면 {@code STACKING_CONFIRMED}다(U2).
	 *
	 * @param positives 서비스 ID 오름차순의 양수 후보. 쌍의 서비스 순서가 이 순서를 따른다
	 * @param servicesById 규칙의 서비스. 모든 후보의 서비스가 있어야 한다
	 * @return 쌍 단위 사유. 중복 불가로 확정된 쌍은 없다
	 */
	private static List<PendingReason> stackingReasons(List<ServiceCandidate> positives,
			Map<ServiceId, BenefitService> servicesById) {
		List<PendingReason> reasons = new ArrayList<>();
		for (int i = 0; i < positives.size(); i++) {
			for (int j = i + 1; j < positives.size(); j++) {
				BenefitService left = servicesById.get(positives.get(i).serviceId());
				BenefitService right = servicesById.get(positives.get(j).serviceId());
				List<ServiceId> pair = List.of(left.id(), right.id());
				RuleTerm<Set<ServiceId>> leftTerm = left.stackableWith();
				RuleTerm<Set<ServiceId>> rightTerm = right.stackableWith();
				if (!leftTerm.isResolved() || !rightTerm.isResolved()) {
					reasons.add(new PendingReason(PendingReason.Kind.STACKING_UNRESOLVED, pair,
							"stacking of " + left.id() + " and " + right.id() + " is not confirmed"));
					continue;
				}
				boolean leftLists = leftTerm.resolvedValue().contains(right.id());
				boolean rightLists = rightTerm.resolvedValue().contains(left.id());
				if (leftLists && rightLists) {
					reasons.add(new PendingReason(PendingReason.Kind.STACKING_CONFIRMED, pair, "stacking of " + left.id()
							+ " and " + right.id() + " is allowed, but this order does not meet the stacking exception conditions"));
				}
				else if (leftLists || rightLists) {
					reasons.add(new PendingReason(PendingReason.Kind.STACKING_UNRESOLVED, pair, "stacking of " + left.id()
							+ " and " + right.id() + " is listed on one side only"));
				}
			}
		}
		return reasons;
	}

	/**
	 * 지원 범위 밖 서비스 사유의 설명. 지원하지 않는 이유는 이름순으로, 규칙 선택 단계 사유는 원문 그대로 붙인다.
	 *
	 * @param unsupported 지원 범위 밖 서비스
	 * @return 사람이 읽는 설명
	 */
	private static String unsupportedDetail(UnsupportedService unsupported) {
		String detail = "service " + unsupported.serviceId() + " is not decided by this feature: "
				+ unsupported.reasons().stream().map(Enum::name).sorted().collect(Collectors.joining(", "));
		if (!unsupported.selectionReasons().isEmpty()) {
			detail += "; rule selection: " + String.join("; ", unsupported.selectionReasons());
		}
		return detail;
	}

	/**
	 * 막는 사유가 있을 때 양수 후보 기여액과 카드 금액에 쓰는 {@code 확인 필요} 문구. 사유의 종류와 서비스만 나열하고 설명은 사유 목록에 남긴다.
	 *
	 * @param reasons 정렬된 막는 사유. 비어 있지 않다
	 * @return 공백이 아닌 확인 필요 문구
	 */
	private static String pendingSummary(List<PendingReason> reasons) {
		return "order benefit needs confirmation: " + reasons.stream()
			.map(reason -> reason.kind() + " " + reason.services())
			.collect(Collectors.joining(", "));
	}

	/**
	 * 후보를 채택하지 않고 기여액을 확정 0원으로 둔다. 원본 후보 금액은 바꾸지 않는다.
	 *
	 * @param candidate 원본 후보 판정
	 * @return {@code NOT_ADOPTED}·확정 0원 결과
	 */
	private static OrderServiceBenefit notAdopted(ServiceCandidate candidate) {
		return new OrderServiceBenefit(candidate, Adoption.NOT_ADOPTED, BenefitAmount.confirmed(0));
	}

}
