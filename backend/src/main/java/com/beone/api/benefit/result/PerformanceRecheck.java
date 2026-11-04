package com.beone.api.benefit.result;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 취소 귀속·할인 매출 실적 제외 조건 때문에 최종 채택·기여액을 정하지 못한 후보(#35, U4).
 *
 * <p>
 * {@code candidate}는 후보 취합 결과의 원본 판정을 그대로 담는다. 원본의 확정 0원과 실적 미충족 사유는 입력 실적 기준의 판정일 뿐이며
 * <strong>최종 기여액이 아니다</strong>. 이 후보의 최종 채택 여부와 기여액은 {@code 확인 필요}다.
 *
 * <p>
 * 생성 시 후보가 실적 미충족·한도 소진만 있는 확정 0원이거나 미충족 사유 없는 확정 0원인지, 사유 목록이 비어 있지 않고 공백 문구가 없는지 확인한다.
 * 그 0원이 실제로 조건에 따라 달라질 수 있는지는 확인하지 않는다(결정기 책임).
 *
 * @param candidate 원본 후보 판정
 * @param reasons 재확인이 필요한 사유
 */
public record PerformanceRecheck(ServiceCandidate candidate, List<String> reasons) {

	private static final Set<ApplicationStatus> PERFORMANCE_DEPENDENT_UNMET = Set
		.of(ApplicationStatus.NOT_APPLICABLE_PERFORMANCE, ApplicationStatus.LIMIT_EXHAUSTED);

	/**
	 * 후보 모양과 사유를 확인한다.
	 *
	 * @throws IllegalArgumentException 후보 모양이 맞지 않거나 사유가 비어 있는 경우
	 */
	public PerformanceRecheck {
		Objects.requireNonNull(candidate, "candidate");
		Set<ApplicationStatus> unmet = candidate.unmetConditions();
		// 한도액은 실적 구간으로 정해지므로 한도 소진도 실적이 바뀌면 달라질 수 있다.
		if (!PERFORMANCE_DEPENDENT_UNMET.containsAll(unmet)) {
			throw new IllegalArgumentException("service " + candidate.serviceId()
					+ " has unmet conditions other than performance " + unmet + ", so its zero is final");
		}
		if (!ServiceCandidate.isConfirmedZero(candidate.standaloneAmount())) {
			throw new IllegalArgumentException("service " + candidate.serviceId() + " recheck needs a confirmed zero candidate, not "
					+ candidate.standaloneAmount());
		}
		reasons = List.copyOf(reasons);
		if (reasons.isEmpty()) {
			throw new IllegalArgumentException("service " + candidate.serviceId() + " needs at least one recheck reason");
		}
		for (String reason : reasons) {
			if (reason.isBlank()) {
				throw new IllegalArgumentException("service " + candidate.serviceId() + " has a blank recheck reason");
			}
		}
	}

}
