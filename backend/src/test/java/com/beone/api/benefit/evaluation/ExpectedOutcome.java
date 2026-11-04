package com.beone.api.benefit.evaluation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.money.BenefitAmount;
import com.beone.api.benefit.money.Won;
import com.beone.api.benefit.result.ApplicationStatus;
import com.beone.api.benefit.result.SelectionBasis;

/**
 * 평가 시나리오 하나의 기대 결과를 운영 결과 타입으로 표현한다.
 * fixture는 {@link com.beone.api.benefit.result.Recommendation}의 일부만 정하므로 fixture가 적은 필드만 둔다.
 *
 * @param currentOrderBenefits fixture {@code benefits}. {@code null}은 확인 필요가 된다
 * @param monthlyTotal 선택 카드의 fixture {@code monthlyTotalWon}. 적힌 경우에만 있다
 */
record ExpectedOutcome(Map<CardId, BenefitAmount> currentOrderBenefits, Map<CardId, ApplicationStatus> statusByCard,
		Map<ServiceRef, BenefitAmount> serviceBenefits, Map<ServiceRef, ApplicationStatus> serviceStatus,
		Optional<CardId> selected, List<CardId> tie, Optional<SelectionBasis> tieBreak, Optional<Won> monthlyTotal,
		String calculation) {

	ExpectedOutcome {
		currentOrderBenefits = Map.copyOf(currentOrderBenefits);
		statusByCard = Map.copyOf(statusByCard);
		serviceBenefits = Map.copyOf(serviceBenefits);
		serviceStatus = Map.copyOf(serviceStatus);
		Objects.requireNonNull(selected, "selected");
		tie = List.copyOf(tie);
		Objects.requireNonNull(tieBreak, "tieBreak");
		Objects.requireNonNull(monthlyTotal, "monthlyTotal");
		Objects.requireNonNull(calculation, "calculation");
		validate(currentOrderBenefits, statusByCard, serviceBenefits, serviceStatus, selected, tie, tieBreak,
				monthlyTotal);
	}

	private static void validate(Map<CardId, BenefitAmount> currentOrderBenefits,
			Map<CardId, ApplicationStatus> statusByCard, Map<ServiceRef, BenefitAmount> serviceBenefits,
			Map<ServiceRef, ApplicationStatus> serviceStatus, Optional<CardId> selected, List<CardId> tie,
			Optional<SelectionBasis> tieBreak, Optional<Won> monthlyTotal) {
		if (!currentOrderBenefits.keySet().equals(statusByCard.keySet())) {
			throw new IllegalArgumentException("benefits and statuses must name the same cards");
		}
		currentOrderBenefits.forEach((card, amount) -> requireConsistent(statusByCard.get(card), amount, card));
		if (!serviceBenefits.keySet().equals(serviceStatus.keySet())) {
			throw new IllegalArgumentException("service amounts and statuses must name the same services");
		}
		serviceStatus.forEach((service, status) -> requireConsistent(status, serviceBenefits.get(service), service));
		for (ServiceRef service : serviceBenefits.keySet()) {
			if (!currentOrderBenefits.containsKey(service.cardId())) {
				throw new IllegalArgumentException("service " + service + " belongs to a card without a result");
			}
		}
		selected.ifPresent(card -> {
			if (!currentOrderBenefits.containsKey(card)) {
				throw new IllegalArgumentException("selected card " + card + " has no expected result");
			}
		});
		boolean arbitrary = tieBreak.map(SelectionBasis::isArbitrary).orElse(false);
		if (tieBreak.isPresent() && !arbitrary) {
			throw new IllegalArgumentException("fixture tieBreak must be an arbitrary D-12 step");
		}
		if (arbitrary != !tie.isEmpty()) {
			throw new IllegalArgumentException("tie and tieBreak must be given together");
		}
		if (!tie.isEmpty() && (tie.size() < 2 || Set.copyOf(tie).size() != tie.size()
				|| !selected.map(tie::contains).orElse(false))) {
			throw new IllegalArgumentException("a tie needs two distinct cards including the selected one");
		}
		if (monthlyTotal.isPresent() && selected.isEmpty()) {
			throw new IllegalArgumentException("a monthly total is stated only for a selected card");
		}
	}

	private static void requireConsistent(ApplicationStatus status, BenefitAmount amount, Object key) {
		if (status == null || amount == null || !status.isConsistentWith(amount)) {
			throw new IllegalArgumentException(key + ": status " + status + " does not match amount " + amount);
		}
	}

}
