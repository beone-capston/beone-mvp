package com.beone.api.recommendation.application;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import com.beone.api.benefit.engine.AppliedRuleOutput;
import com.beone.api.benefit.engine.BenefitAmountOutput;
import com.beone.api.benefit.engine.EngineResult;
import com.beone.api.benefit.engine.PendingReasonOutput;
import com.beone.api.benefit.engine.ServiceLimitLine;
import com.beone.api.benefit.engine.ServiceResult;
import com.beone.api.card.application.UserCardView;
import com.beone.api.order.application.OrderDetail;

/**
 * 엔진 결과를 계약 응답 본문({@link RecommendationDocument})으로 옮긴다. 엔진 값을 그대로 쓰고 다시 계산하지 않는다.
 */
final class DocumentAssembler {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private static final DateTimeFormatter INSTANT = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssXXX");

	private DocumentAssembler() {
	}

	static String seoulInstant(LocalDateTime seoulTime) {
		return INSTANT.format(seoulTime.atZone(SEOUL));
	}

	/**
	 * @param recommendationId 추천 ID
	 * @param calculatedAt 계산 시각(서울)
	 * @param order 주문
	 * @param selectionMode 결제 선택 방식
	 * @param representativeUserCardId 엔진에 넘긴 대표 카드
	 * @param cards 엔진에 넘긴 보유 카드(표시 이름·매핑)
	 * @param monthlyStateSnapshotAt 카드별 월간 상태 스냅샷 시각(입력 있는 카드만)
	 * @param result 엔진 결과
	 */
	static RecommendationDocument document(String recommendationId, LocalDateTime calculatedAt, OrderDetail order,
			String selectionMode, String representativeUserCardId, List<UserCardView> cards,
			Map<String, String> monthlyStateSnapshotAt, EngineResult result) {
		boolean determined = "SELECTED".equals(result.outcome());
		RecommendationDocument.Selected selected = (result.selected() == null) ? null
				: new RecommendationDocument.Selected(result.selected().userCardId(), result.selected().cardProductId(),
						new RecommendationDocument.Basis(result.selected().decidedBy(),
								result.selected().arbitraryTieBreak(), result.selected().tiedUserCardIds(),
								result.selected().gapToRunnerUpWon()));
		List<RecommendationDocument.ComparedCard> compared = result.comparedCards()
			.stream()
			.map(card -> {
				UserCardView view = find(cards, card.userCardId());
				RecommendationDocument.Mapping mapping = (view.mapping() == null) ? null
						: new RecommendationDocument.Mapping(view.mapping().status().name(), view.mapping().note());
				return new RecommendationDocument.ComparedCard(card.userCardId(), card.cardProductId(),
						view.displayName(), mapping, card.applicationStatus(), rule(card.appliedRule()),
						amount(card.currentOrderBenefit()), amount(card.monthlyTotalBenefit()),
						card.services().stream().map(DocumentAssembler::service).toList(), card.noBenefitReason(),
						card.policyNotes(), pending(card.pendingReasons()));
			})
			.toList();
		List<RecommendationDocument.ExcludedCard> excluded = result.excludedCards()
			.stream()
			.map(card -> new RecommendationDocument.ExcludedCard(card.userCardId(), card.cardProductId(), card.reason(),
					card.detail(), card.candidateCardProductId(), amount(card.monthlyTotalBenefit()),
					card.applicationStatus(), rule(card.appliedRule()), pending(card.pendingReasons())))
			.toList();
		RecommendationDocument.FixedCardComparison fixed = (result.fixedComparison() == null) ? null
				: new RecommendationDocument.FixedCardComparison(result.fixedComparison().representativeUserCardId(),
						result.fixedComparison().optimalUserCardId(), result.fixedComparison().differenceWon());
		// 계약 예시대로 NOT_DETERMINED면 입력 스냅샷을 비운다(결정 27 B2-6).
		RecommendationDocument.InputSnapshot snapshot = determined
				? new RecommendationDocument.InputSnapshot(new RecommendationDocument.OrderSnapshot(order.amountWon(),
						order.orderDate(), order.merchantClass(), order.paymentMethod().name()),
						representativeUserCardId, monthlyStateSnapshotAt)
				: null;
		return new RecommendationDocument(recommendationId, order.orderId(), seoulInstant(calculatedAt),
				result.decisionDate(), selectionMode, result.outcome(), result.conditional(), selected, compared,
				excluded, fixed, snapshot, RecommendationNotes.toTexts(result.noteCodes()));
	}

	private static UserCardView find(List<UserCardView> cards, String userCardId) {
		return cards.stream()
			.filter(card -> card.userCardId().equals(userCardId))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("engine returned a card that was not passed in"));
	}

	private static RecommendationDocument.Service service(ServiceResult service) {
		return new RecommendationDocument.Service(service.serviceId(), service.applicationStatus(),
				service.candidateWon(), amount(service.contribution()), service.adoption(),
				service.limits().stream().map(DocumentAssembler::limit).toList(), service.limitReason(),
				service.usageBasis(), service.mayDifferFromActual(), service.usageNote(),
				service.fractionalWonTruncated(),
				(service.fractionalWonBasis() == null) ? null
						: new RecommendationDocument.FractionalWonBasis(service.fractionalWonBasis().kind(),
								service.fractionalWonBasis().decision()),
				service.unevaluatedExclusions());
	}

	private static RecommendationDocument.Limit limit(ServiceLimitLine line) {
		return new RecommendationDocument.Limit(line.limitBucketId(), line.parentLimitBucketId(), line.limitWon(),
				new RecommendationDocument.UsedBefore(line.usedBeforeWon(), line.usedBeforeSource()),
				line.remainingAfterWon());
	}

	private static RecommendationDocument.AppliedRule rule(AppliedRuleOutput rule) {
		return (rule == null) ? null
				: new RecommendationDocument.AppliedRule(rule.ruleVersion(), rule.verificationStatus(),
						rule.officialSources()
							.stream()
							.map(source -> new RecommendationDocument.OfficialSource(source.uri(), source.locator()))
							.toList());
	}

	private static RecommendationDocument.Amount amount(BenefitAmountOutput amount) {
		return new RecommendationDocument.Amount(amount.amountWon(), amount.status());
	}

	private static List<RecommendationDocument.PendingReason> pending(List<PendingReasonOutput> reasons) {
		return reasons.stream()
			.map(reason -> new RecommendationDocument.PendingReason(reason.kind(), reason.serviceIds(), reason.detail()))
			.toList();
	}

}
