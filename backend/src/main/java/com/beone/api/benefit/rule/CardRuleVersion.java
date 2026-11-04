package com.beone.api.benefit.rule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.beone.api.benefit.card.CardId;
import com.beone.api.benefit.card.LimitBucketId;
import com.beone.api.benefit.card.ServiceId;

/**
 * 카드 상품의 버전 있는 규칙(PRD FR-01).
 *
 * <p>
 * {@code evaluationOnly}는 fixture C02처럼 평가 자료로만 존재하는 규칙 스냅샷을 표시한다.
 * 이런 규칙은 {@code VERIFIED}가 될 수 없으므로 {@link RuleCatalog}가 선택하지 않는다.
 * 평가 전용 규칙의 선택·실행은 후속 평가 하네스에서 다룬다.
 */
public record CardRuleVersion(CardId cardId, String ruleVersion, ValidityPeriod validity, VerificationStatus status,
		boolean evaluationOnly, List<OfficialSource> sources, RulePolicy policy, List<BenefitService> services,
		List<LimitBucket> limitBuckets) {

	public CardRuleVersion {
		Objects.requireNonNull(cardId, "cardId");
		if (ruleVersion == null || ruleVersion.isBlank()) {
			throw new IllegalArgumentException("rule version is required");
		}
		Objects.requireNonNull(validity, "validity");
		Objects.requireNonNull(status, "status");
		sources = List.copyOf(sources);
		Objects.requireNonNull(policy, "policy");
		services = List.copyOf(services);
		limitBuckets = List.copyOf(limitBuckets);
		// VERIFIED 규칙은 평가 전용일 수 없고 공식 출처와 서비스가 있어야 한다.
		if (status == VerificationStatus.VERIFIED) {
			if (evaluationOnly) {
				throw new IllegalArgumentException("a VERIFIED rule cannot be evaluation-only");
			}
			if (sources.isEmpty()) {
				throw new IllegalArgumentException("a VERIFIED rule needs an official source");
			}
			if (services.isEmpty()) {
				throw new IllegalArgumentException("a VERIFIED rule needs at least one benefit service");
			}
		}
		// 서비스·한도 식별자 중복과 존재하지 않는 참조, 상위 한도 순환을 거부한다.
		Map<ServiceId, BenefitService> servicesById = uniqueBy(services, BenefitService::id, "service");
		Map<LimitBucketId, LimitBucket> bucketsById = uniqueBy(limitBuckets, LimitBucket::id, "limit bucket");
		for (BenefitService service : services) {
			for (LimitBucketId bucket : service.limitBuckets()) {
				if (!bucketsById.containsKey(bucket)) {
					throw new IllegalArgumentException("service " + service.id() + " refers to unknown limit bucket " + bucket);
				}
			}
			if (service.stackableWith().isResolved()) {
				for (ServiceId other : service.stackableWith().resolvedValue()) {
					if (!servicesById.containsKey(other)) {
						throw new IllegalArgumentException("service " + service.id() + " stacks with unknown service " + other);
					}
				}
			}
		}
		requireAcyclicParents(bucketsById);
		if (policy.newCardGrace().isResolved()) {
			policy.newCardGrace().resolvedValue().ifPresent(grace -> {
				for (ServiceId service : grace.services()) {
					if (!servicesById.containsKey(service)) {
						throw new IllegalArgumentException("grace refers to unknown service " + service);
					}
				}
			});
		}
	}

	private static <T, K> Map<K, T> uniqueBy(List<T> items, Function<T, K> key, String name) {
		Map<K, T> byKey = items.stream().collect(Collectors.toMap(key, Function.identity(), (first, second) -> {
			throw new IllegalArgumentException("duplicate " + name + " " + key.apply(first));
		}));
		return Map.copyOf(byKey);
	}

	private static void requireAcyclicParents(Map<LimitBucketId, LimitBucket> bucketsById) {
		for (LimitBucket bucket : bucketsById.values()) {
			Set<LimitBucketId> visited = new HashSet<>();
			Optional<LimitBucketId> current = Optional.of(bucket.id());
			while (current.isPresent()) {
				if (!visited.add(current.get())) {
					throw new IllegalArgumentException("limit bucket parents form a cycle at " + bucket.id());
				}
				LimitBucket currentBucket = bucketsById.get(current.get());
				if (currentBucket == null) {
					throw new IllegalArgumentException("limit bucket refers to unknown parent " + current.get());
				}
				current = currentBucket.parent();
			}
		}
	}

	public RuleVersionRef ref() {
		return new RuleVersionRef(cardId, ruleVersion);
	}

	public boolean isVerified() {
		return status == VerificationStatus.VERIFIED;
	}

	/**
	 * 이 규칙만으로 서비스를 계산할 수 없는 사유. 비어 있으면 계산할 수 있다.
	 *
	 * <ul>
	 * <li>계산식이 미확정이다.</li>
	 * <li>서비스나 서비스가 쓰는 한도(상위 한도 포함)가 실적에 따라 달라지는데 실적 기간이나 거래 기준일이 미확정이다.</li>
	 * <li>한도가 있는데 한도 기간이 미확정이다.</li>
	 * </ul>
	 * 입력에 따라서만 필요한 조건(취소 귀속, 할인 매출 실적 제외, 차감 순서, 유예, 중복, 원 미만 처리)은
	 * {@link #unresolvedConditionalTerms()}로 따로 넘긴다.
	 */
	public List<String> blockingReasons(BenefitService service) {
		List<String> reasons = new ArrayList<>(service.unresolvedCalculationReasons());
		boolean dependsOnPerformance = service.hasPerformanceRequirement()
				|| limitChain(service).stream().anyMatch(LimitBucket::dependsOnPerformance);
		if (dependsOnPerformance) {
			addReason(reasons, policy.performanceWindow());
			addReason(reasons, policy.performanceDateBasis());
		}
		if (!service.limitBuckets().isEmpty()) {
			addReason(reasons, policy.limitPeriod());
		}
		return List.copyOf(reasons);
	}

	private static void addReason(List<String> reasons, RuleTerm<?> term) {
		if (term instanceof RuleTerm.Unresolved<?> unresolved) {
			reasons.add(unresolved.reason());
		}
	}

	/**
	 * 입력에 따라서만 필요한 규칙·서비스 단위의 미확정 조건.
	 * 무시할 수 있는 경우는 {@link ConditionalTerm}을 따른다.
	 */
	public List<ConditionalTerm> unresolvedConditionalTerms() {
		List<ConditionalTerm> unresolved = new ArrayList<>();
		RulePolicy.addIfUnresolved(unresolved, "cancellationAttribution", policy.cancellationAttribution(),
				TermTrigger.CANCELLATION_AFFECTING_PERFORMANCE_WINDOW);
		RulePolicy.addIfUnresolved(unresolved, "excludesBenefitedSales", policy.excludesBenefitedSales(),
				TermTrigger.BENEFITED_SALE_IN_PERFORMANCE_WINDOW);
		RulePolicy.addIfUnresolved(unresolved, "limitDeductionOrder", policy.limitDeductionOrder(),
				TermTrigger.SHARED_LIMIT_CONSUMPTION);
		RulePolicy.addIfUnresolved(unresolved, "newCardGrace", policy.newCardGrace(), TermTrigger.WITHIN_NEW_CARD_GRACE);
		for (BenefitService service : services) {
			unresolved.addAll(service.unresolvedConditionalTerms());
		}
		return List.copyOf(unresolved);
	}

	/**
	 * 서비스가 차감하는 한도 묶음. 상위(통합) 한도까지 모두 포함한다.
	 */
	public List<LimitBucket> limitChain(BenefitService service) {
		List<LimitBucket> chain = new ArrayList<>();
		for (LimitBucketId id : service.limitBuckets()) {
			Optional<LimitBucketId> current = Optional.of(id);
			while (current.isPresent()) {
				LimitBucket bucket = limitBucket(current.get());
				if (!chain.contains(bucket)) {
					chain.add(bucket);
				}
				current = bucket.parent();
			}
		}
		return List.copyOf(chain);
	}

	public LimitBucket limitBucket(LimitBucketId id) {
		return limitBuckets.stream()
			.filter(bucket -> bucket.id().equals(id))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("unknown limit bucket " + id));
	}

}
