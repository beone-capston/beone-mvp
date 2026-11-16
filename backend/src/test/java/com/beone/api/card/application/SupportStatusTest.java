package com.beone.api.card.application;

import static com.beone.api.card.application.CardRuleFixtures.policy;
import static com.beone.api.card.application.CardRuleFixtures.resolvedPolicy;
import static com.beone.api.card.application.CardRuleFixtures.service;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.beone.api.benefit.rule.CancellationAttribution;
import com.beone.api.benefit.rule.LimitDeductionOrder;
import com.beone.api.benefit.rule.RulePolicy;
import com.beone.api.benefit.rule.RuleTerm;
import org.junit.jupiter.api.Test;

/**
 * 결정 27 A1-5의 판정 순서(선택형 → 한도 없음 → 한도 기간 확정 여부)를 확인한다.
 */
class SupportStatusTest {

	private static final RulePolicy UNRESOLVED_LIMIT_PERIOD = policy(
			RuleTerm.resolved(CancellationAttribution.RECEIPT_MONTH), RuleTerm.unresolved("한도 기간 표기 없음"),
			RuleTerm.resolved(LimitDeductionOrder.APPROVAL_ORDER));

	@Test
	void serviceWithoutLimitIsSupported() {
		assertThat(SupportStatus.of(service("A", List.of(), false, null), resolvedPolicy()))
			.isEqualTo(SupportStatus.SUPPORTED);
		assertThat(SupportStatus.of(service("A", List.of(), false, null), UNRESOLVED_LIMIT_PERIOD))
			.isEqualTo(SupportStatus.SUPPORTED);
	}

	@Test
	void limitedServiceWithCalendarMonthPeriodIsSupportedSingleMonthlyLimit() {
		assertThat(SupportStatus.of(service("A", List.of("L"), false, null), resolvedPolicy()))
			.isEqualTo(SupportStatus.SUPPORTED_SINGLE_MONTHLY_LIMIT);
	}

	@Test
	void limitedServiceWithUnresolvedPeriodIsUnsupportedLimitPeriod() {
		assertThat(SupportStatus.of(service("A", List.of("L"), false, null), UNRESOLVED_LIMIT_PERIOD))
			.isEqualTo(SupportStatus.UNSUPPORTED_LIMIT_PERIOD);
	}

	@Test
	void selectionServiceIsUnsupportedSelectionRuleBeforeAnyLimitCheck() {
		assertThat(SupportStatus.of(service("A", List.of(), true, null), resolvedPolicy()))
			.isEqualTo(SupportStatus.UNSUPPORTED_SELECTION_RULE);
		assertThat(SupportStatus.of(service("A", List.of("L"), true, null), resolvedPolicy()))
			.isEqualTo(SupportStatus.UNSUPPORTED_SELECTION_RULE);
		assertThat(SupportStatus.of(service("A", List.of("L"), true, null), UNRESOLVED_LIMIT_PERIOD))
			.isEqualTo(SupportStatus.UNSUPPORTED_SELECTION_RULE);
	}

}
