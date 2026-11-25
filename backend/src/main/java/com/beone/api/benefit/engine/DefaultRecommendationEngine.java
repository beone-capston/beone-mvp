package com.beone.api.benefit.engine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.input.CardMonthlyState;
import com.beone.api.benefit.input.Order;
import com.beone.api.benefit.input.SelectionMode;
import com.beone.api.benefit.primitive.OrderBenefitDecider;
import com.beone.api.benefit.result.ApplicationStatus;
import com.beone.api.benefit.result.OrderBenefit;
import com.beone.api.benefit.result.PendingReason;
import com.beone.api.benefit.result.SelectionBasis;
import com.beone.api.benefit.rule.RuleCatalog;
import com.beone.api.benefit.rule.RuleSelection;
import com.beone.api.benefit.state.StateValue;

/**
 * 규칙 카탈로그로 보유 카드를 계산하고 D-12 순서로 카드를 고르는 엔진(D-22).
 *
 * <ol>
 * <li>선별: 사용자 제외 → {@code USER_EXCLUDED}, {@code NOT_SUPPORTED} → {@code NO_SUPPORTED_RULE}, {@code UNMAPPED}·{@code CANDIDATE}
 * → {@code MAPPING_UNCONFIRMED}. 가상 포트폴리오 카드는 매핑 대상이 아니라 규칙 카드로 바로 계산한다.</li>
 * <li>계산: 판정일 규칙을 고르고 결정기로 서비스별 채택·기여액을 정한다. 규칙이 없으면 {@code RULE_UNAVAILABLE}, 금액이 미확정이면 사유가
 * {@code UNSUPPORTED}뿐일 때 {@code UNSUPPORTED}, 그 밖은 {@code NEEDS_CONFIRMATION}으로 제외한다.</li>
 * <li>선택: 금액이 확정된 카드끼리 월간 합계 → 현재 주문 혜택 → 대표 카드 → 카드 ID 오름차순. 비교 카드가 0장이면 {@code NOT_DETERMINED},
 * 1장이면 근거와 차액이 없다. 사용자 제외를 뺀 제외 카드가 있으면 조건부다.</li>
 * <li>대표 카드 고정: {@code selected}는 그대로 D-12 최적 카드이고, 대표 카드와의 차이만 {@code fixedComparison}에 담는다.</li>
 * </ol>
 * 월간 합계는 현재 주문 혜택과 같다(D-18 K4). 예정 소비·원장·모의 취소는 다루지 않는다.
 */
@Component
public class DefaultRecommendationEngine implements RecommendationEngine {

	/** 예정 소비를 월간 합계에 넣지 않았다(D-18 K4). 항상 붙는다. */
	static final String NOTE_PLANNED_SPENDING_EXCLUDED = "PLANNED_SPENDING_EXCLUDED";

	/** 비교에서 빠진 카드가 더 나을 수 있다. */
	static final String NOTE_CONDITIONAL = "CONDITIONAL_RECOMMENDATION";

	/** 비교 카드가 1장이라 최적이라고 표시하지 않는다. */
	static final String NOTE_SINGLE_COMPARED_CARD = "SINGLE_COMPARED_CARD";

	/** 금액이 확정된 카드가 없다. */
	static final String NOTE_NO_COMPARABLE_CARD = "NO_COMPARABLE_CARD";

	private final RuleCatalog catalog;

	public DefaultRecommendationEngine(RuleCatalog catalog) {
		this.catalog = Objects.requireNonNull(catalog, "catalog");
	}

	@Override
	public EngineResult recommend(EngineRequest request) {
		Objects.requireNonNull(request, "request");
		LocalDate date = request.decisionDate();
		Order order = EngineInputs.order(request.order());

		List<ComparedCard> compared = new ArrayList<>();
		List<ExcludedCard> excluded = new ArrayList<>();
		List<BucketUsageEffect> effects = new ArrayList<>();
		for (HeldCardInput card : request.cards()) {
			ExcludedCard screened = screen(card);
			if (screened != null) {
				excluded.add(screened);
				continue;
			}
			CardId cardId = CardId.of(card.cardProductId());
			RuleSelection selection = catalog.select(cardId, date);
			CardMonthlyState state = EngineInputs.monthlyState(cardId, card.monthlyState(), date);
			StateValue<LocalDate> registeredAt = EngineInputs.registeredAt(card.registeredOn());
			OrderBenefit result = OrderBenefitDecider.decide(selection, order, state, registeredAt,
					EngineInputs.evidence(cardId, card.policyEvidence()));
			switch (result) {
				case OrderBenefit.Unavailable unavailable -> excluded.add(ruleUnavailable(card, unavailable));
				case OrderBenefit.Decided decided -> {
					RuleSelection.Selected selected = (RuleSelection.Selected) selection;
					CardEvaluation evaluation = CardEvaluator.evaluate(card.userCardId(), selected.rule(), decided, state,
							registeredAt, date);
					effects.addAll(evaluation.usageEffects());
					if (evaluation.isConfirmed()) {
						compared.add(compared(card, evaluation));
					}
					else {
						excluded.add(unconfirmed(card, evaluation));
					}
				}
			}
		}

		SelectedCard selected = select(compared, request.representativeUserCardId());
		boolean determined = selected != null;
		boolean conditional = determined && excluded.stream().anyMatch(card -> !"USER_EXCLUDED".equals(card.reason()));
		FixedComparison fixed = SelectionMode.FIXED_REPRESENTATIVE.name().equals(request.selectionMode())
				? fixedComparison(request.representativeUserCardId(), selected, compared) : null;
		return new EngineResult(date, determined ? "SELECTED" : "NOT_DETERMINED", conditional, selected, compared, excluded,
				fixed, noteCodes(determined, conditional, compared.size()), effects);
	}

	/**
	 * 계산 전에 빠지는 카드. 계산할 카드면 null이다.
	 */
	private static ExcludedCard screen(HeldCardInput card) {
		if (card.excludedByUser()) {
			return notCalculated(card, "USER_EXCLUDED", "사용자가 추천에서 제외한 카드", null);
		}
		if (card.mappingStatus() == null || "USER_CONFIRMED".equals(card.mappingStatus())) {
			return null;
		}
		if ("NOT_SUPPORTED".equals(card.mappingStatus())) {
			return notCalculated(card, "NO_SUPPORTED_RULE", "검증된 규칙 없음(지원 범위 밖)", null);
		}
		return notCalculated(card, "MAPPING_UNCONFIRMED", "카드 상품 매핑이 확인되지 않음",
				card.candidateCardProductId());
	}

	private static ExcludedCard notCalculated(HeldCardInput card, String reason, String detail, String candidate) {
		return new ExcludedCard(card.userCardId(), card.cardProductId(), reason, detail, candidate,
				BenefitAmountOutput.needsConfirmation(), null, null, List.of());
	}

	private static ExcludedCard ruleUnavailable(HeldCardInput card, OrderBenefit.Unavailable unavailable) {
		String detail = unavailable.status() == ApplicationStatus.RULE_EXPIRED ? "판정일에 유효한 규칙 버전이 만료됨"
				: "판정일에 유효한 VERIFIED 규칙이 없음";
		return new ExcludedCard(card.userCardId(), card.cardProductId(), "RULE_UNAVAILABLE", detail, null,
				BenefitAmountOutput.needsConfirmation(), unavailable.status().name(), null, List.of());
	}

	private static ExcludedCard unconfirmed(HeldCardInput card, CardEvaluation evaluation) {
		boolean unsupportedOnly = evaluation.source()
			.reasons()
			.stream()
			.allMatch(reason -> reason.kind() == PendingReason.Kind.UNSUPPORTED);
		String reason = unsupportedOnly ? "UNSUPPORTED" : "NEEDS_CONFIRMATION";
		String detail = unsupportedOnly ? "지원 범위 밖 서비스 때문에 카드 금액을 확정하지 못함"
				: "카드 금액을 확정하지 못함. 사유는 pendingReasons에 있음";
		return new ExcludedCard(card.userCardId(), card.cardProductId(), reason, detail, null,
				BenefitAmountOutput.needsConfirmation(), evaluation.benefit().status().name(), evaluation.appliedRule(),
				evaluation.pendingReasons());
	}

	private static ComparedCard compared(HeldCardInput card, CardEvaluation evaluation) {
		long amount = evaluation.benefit().currentOrderBenefit().confirmedWon().value();
		long monthly = evaluation.benefit().monthlyTotal().confirmedWon().value();
		return new ComparedCard(card.userCardId(), card.cardProductId(), evaluation.benefit().status().name(),
				evaluation.appliedRule(), BenefitAmountOutput.confirmed(amount), BenefitAmountOutput.confirmed(monthly),
				evaluation.services(), amount == 0 ? "CONFIRMED_NOT_MET" : null, evaluation.policyNotes(),
				evaluation.pendingReasons());
	}

	/**
	 * D-12 순서로 고른다. 비교 카드가 없으면 null이다.
	 */
	static SelectedCard select(List<ComparedCard> compared, String representativeUserCardId) {
		if (compared.isEmpty()) {
			return null;
		}
		List<ComparedCard> ranked = compared.stream()
			.sorted(Comparator.comparingLong(CardSelectionKeys::monthly)
				.reversed()
				.thenComparing(Comparator.comparingLong(CardSelectionKeys::current).reversed())
				.thenComparing(ComparedCard::cardProductId))
			.toList();
		ComparedCard top = ranked.get(0);
		if (ranked.size() == 1) {
			return new SelectedCard(top.userCardId(), top.cardProductId(), null, false, List.of(), null);
		}
		ComparedCard second = ranked.get(1);
		long monthlyGap = CardSelectionKeys.monthly(top) - CardSelectionKeys.monthly(second);
		if (monthlyGap > 0) {
			return new SelectedCard(top.userCardId(), top.cardProductId(), SelectionBasis.MONTHLY_TOTAL.name(), false,
					List.of(), monthlyGap);
		}
		long currentGap = CardSelectionKeys.current(top) - CardSelectionKeys.current(second);
		if (currentGap > 0) {
			return new SelectedCard(top.userCardId(), top.cardProductId(), SelectionBasis.CURRENT_ORDER_BENEFIT.name(),
					false, List.of(), currentGap);
		}
		List<ComparedCard> tied = ranked.stream()
			.filter(card -> CardSelectionKeys.monthly(card) == CardSelectionKeys.monthly(top)
					&& CardSelectionKeys.current(card) == CardSelectionKeys.current(top))
			.toList();
		List<String> tiedIds = tied.stream().map(ComparedCard::userCardId).toList();
		ComparedCard representative = tied.stream()
			.filter(card -> card.userCardId().equals(representativeUserCardId))
			.findFirst()
			.orElse(null);
		if (representative != null) {
			return new SelectedCard(representative.userCardId(), representative.cardProductId(),
					SelectionBasis.REPRESENTATIVE_CARD.name(), true, tiedIds, 0L);
		}
		return new SelectedCard(top.userCardId(), top.cardProductId(), SelectionBasis.CARD_ID_ASC.name(), true, tiedIds, 0L);
	}

	private static FixedComparison fixedComparison(String representativeUserCardId, SelectedCard selected,
			List<ComparedCard> compared) {
		ComparedCard representative = compared.stream()
			.filter(card -> card.userCardId().equals(representativeUserCardId))
			.findFirst()
			.orElse(null);
		if (selected == null) {
			return new FixedComparison(representativeUserCardId, null, null);
		}
		ComparedCard optimal = compared.stream()
			.filter(card -> card.userCardId().equals(selected.userCardId()))
			.findFirst()
			.orElseThrow();
		Long difference = representative == null ? null
				: CardSelectionKeys.monthly(optimal) - CardSelectionKeys.monthly(representative);
		return new FixedComparison(representativeUserCardId, selected.userCardId(), difference);
	}

	private static List<String> noteCodes(boolean determined, boolean conditional, int comparedCount) {
		List<String> codes = new ArrayList<>();
		codes.add(NOTE_PLANNED_SPENDING_EXCLUDED);
		if (!determined) {
			codes.add(NOTE_NO_COMPARABLE_CARD);
		}
		if (conditional) {
			codes.add(NOTE_CONDITIONAL);
		}
		if (comparedCount == 1) {
			codes.add(NOTE_SINGLE_COMPARED_CARD);
		}
		return codes;
	}

	/** 비교 카드의 확정 금액. 비교 카드는 생성 때 확정 금액이 검증되어 있다. */
	private static final class CardSelectionKeys {

		private CardSelectionKeys() {
		}

		static long monthly(ComparedCard card) {
			return card.monthlyTotalBenefit().amountWon();
		}

		static long current(ComparedCard card) {
			return card.currentOrderBenefit().amountWon();
		}

	}

}
