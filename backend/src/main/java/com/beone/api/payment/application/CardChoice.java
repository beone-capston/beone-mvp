package com.beone.api.payment.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.beone.api.recommendation.application.RecommendationDocument;

/**
 * 승인 직전 재계산 결과에서 승인 카드를 정한다(docs/api/mock-payments.md 흐름, D-19·D-22·D-23). 혜택은 재계산 값을 옮기기만 한다.
 *
 * <ul>
 * <li>{@code AUTO_RECOMMEND}: 재계산의 선택 카드. 비교 카드가 0장({@code NOT_DETERMINED})이면 409
 * {@code RECOMMENDATION_NOT_DETERMINED}(D-18 K2)</li>
 * <li>{@code FIXED_REPRESENTATIVE}: 대표 카드. 비교 카드면 그 혜택, 금액만 미확정인 제외 카드({@code NEEDS_CONFIRMATION}·
 * {@code UNSUPPORTED}·{@code RULE_UNAVAILABLE})면 {@code 확인 필요}로 승인(D-19, 0원 금지). 대표 카드가 결과에 없거나 매핑 미확인·
 * 지원 규칙 없음·사용자 제외로 계산하지 않은 카드면 409 {@code REPRESENTATIVE_CARD_NOT_ELIGIBLE}</li>
 * </ul>
 *
 * @param userCardId 승인 카드
 * @param cardProductId 규칙 상품 ID
 * @param benefit 승인 시점 혜택
 * @param appliedRule 계산에 쓴 규칙. 판정일에 규칙이 없으면 {@code null}
 * @param selectionBasis D-12 선택 근거. 대표 카드 고정이면 {@code null}
 * @param benefitServiceIds 혜택 기여가 확정 0원이 아닌 서비스(취소 영향 {@code BENEFIT}의 대상)
 */
record CardChoice(String userCardId, String cardProductId, ApprovalDocument.Amount benefit,
		ApprovalDocument.AppliedRule appliedRule, ApprovalDocument.SelectionBasis selectionBasis,
		List<String> benefitServiceIds) {

	static final String AUTO_RECOMMEND = "AUTO_RECOMMEND";

	// 금액만 미확정이고 규칙 상품이 정해진 제외 사유. 대표 카드 고정이면 확인 필요로 승인한다.
	private static final Set<String> APPROVABLE_EXCLUSIONS = Set.of("NEEDS_CONFIRMATION", "UNSUPPORTED",
			"RULE_UNAVAILABLE");

	CardChoice {
		Objects.requireNonNull(userCardId, "userCardId");
		Objects.requireNonNull(benefit, "benefit");
		benefitServiceIds = List.copyOf(benefitServiceIds);
	}

	static CardChoice choose(RecommendationDocument document) {
		if (AUTO_RECOMMEND.equals(document.selectionMode())) {
			if (document.selected() == null) {
				throw new RecommendationNotDeterminedException();
			}
			RecommendationDocument.Selected selected = document.selected();
			RecommendationDocument.ComparedCard card = compared(document, selected.userCardId())
				.orElseThrow(() -> new IllegalStateException("the selected card must be compared"));
			return fromCompared(card, new ApprovalDocument.SelectionBasis(selected.basis().decidedBy(),
					selected.basis().arbitraryTieBreak(), selected.basis().tiedUserCardIds()));
		}
		String representative = (document.fixedCardComparison() == null) ? null
				: document.fixedCardComparison().representativeUserCardId();
		if (representative == null) {
			throw new RepresentativeCardNotEligibleException();
		}
		Optional<RecommendationDocument.ComparedCard> compared = compared(document, representative);
		if (compared.isPresent()) {
			return fromCompared(compared.get(), null);
		}
		RecommendationDocument.ExcludedCard excluded = document.excludedCards()
			.stream()
			.filter(card -> card.userCardId().equals(representative))
			.filter(card -> APPROVABLE_EXCLUSIONS.contains(card.reason()))
			.findFirst()
			.orElseThrow(RepresentativeCardNotEligibleException::new);
		List<String> services = excluded.pendingReasons()
			.stream()
			.flatMap(reason -> reason.serviceIds().stream())
			.distinct()
			.toList();
		return new CardChoice(excluded.userCardId(), excluded.cardProductId(),
				ApprovalDocument.Amount.needsConfirmation(), rule(excluded.appliedRule()), null, services);
	}

	private static Optional<RecommendationDocument.ComparedCard> compared(RecommendationDocument document,
			String userCardId) {
		return document.comparedCards().stream().filter(card -> card.userCardId().equals(userCardId)).findFirst();
	}

	private static CardChoice fromCompared(RecommendationDocument.ComparedCard card,
			ApprovalDocument.SelectionBasis basis) {
		List<String> services = card.services()
			.stream()
			.filter(service -> !amount(service.contribution()).isConfirmedZero())
			.map(RecommendationDocument.Service::serviceId)
			.toList();
		return new CardChoice(card.userCardId(), card.cardProductId(), amount(card.currentOrderBenefit()),
				rule(card.appliedRule()), basis, services);
	}

	private static ApprovalDocument.Amount amount(RecommendationDocument.Amount amount) {
		return new ApprovalDocument.Amount(amount.amountWon(), amount.status());
	}

	private static ApprovalDocument.AppliedRule rule(RecommendationDocument.AppliedRule rule) {
		return (rule == null) ? null
				: new ApprovalDocument.AppliedRule(rule.ruleVersion(), rule.verificationStatus(),
						rule.officialSources()
							.stream()
							.map(source -> new ApprovalDocument.OfficialSource(source.uri(), source.locator()))
							.toList());
	}

}
