package com.beone.api.benefit.rule;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.card.ServiceId;
import com.beone.api.benefit.result.ApplicationStatus;

/**
 * 모든 카드의 규칙 버전을 보관하고, 판정일에 적용할 {@code VERIFIED} 규칙을 고른다(PRD FR-01).
 *
 * <p>
 * 기간을 먼저 보고 검증 상태를 나중에 본다(이슈 #15 Q1).
 * <ol>
 * <li>카드에 규칙 버전이 없으면 {@code NO_VERIFIED_RULE}</li>
 * <li>판정일을 포함하는 버전이 없으면 {@code RULE_EXPIRED}</li>
 * <li>포함하는 버전은 있지만 {@code VERIFIED}가 없으면 {@code NO_VERIFIED_RULE}</li>
 * <li>그 밖에는 해당 {@code VERIFIED} 버전을 선택</li>
 * </ol>
 * {@code VERIFIED}가 아닌 후보, AI 초안, 평가 전용 스냅샷은 표현용으로만 보관하고 선택하지 않는다.
 */
public final class RuleCatalog {

	private final List<CardRuleVersion> versions;

	public RuleCatalog(List<CardRuleVersion> versions) {
		this.versions = List.copyOf(versions);
		Set<RuleVersionRef> refs = new HashSet<>();
		for (CardRuleVersion version : this.versions) {
			if (!refs.add(version.ref())) {
				throw new IllegalArgumentException("duplicate rule version " + version.ref());
			}
		}
		requireNoOverlappingVerifiedVersions();
	}

	private void requireNoOverlappingVerifiedVersions() {
		List<CardRuleVersion> verified = versions.stream().filter(CardRuleVersion::isVerified).toList();
		for (int i = 0; i < verified.size(); i++) {
			for (int j = i + 1; j < verified.size(); j++) {
				CardRuleVersion first = verified.get(i);
				CardRuleVersion second = verified.get(j);
				if (first.cardId().equals(second.cardId()) && first.validity().overlaps(second.validity())) {
					throw new IllegalArgumentException("VERIFIED rule versions " + first.ref() + " and " + second.ref()
							+ " overlap");
				}
			}
		}
	}

	public RuleSelection select(CardId cardId, LocalDate decisionDate) {
		Objects.requireNonNull(cardId, "cardId");
		Objects.requireNonNull(decisionDate, "decisionDate");
		// 1) 카드의 규칙 버전 존재 여부
		List<CardRuleVersion> forCard = versions.stream().filter(v -> v.cardId().equals(cardId)).toList();
		if (forCard.isEmpty()) {
			return new RuleSelection.Unavailable(cardId, ApplicationStatus.NO_VERIFIED_RULE,
					"no rule version for card " + cardId);
		}
		// 2) 판정일을 포함하는 버전이 있는지 기간부터 확인
		List<CardRuleVersion> covering = forCard.stream().filter(v -> v.validity().contains(decisionDate)).toList();
		if (covering.isEmpty()) {
			return new RuleSelection.Unavailable(cardId, ApplicationStatus.RULE_EXPIRED,
					"no rule version of card " + cardId + " is valid on " + decisionDate);
		}
		// 3) 그중 VERIFIED만 선택. 생성 시 겹침을 막았으므로 최대 한 개다
		List<CardRuleVersion> verified = covering.stream().filter(CardRuleVersion::isVerified).toList();
		if (verified.isEmpty()) {
			return new RuleSelection.Unavailable(cardId, ApplicationStatus.NO_VERIFIED_RULE,
					"no VERIFIED rule version of card " + cardId + " is valid on " + decisionDate);
		}
		return selected(verified.get(0));
	}

	/**
	 * 선택된 규칙의 서비스를 계산 가능한 것과 확인이 필요한 것으로 나누고, 조건부 미확정 조건을 함께 넘긴다.
	 */
	private static RuleSelection.Selected selected(CardRuleVersion rule) {
		Map<ServiceId, List<String>> blocked = new LinkedHashMap<>();
		List<BenefitService> calculable = rule.services().stream().filter(service -> {
			List<String> reasons = rule.blockingReasons(service);
			if (!reasons.isEmpty()) {
				blocked.put(service.id(), reasons);
			}
			return reasons.isEmpty();
		}).toList();
		return new RuleSelection.Selected(rule, calculable, blocked, rule.unresolvedConditionalTerms());
	}

}
