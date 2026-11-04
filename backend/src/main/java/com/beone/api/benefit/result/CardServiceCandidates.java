package com.beone.api.benefit.result;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.card.ServiceId;
import com.beone.api.benefit.rule.ConditionalTerm;
import com.beone.api.benefit.rule.RuleVersionRef;

/**
 * 카드 한 장에서 주문 한 건의 서비스 후보를 취합한 결과(#33, D-13). 어떤 서비스를 채택할지와 카드 혜택 합계는 담지 않는다.
 */
public sealed interface CardServiceCandidates {

	CardId cardId();

	/**
	 * 쓸 수 있는 규칙이 없어 서비스 목록을 알 수 없는 경우. 0원이나 빈 취합 결과가 아니다.
	 */
	record Unavailable(CardId cardId, ApplicationStatus status, String reason) implements CardServiceCandidates {

		public Unavailable {
			Objects.requireNonNull(cardId, "cardId");
			if (status != ApplicationStatus.RULE_EXPIRED && status != ApplicationStatus.NO_VERIFIED_RULE) {
				throw new IllegalArgumentException("unavailable rule status must be RULE_EXPIRED or NO_VERIFIED_RULE");
			}
			if (reason == null || reason.isBlank()) {
				throw new IllegalArgumentException("reason is required");
			}
		}

	}

	/**
	 * 선택된 규칙의 서비스마다 후보·확인 필요·지원 범위 밖 중 하나로 나눈 결과. 목록 순서는 채택 우선순위가 아니다.
	 * 생성 시 확인 필요 금액의 후보와 같은 서비스의 중복 분류를 거부하고, 모든 목록을 불변 복사본으로 보관한다.
	 *
	 * @param rule 서비스를 가져온 규칙 버전
	 * @param candidates 후보 금액이 확정된 서비스. 확정 0원(미충족 사유 포함)도 여기에 둔다. 확정 금액은 단독 후보 금액이며 채택이나
	 * {@code unresolvedConditionalTerms} 해소를 뜻하지 않는다
	 * @param needingConfirmation 후보 금액을 확정하지 못한 서비스
	 * @param unsupported 이번 기능이 판정하지 않는 서비스(한도·선택형)
	 * @param unresolvedConditionalTerms 규칙 선택 결과가 넘긴 조건부 미확정 조건. 해소하지 않고 그대로 전달한다
	 */
	record Collected(RuleVersionRef rule, List<ServiceCandidate> candidates,
			List<ServiceNeedingConfirmation> needingConfirmation, List<UnsupportedService> unsupported,
			List<ConditionalTerm> unresolvedConditionalTerms) implements CardServiceCandidates {

		public Collected {
			Objects.requireNonNull(rule, "rule");
			candidates = List.copyOf(candidates);
			needingConfirmation = List.copyOf(needingConfirmation);
			unsupported = List.copyOf(unsupported);
			unresolvedConditionalTerms = List.copyOf(unresolvedConditionalTerms);
			for (ServiceCandidate candidate : candidates) {
				// 확인 필요 후보는 needingConfirmation에만 둔다.
				if (!candidate.standaloneAmount().isConfirmed()) {
					throw new IllegalArgumentException("candidate " + candidate.serviceId()
							+ " needs confirmation and belongs to needingConfirmation");
				}
			}
			Set<ServiceId> seen = new HashSet<>();
			Stream.of(candidates.stream().map(ServiceCandidate::serviceId),
					needingConfirmation.stream().map(ServiceNeedingConfirmation::serviceId),
					unsupported.stream().map(UnsupportedService::serviceId))
				.flatMap(ids -> ids)
				.forEach(id -> {
					if (!seen.add(id)) {
						throw new IllegalArgumentException("service " + id + " is classified more than once");
					}
				});
		}

		@Override
		public CardId cardId() {
			return rule.cardId();
		}

	}

}
