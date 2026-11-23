package com.beone.api.payment.application;

import static com.beone.api.payment.application.PaymentFixtures.auto;
import static com.beone.api.payment.application.PaymentFixtures.compared;
import static com.beone.api.payment.application.PaymentFixtures.excluded;
import static com.beone.api.payment.application.PaymentFixtures.fixed;
import static com.beone.api.payment.application.PaymentFixtures.selected;
import static com.beone.api.payment.application.PaymentFixtures.service;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 승인 카드 결정(docs/api/mock-payments.md 흐름·실패 응답, D-18 K2, D-19). 기대값은 계약 승인 응답 예시와 실패 목록에서 썼다.
 */
class CardChoiceTest {

	@Test
	void autoRecommendTakesTheSelectedCardAndItsBenefitAsInTheContractExample() {
		CardChoice choice = CardChoice.choose(auto("rec-3002", selected("uc-1001", "09174", "MONTHLY_TOTAL"),
				List.of(compared("uc-1001", "09174", 2100L, List.of(service("SVC-GREAT", 2100L), service("SVC-X", 0L))),
						compared("uc-1003", "01664", 1200L, List.of())),
				List.of(), true));

		assertThat(choice.userCardId()).isEqualTo("uc-1001");
		assertThat(choice.cardProductId()).isEqualTo("09174");
		assertThat(choice.benefit()).isEqualTo(new ApprovalDocument.Amount(2100L, "CONFIRMED"));
		assertThat(choice.appliedRule()).isEqualTo(new ApprovalDocument.AppliedRule("09174-v1", "VERIFIED",
				List.of(new ApprovalDocument.OfficialSource(PaymentFixtures.SOURCE_URI, "p.1"))));
		assertThat(choice.selectionBasis())
			.isEqualTo(new ApprovalDocument.SelectionBasis("MONTHLY_TOTAL", false, List.of()));
		// 확정 0원 기여 서비스는 취소 영향(BENEFIT) 대상에서 뺀다
		assertThat(choice.benefitServiceIds()).containsExactly("SVC-GREAT");
	}

	@Test
	void autoRecommendWithoutComparableCardIsNotDetermined() {
		assertThatThrownBy(() -> CardChoice.choose(auto("rec-3002", null, List.of(),
				List.of(excluded("uc-1001", "09174", "NEEDS_CONFIRMATION", List.of("SVC-GREAT"))), false)))
			.isInstanceOf(RecommendationNotDeterminedException.class);
	}

	@Test
	void fixedRepresentativeComparedCardIsApprovedWithItsBenefitAndNoSelectionBasis() {
		CardChoice choice = CardChoice.choose(fixed("uc-1003", selected("uc-1001", "09174", "MONTHLY_TOTAL"),
				List.of(compared("uc-1001", "09174", 2100L, List.of()), compared("uc-1003", "01664", 1200L, List.of())),
				List.of()));

		assertThat(choice.userCardId()).isEqualTo("uc-1003");
		assertThat(choice.cardProductId()).isEqualTo("01664");
		assertThat(choice.benefit()).isEqualTo(new ApprovalDocument.Amount(1200L, "CONFIRMED"));
		assertThat(choice.selectionBasis()).isNull();
	}

	@Test
	void fixedRepresentativeNeedingConfirmationIsApprovedAsNeedsConfirmationNotZero() {
		// D-19: 혜택이 확인 필요인 대표 카드도 승인하고 amountWon=null로 둔다
		CardChoice choice = CardChoice.choose(fixed("uc-1001", null, List.of(),
				List.of(excluded("uc-1001", "09174", "NEEDS_CONFIRMATION", List.of("SVC-GREAT", "SVC-GREAT")))));

		assertThat(choice.userCardId()).isEqualTo("uc-1001");
		assertThat(choice.benefit()).isEqualTo(new ApprovalDocument.Amount(null, "NEEDS_CONFIRMATION"));
		assertThat(choice.appliedRule()).isNotNull();
		assertThat(choice.benefitServiceIds()).containsExactly("SVC-GREAT");
	}

	@Test
	void fixedRepresentativeWithoutRuleOnTheDecisionDateIsApprovedWithoutRule() {
		// 계약: 판정일에 규칙이 없어도(RULE_UNAVAILABLE) 대표 카드 고정이면 승인하고 혜택은 확인 필요
		CardChoice choice = CardChoice
			.choose(fixed("uc-1001", null, List.of(), List.of(excluded("uc-1001", "09174", "RULE_UNAVAILABLE", List.of()))));

		assertThat(choice.benefit().amountWon()).isNull();
		assertThat(choice.appliedRule()).isNull();
		assertThat(choice.cardProductId()).isEqualTo("09174");
	}

	@Test
	void fixedRepresentativeWithUnconfirmedMappingOrMissingIsNotEligible() {
		for (String reason : List.of("MAPPING_UNCONFIRMED", "NO_SUPPORTED_RULE", "USER_EXCLUDED")) {
			assertThatThrownBy(() -> CardChoice
				.choose(fixed("uc-1001", null, List.of(), List.of(excluded("uc-1001", null, reason, List.of())))))
				.as(reason)
				.isInstanceOf(RepresentativeCardNotEligibleException.class);
		}
		assertThatThrownBy(() -> CardChoice.choose(fixed("uc-9", selected("uc-1001", "09174", null),
				List.of(compared("uc-1001", "09174", 2100L, List.of())), List.of())))
			.isInstanceOf(RepresentativeCardNotEligibleException.class);
	}

}
