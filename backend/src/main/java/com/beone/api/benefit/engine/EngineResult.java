package com.beone.api.benefit.engine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 엔진 입구의 출력. {@code docs/api/recommendations.md} 응답 중 업무 API가 채우는 필드를 뺀 전부를 담는다.
 *
 * <p>비교 카드가 0장이면 {@code NOT_DETERMINED}다. 사용자 제외를 뺀 제외 카드가 있으면 조건부이고, 사용자 제외만 있으면
 * 조건부가 아니다(D-22).
 *
 * @param decisionDate 판정일
 * @param outcome {@code SELECTED}·{@code NOT_DETERMINED}
 * @param conditional 비교에서 빠진 카드가 더 나을 수 있으면 참
 * @param selected 고른 카드. {@code NOT_DETERMINED}면 null
 * @param comparedCards 비교한 카드
 * @param excludedCards 제외한 카드
 * @param fixedComparison 대표 카드 고정일 때의 비교. 자동 추천이면 null
 * @param noteCodes 안내 문구 코드. 문구는 업무 API가 정한다
 * @param usageEffects 카드별 차감 정보
 */
public record EngineResult(LocalDate decisionDate, String outcome, boolean conditional, SelectedCard selected,
		List<ComparedCard> comparedCards, List<ExcludedCard> excludedCards, FixedComparison fixedComparison,
		List<String> noteCodes, List<BucketUsageEffect> usageEffects) {

	public EngineResult {
		Objects.requireNonNull(decisionDate, "decisionDate");
		EngineChecks.requireOneOf(Set.of("SELECTED", "NOT_DETERMINED"), outcome, "outcome");
		comparedCards = EngineChecks.copy(comparedCards, "comparedCards");
		excludedCards = EngineChecks.copy(excludedCards, "excludedCards");
		noteCodes = EngineChecks.copy(noteCodes, "noteCodes");
		noteCodes.forEach(code -> EngineChecks.requireToken(code, "noteCode"));
		usageEffects = EngineChecks.copy(usageEffects, "usageEffects");

		List<String> comparedIds = comparedCards.stream().map(ComparedCard::userCardId).toList();
		List<String> allIds = new ArrayList<>(comparedIds);
		excludedCards.forEach(card -> allIds.add(card.userCardId()));
		EngineChecks.requireDistinct(allIds, "userCardId");

		checkOutcome(outcome, conditional, selected, comparedCards, excludedCards);
		checkFixedComparison(fixedComparison, selected, allIds, comparedIds);
		checkUsageEffects(usageEffects, allIds);
	}

	private static void checkOutcome(String outcome, boolean conditional, SelectedCard selected,
			List<ComparedCard> compared, List<ExcludedCard> excluded) {
		if ("NOT_DETERMINED".equals(outcome)) {
			if (selected != null || !compared.isEmpty() || conditional) {
				throw new IllegalArgumentException("NOT_DETERMINED has no compared card, selection or condition");
			}
			return;
		}
		Objects.requireNonNull(selected, "selected");
		ComparedCard card = compared.stream()
			.filter(candidate -> candidate.userCardId().equals(selected.userCardId()))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("the selected card must be compared"));
		if (!card.cardProductId().equals(selected.cardProductId())) {
			throw new IllegalArgumentException("the selected card product must match");
		}
		if ((compared.size() == 1) != (selected.decidedBy() == null)) {
			throw new IllegalArgumentException("decidedBy is null exactly when one card was compared");
		}
		boolean expected = excluded.stream().anyMatch(candidate -> !"USER_EXCLUDED".equals(candidate.reason()));
		if (conditional != expected) {
			throw new IllegalArgumentException("conditional is true exactly when a card other than a user exclusion is out");
		}
		if (!compared.stream().map(ComparedCard::userCardId).toList().containsAll(selected.tiedUserCardIds())) {
			throw new IllegalArgumentException("tied cards must be compared");
		}
	}

	private static void checkFixedComparison(FixedComparison fixed, SelectedCard selected, List<String> allIds,
			List<String> comparedIds) {
		if (fixed == null) {
			return;
		}
		if (!allIds.contains(fixed.representativeUserCardId())) {
			throw new IllegalArgumentException("the representative card must be in the result");
		}
		String optimal = selected == null ? null : selected.userCardId();
		if (!Objects.equals(fixed.optimalUserCardId(), optimal)) {
			throw new IllegalArgumentException("the optimal card must be the selected card");
		}
		if (fixed.differenceWon() != null && !comparedIds.contains(fixed.representativeUserCardId())) {
			throw new IllegalArgumentException("a difference needs a representative card with a confirmed benefit");
		}
	}

	private static void checkUsageEffects(List<BucketUsageEffect> effects, List<String> allIds) {
		Set<String> keys = new HashSet<>();
		for (BucketUsageEffect effect : effects) {
			if (!allIds.contains(effect.userCardId())) {
				throw new IllegalArgumentException("a usage effect must belong to a card in the result");
			}
			if (!keys.add(effect.userCardId() + "\n" + effect.limitBucketId())) {
				throw new IllegalArgumentException("a usage effect is duplicated: " + effect.limitBucketId());
			}
		}
	}

}
