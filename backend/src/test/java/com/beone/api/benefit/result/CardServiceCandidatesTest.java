package com.beone.api.benefit.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.card.ServiceId;
import com.beone.api.benefit.money.BenefitAmount;
import com.beone.api.benefit.rule.RuleVersionRef;

/**
 * 서비스 후보 취합 결과 타입의 생성 검증. 값은 합성이다.
 */
class CardServiceCandidatesTest {

	private static final CardId CARD = CardId.of("SYN-CARD");

	private static final RuleVersionRef RULE = new RuleVersionRef(CARD, "SYN-2026");

	private static final ServiceId SERVICE = ServiceId.of("SYN-SVC");

	private static ServiceCandidate confirmed(long won) {
		return new ServiceCandidate(SERVICE, BenefitAmount.confirmed(won), Set.of());
	}

	private static ServiceNeedingConfirmation pending() {
		return new ServiceNeedingConfirmation(SERVICE, ConfirmationStage.RULE_SELECTION, List.of("synthetic"));
	}

	private static UnsupportedService unsupported() {
		return new UnsupportedService(SERVICE, Set.of(UnsupportedReason.HAS_LIMIT_BUCKETS), List.of());
	}

	@Test
	void collectedKeepsConfirmedZeroAsCandidate() {
		CardServiceCandidates.Collected result = new CardServiceCandidates.Collected(RULE, List.of(confirmed(0)),
				List.of(), List.of(), List.of());

		assertThat(result.cardId()).isEqualTo(CARD);
		assertThat(result.candidates()).containsExactly(confirmed(0));
	}

	@Test
	void collectedRejectsCandidateThatNeedsConfirmation() {
		ServiceCandidate unconfirmed = new ServiceCandidate(SERVICE, BenefitAmount.needsConfirmation("synthetic"),
				Set.of());

		assertThatThrownBy(
				() -> new CardServiceCandidates.Collected(RULE, List.of(unconfirmed), List.of(), List.of(), List.of()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("needingConfirmation");
	}

	@Test
	void collectedRejectsServiceInMoreThanOneClassification() {
		assertThatThrownBy(() -> new CardServiceCandidates.Collected(RULE, List.of(confirmed(100)), List.of(pending()),
				List.of(), List.of()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("more than once");
		assertThatThrownBy(() -> new CardServiceCandidates.Collected(RULE, List.of(), List.of(pending()),
				List.of(unsupported()), List.of()))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CardServiceCandidates.Collected(RULE, List.of(), List.of(),
				List.of(unsupported(), unsupported()), List.of()))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void collectedCopiesItsLists() {
		List<ServiceCandidate> candidates = new ArrayList<>(List.of(confirmed(100)));
		CardServiceCandidates.Collected result = new CardServiceCandidates.Collected(RULE, candidates, List.of(),
				List.of(), List.of());
		candidates.clear();

		assertThat(result.candidates()).containsExactly(confirmed(100));
		assertThatThrownBy(() -> result.candidates().clear()).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void unavailableAcceptsOnlyRuleStatuses() {
		assertThat(new CardServiceCandidates.Unavailable(CARD, ApplicationStatus.RULE_EXPIRED, "synthetic").cardId())
			.isEqualTo(CARD);
		for (ApplicationStatus status : EnumSet.complementOf(
				EnumSet.of(ApplicationStatus.RULE_EXPIRED, ApplicationStatus.NO_VERIFIED_RULE))) {
			assertThatThrownBy(() -> new CardServiceCandidates.Unavailable(CARD, status, "synthetic"))
				.isInstanceOf(IllegalArgumentException.class);
		}
		assertThatThrownBy(() -> new CardServiceCandidates.Unavailable(CARD, ApplicationStatus.NO_VERIFIED_RULE, " "))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void serviceNeedingConfirmationNeedsReasons() {
		assertThatThrownBy(() -> new ServiceNeedingConfirmation(SERVICE, ConfirmationStage.PRIOR_PERFORMANCE, List.of()))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(
				() -> new ServiceNeedingConfirmation(SERVICE, ConfirmationStage.PRIOR_PERFORMANCE, List.of(" ")))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void unsupportedServiceNeedsReason() {
		assertThatThrownBy(() -> new UnsupportedService(SERVICE, Set.of(), List.of()))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
