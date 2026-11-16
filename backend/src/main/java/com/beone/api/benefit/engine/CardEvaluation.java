package com.beone.api.benefit.engine;

import java.util.List;
import java.util.Objects;

import com.beone.api.benefit.result.CardBenefit;
import com.beone.api.benefit.result.OrderBenefit;

/**
 * 규칙으로 계산한 카드 한 장의 평가(D-22). 기존 {@link CardBenefit} 계약을 그대로 채우고, API 응답에 필요한 상세를 함께 든다.
 *
 * <p>
 * 엔진 안에서만 쓴다. 비교 카드와 금액 미확정 제외 카드를 모두 이 값에서 만든다.
 *
 * @param benefit 카드 결과. 상태·금액·서비스·버킷별 남은 한도
 * @param source 결정 결과 원본
 * @param appliedRule 계산에 쓴 규칙의 출력 값
 * @param services 규칙의 모든 서비스 결과. 서비스 ID 순
 * @param policyNotes 약관 근거 없는 사용자 결정을 적용한 표시
 * @param pendingReasons 카드 금액을 확정하지 못한 사유
 * @param usageEffects 이 카드로 승인했을 때의 버킷 차감. 차감이 없는 버킷은 넣지 않는다
 */
record CardEvaluation(CardBenefit benefit, OrderBenefit.Decided source, AppliedRuleOutput appliedRule,
		List<ServiceResult> services, List<String> policyNotes, List<PendingReasonOutput> pendingReasons,
		List<BucketUsageEffect> usageEffects) {

	CardEvaluation {
		Objects.requireNonNull(benefit, "benefit");
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(appliedRule, "appliedRule");
		services = List.copyOf(services);
		policyNotes = List.copyOf(policyNotes);
		pendingReasons = List.copyOf(pendingReasons);
		usageEffects = List.copyOf(usageEffects);
		if (!benefit.cardId().equals(source.cardId())) {
			throw new IllegalArgumentException("card evaluation mixes cards " + benefit.cardId() + " and " + source.cardId());
		}
		if (benefit.currentOrderBenefit().isConfirmed() != pendingReasons.isEmpty()) {
			throw new IllegalArgumentException("pending reasons are listed exactly when the card amount needs confirmation");
		}
	}

	boolean isConfirmed() {
		return benefit.currentOrderBenefit().isConfirmed();
	}

}
