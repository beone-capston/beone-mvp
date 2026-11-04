package com.beone.api.benefit.engine;

import java.util.List;
import java.util.Objects;

import com.beone.api.benefit.result.ApplicationStatus;

/**
 * 금액이 확정되어 D-12 비교에 들어간 카드.
 *
 * <p>표시 이름과 매핑 문구는 업무 API가 붙인다. {@code applicationStatus}는 평가와 화면 분기용 카드 상태이며 API 응답 필드가 아니다.
 *
 * @param userCardId 보유 카드 ID
 * @param cardProductId 규칙 카드 ID
 * @param applicationStatus 카드 상태
 * @param appliedRule 계산에 쓴 규칙
 * @param currentOrderBenefit 현재 주문 혜택(D-18 K3)
 * @param monthlyTotalBenefit 월간 합계. 1차는 현재 주문 혜택과 같다(D-18 K4)
 * @param services 서비스 결과
 * @param noBenefitReason 확정 0원이면 {@code CONFIRMED_NOT_MET}, 아니면 null
 * @param policyNotes 약관 근거 없는 사용자 결정의 표시
 * @param pendingReasons 비교 카드에는 보통 비어 있다
 */
public record ComparedCard(String userCardId, String cardProductId, String applicationStatus,
		AppliedRuleOutput appliedRule, BenefitAmountOutput currentOrderBenefit, BenefitAmountOutput monthlyTotalBenefit,
		List<ServiceResult> services, String noBenefitReason, List<String> policyNotes,
		List<PendingReasonOutput> pendingReasons) {

	public ComparedCard {
		EngineChecks.requireToken(userCardId, "userCardId");
		EngineChecks.requireToken(cardProductId, "cardProductId");
		EngineChecks.requireEnumName(ApplicationStatus.class, applicationStatus, "applicationStatus");
		Objects.requireNonNull(appliedRule, "appliedRule");
		Objects.requireNonNull(currentOrderBenefit, "currentOrderBenefit");
		Objects.requireNonNull(monthlyTotalBenefit, "monthlyTotalBenefit");
		if (!currentOrderBenefit.isConfirmed()) {
			throw new IllegalArgumentException("a compared card has a confirmed benefit");
		}
		if (!currentOrderBenefit.equals(monthlyTotalBenefit)) {
			throw new IllegalArgumentException("monthly total equals the current order benefit (D-18 K4)");
		}
		boolean zero = currentOrderBenefit.amountWon() == 0;
		if (zero != "CONFIRMED_NOT_MET".equals(noBenefitReason) || (!zero && noBenefitReason != null)) {
			throw new IllegalArgumentException("noBenefitReason is CONFIRMED_NOT_MET exactly when the benefit is zero");
		}
		services = EngineChecks.copy(services, "services");
		EngineChecks.requireDistinct(services.stream().map(ServiceResult::serviceId).toList(), "serviceId");
		policyNotes = EngineChecks.copy(policyNotes, "policyNotes");
		pendingReasons = EngineChecks.copy(pendingReasons, "pendingReasons");
	}

}
